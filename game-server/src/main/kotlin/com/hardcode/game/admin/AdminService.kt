package com.hardcode.game.admin

import com.hardcode.common.model.AdminAction
import com.hardcode.common.model.AdminActionType
import com.hardcode.common.model.AdminSnapshot
import com.hardcode.common.model.LeaderboardEntry
import com.hardcode.common.model.RosterEntry
import com.hardcode.game.config.ConfigManager
import com.hardcode.game.config.HardcoreConfig
import com.hardcode.game.freeze.FreezeManager
import com.hardcode.game.run.RerollCoordinator
import com.hardcode.game.run.RunManager
import com.hardcode.game.run.VoteManager
import com.hardcode.game.storage.HallOfShame
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.NameAndId
import net.minecraft.world.level.GameType
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Backs the in-game admin panel: every action re-checks operator status server-side
 * regardless of what the client thinks it can do (never trust the client, especially over a
 * raw custom-payload channel that bypasses command permission checks entirely).
 */
class AdminService(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val voteManager: VoteManager,
    private val freezeManager: FreezeManager,
    private val rerollCoordinator: RerollCoordinator,
    private val hallOfShame: HallOfShame,
    private val configManager: ConfigManager,
) {
    private val logger = LoggerFactory.getLogger("hardcore-game")
    private val json = Json { ignoreUnknownKeys = true }

    fun isAuthorized(player: ServerPlayer): Boolean =
        server.playerList.isOp(NameAndId(player.getGameProfile()))

    fun sendSnapshot(player: ServerPlayer) {
        ServerPlayNetworking.send(player, AdminSnapshotPayload(json.encodeToString(buildSnapshot())))
    }

    /** Returns false (and sends nothing) if [player] isn't an operator. */
    fun handleAction(player: ServerPlayer, payload: AdminActionPayload): Boolean {
        if (!isAuthorized(player)) {
            logger.warn("Rejected admin action from non-operator {}", player.getGameProfile().name)
            return false
        }

        val action = runCatching { json.decodeFromString<AdminAction>(payload.actionJson) }.getOrNull() ?: return false
        when (runCatching { AdminActionType.valueOf(action.type) }.getOrNull()) {
            AdminActionType.REQUEST_SNAPSHOT -> {}
            AdminActionType.FORCE_VOTE -> voteManager.startVote()
            AdminActionType.FORCE_FREEZE -> freezeManager.setAdminForced(true)
            AdminActionType.FORCE_UNFREEZE -> freezeManager.setAdminForced(false)
            AdminActionType.FORCE_REROLL -> {
                server.playerList.broadcastSystemMessage(
                    Component.literal("An operator forced a re-roll."),
                    false,
                )
                rerollCoordinator.requestReroll()
            }
            AdminActionType.KICK_PLAYER -> action.targetUuid?.let { kickPlayer(UUID.fromString(it)) }
            AdminActionType.UPDATE_CONFIG -> action.voteDurationSeconds?.let {
                configManager.update(HardcoreConfig(voteDurationSeconds = it.coerceIn(10, 600)))
            }
            null -> logger.warn("Unknown admin action type: {}", action.type)
        }

        sendSnapshot(player)
        return true
    }

    private fun kickPlayer(uuid: UUID) {
        runManager.removeFromRoster(uuid)
        hallOfShame.markKicked(runManager.runId, uuid.toString())
        freezeManager.forgetMissingPlayer(uuid)

        val online = server.playerList.getPlayer(uuid)
        if (online != null) {
            online.getInventory().clearContent()
            online.setGameMode(GameType.SPECTATOR)
            online.sendSystemMessage(Component.literal("An operator kicked you from this run."))
        }
        logger.info("Operator kicked {} from run {}", uuid, runManager.runId)
    }

    private fun buildSnapshot(): AdminSnapshot {
        val names = hallOfShame.playerNamesForRun(runManager.runId)
        val roster = runManager.rosterSnapshot().map { uuid ->
            RosterEntry(
                uuid = uuid.toString(),
                name = names[uuid.toString()] ?: uuid.toString(),
                online = server.playerList.getPlayer(uuid) != null,
                dead = runManager.isDead(uuid),
            )
        }
        val leaderboard = hallOfShame.leaderboard().map { LeaderboardEntry(it.playerName, it.deaths) }

        return AdminSnapshot(
            runId = runManager.runId,
            state = runManager.state.name,
            frozen = freezeManager.isFrozen,
            waitingForPlayerName = freezeManager.missingPlayers.mapNotNull { names[it.toString()] }.joinToString(", "),
            roster = roster,
            leaderboard = leaderboard,
            voteDurationSeconds = configManager.config.voteDurationSeconds,
        )
    }
}
