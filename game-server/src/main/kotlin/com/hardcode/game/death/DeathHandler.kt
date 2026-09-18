package com.hardcode.game.death

import com.hardcode.common.model.DeathRecord
import com.hardcode.common.model.RunState
import com.hardcode.game.run.RunManager
import com.hardcode.game.run.VoteManager
import com.hardcode.game.storage.HallOfShame
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import java.util.UUID

/**
 * Wires the death → mass-spectator → vote flow together. Real drama (custom titles, red
 * flash, sound) is Phase 6 - this fires a plain broadcast for now so the loop is testable.
 */
class DeathHandler(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val voteManager: VoteManager,
    private val hallOfShame: HallOfShame,
) {
    fun register() {
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
            val player = entity as? ServerPlayer ?: return@register
            val uuid = player.getUUID()
            if (!runManager.isParticipant(uuid) || runManager.isDead(uuid)) return@register

            runManager.markDead(uuid)

            val deathMessage = player.getCombatTracker().getDeathMessage().getString()
            val pos = player.position()
            hallOfShame.recordDeath(
                DeathRecord(
                    runId = runManager.runId,
                    uuid = uuid.toString(),
                    playerName = player.getGameProfile().name,
                    cause = damageSource.getMsgId(),
                    deathMessage = deathMessage,
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    dimension = player.level().dimension().identifier().toString(),
                    epochMillis = System.currentTimeMillis(),
                ),
            )

            server.playerList.broadcastSystemMessage(Component.literal(deathMessage), false)

            val gameModeSnapshot = mutableMapOf<UUID, GameType>()
            for (other in server.playerList.players) {
                val otherUuid = other.getUUID()
                if (otherUuid == uuid) continue
                if (!runManager.isParticipant(otherUuid) || runManager.isDead(otherUuid)) continue
                gameModeSnapshot[otherUuid] = other.gameMode()
                other.setGameMode(GameType.SPECTATOR)
            }

            runManager.setState(RunState.VOTE_PENDING)
            voteManager.startVote(gameModeSnapshot)
        }
    }
}
