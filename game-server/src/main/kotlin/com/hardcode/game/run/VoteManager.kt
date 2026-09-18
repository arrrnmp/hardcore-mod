package com.hardcode.game.run

import com.hardcode.common.model.RunState
import com.hardcode.common.model.VoteChoice
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.world.BossEvent
import net.minecraft.world.level.GameType
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Runs the "continue this run?" vote that follows a death: every online, still-alive run
 * participant gets a ballot, shown via a boss bar countdown. On resolution, restores
 * everyone's pre-vote gamemode (Phase 1 has no reroll pipeline yet - that's Phase 2, which
 * will replace the "would reroll" log line with an actual world reroll on a pass).
 */
class VoteManager(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val voteDurationTicks: Int = 20 * 60,
) {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    private var active = false
    private var ticksRemaining = 0
    private val votes = mutableMapOf<UUID, VoteChoice>()
    private var eligibleVoters: Set<UUID> = emptySet()
    private var preVoteGameModes: Map<UUID, GameType> = emptyMap()

    private val bossBar = ServerBossEvent(
        UUID.randomUUID(),
        Component.literal("Continue this run?"),
        BossEvent.BossBarColor.RED,
        BossEvent.BossBarOverlay.PROGRESS,
    )

    val isActive: Boolean
        get() = active

    fun startVote(gameModeSnapshot: Map<UUID, GameType>) {
        preVoteGameModes = gameModeSnapshot
        eligibleVoters = server.playerList.players
            .map { it.getUUID() }
            .filter { runManager.isParticipant(it) && !runManager.isDead(it) }
            .toSet()
        votes.clear()
        active = true
        ticksRemaining = voteDurationTicks

        bossBar.removeAllPlayers()
        bossBar.isVisible = true
        bossBar.setProgress(1f)
        for (uuid in eligibleVoters) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            bossBar.addPlayer(player)
            player.sendSystemMessage(
                Component.literal("Vote: should the run continue? /run vote yes  or  /run vote no  (60s)"),
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
        bossBar.setProgress((ticksRemaining.toFloat() / voteDurationTicks.toFloat()).coerceIn(0f, 1f))
        if (ticksRemaining % 20 == 0) {
            val yes = votes.values.count { it == VoteChoice.YES }
            val no = votes.values.count { it == VoteChoice.NO }
            bossBar.setName(Component.literal("Continue? YES $yes - NO $no  (${ticksRemaining / 20}s)"))
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

        for ((uuid, previousMode) in preVoteGameModes) {
            server.playerList.getPlayer(uuid)?.setGameMode(previousMode)
        }
        preVoteGameModes = emptyMap()

        server.playerList.broadcastSystemMessage(
            Component.literal(
                if (passed) {
                    "Vote passed ($yes-$no) - a world re-roll would happen here once Phase 2 lands."
                } else {
                    "Vote failed ($yes-$no) - the run continues."
                },
            ),
            false,
        )

        runManager.setState(RunState.RUN_ACTIVE)
        logger.info("Vote resolved: passed={} yes={} no={}", passed, yes, no)
    }
}
