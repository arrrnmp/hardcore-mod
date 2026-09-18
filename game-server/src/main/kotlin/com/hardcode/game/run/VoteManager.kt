package com.hardcode.game.run

import com.hardcode.common.model.RunState
import com.hardcode.common.model.VoteChoice
import com.hardcode.game.config.ConfigManager
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.world.BossEvent
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Runs the "start the next run?" vote that follows a death: every online, still-alive run
 * participant gets a ballot, shown via a boss bar countdown. This run is over the moment
 * someone dies, full stop - the vote only ever decides *when* to re-roll, never whether to
 * keep playing this world (see [RunState.RUN_ENDED]). A pass hands off to
 * [rerollCoordinator] immediately; a fail just leaves everyone spectating until a future
 * vote passes or an operator forces a re-roll.
 */
class VoteManager(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val rerollCoordinator: RerollCoordinator,
    private val configManager: ConfigManager,
) {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    private var active = false
    private var ticksRemaining = 0
    private var voteDurationTicks = 20 * 60
    private val votes = mutableMapOf<UUID, VoteChoice>()
    private var eligibleVoters: Set<UUID> = emptySet()

    private val bossBar = ServerBossEvent(
        UUID.randomUUID(),
        Component.literal("Start the next run?"),
        BossEvent.BossBarColor.GREEN,
        BossEvent.BossBarOverlay.PROGRESS,
    )

    val isActive: Boolean
        get() = active

    fun startVote() {
        eligibleVoters = server.playerList.players
            .map { it.getUUID() }
            .filter { runManager.isParticipant(it) && !runManager.isDead(it) }
            .toSet()
        votes.clear()
        active = true
        voteDurationTicks = configManager.config.voteDurationSeconds * 20
        ticksRemaining = voteDurationTicks

        bossBar.removeAllPlayers()
        bossBar.isVisible = true
        bossBar.setProgress(1f)
        bossBar.setColor(BossEvent.BossBarColor.GREEN)
        for (uuid in eligibleVoters) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            bossBar.addPlayer(player)
            player.sendSystemMessage(
                Component.literal(
                    "Vote: start the next run now? /run vote yes  or  /run vote no  " +
                        "(${configManager.config.voteDurationSeconds}s)",
                ),
            )
        }
    }

    /** Returns true if [uuid] was an eligible voter and the vote was recorded. */
    fun castVote(uuid: UUID, choice: VoteChoice): Boolean {
        if (!active || uuid !in eligibleVoters) return false
        votes[uuid] = choice
        server.playerList.getPlayer(uuid)
            ?.sendSystemMessage(Component.literal("Vote recorded: ${choice.name}"))
        if (votes.keys.containsAll(eligibleVoters)) resolve()
        return true
    }

    fun tick() {
        if (!active) return
        ticksRemaining--
        val fraction = (ticksRemaining.toFloat() / voteDurationTicks.toFloat()).coerceIn(0f, 1f)
        bossBar.setProgress(fraction)
        bossBar.setColor(
            when {
                fraction > 0.5f -> BossEvent.BossBarColor.GREEN
                fraction > 0.2f -> BossEvent.BossBarColor.YELLOW
                else -> BossEvent.BossBarColor.RED
            },
        )
        if (ticksRemaining % 20 == 0) {
            val yes = votes.values.count { it == VoteChoice.YES }
            val no = votes.values.count { it == VoteChoice.NO }
            bossBar.setName(Component.literal("Start next run? YES $yes - NO $no  (${ticksRemaining / 20}s)"))
        }
        if (ticksRemaining <= 0) resolve()
    }

    private fun resolve() {
        active = false
        bossBar.isVisible = false
        bossBar.removeAllPlayers()

        val yes = votes.values.count { it == VoteChoice.YES }
        val no = votes.values.count { it == VoteChoice.NO }
        val passed = yes > no

        logger.info("Vote resolved: passed={} yes={} no={}", passed, yes, no)

        if (passed) {
            server.playerList.broadcastSystemMessage(Component.literal("Vote passed ($yes-$no)."), false)
            runManager.setState(RunState.REROLLING)
            val yesVoters = votes.filterValues { it == VoteChoice.YES }.keys
            rerollCoordinator.requestReroll(yesVoters)
        } else {
            server.playerList.broadcastSystemMessage(
                Component.literal(
                    "Vote failed ($yes-$no) - this run is over. Wait for a future vote, " +
                        "or ask an operator to force a re-roll.",
                ),
                false,
            )
            runManager.setState(RunState.RUN_ENDED)
        }
    }
}
