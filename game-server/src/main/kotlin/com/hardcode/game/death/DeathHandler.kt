package com.hardcode.game.death

import com.hardcode.common.model.DeathRecord
import com.hardcode.common.model.RunState
import com.hardcode.game.config.ConfigManager
import com.hardcode.game.run.RunManager
import com.hardcode.game.run.VoteManager
import com.hardcode.game.storage.HallOfShame
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.GameType
import java.util.UUID

/**
 * Wires the death → drama → mass-spectator → vote flow together: a custom (admin-templated)
 * title, vanilla's own richer contextual death message as the subtitle, a red screen flash,
 * an ominous sound cue, and a global chat announcement - see the project plan, section 6.
 */
class DeathHandler(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val voteManager: VoteManager,
    private val hallOfShame: HallOfShame,
    private val configManager: ConfigManager,
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
            playDrama(player, deathMessage)

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

    private fun playDrama(dead: ServerPlayer, deathMessage: String) {
        val title = configManager.config.deathTitleTemplate.replace("%player%", dead.getGameProfile().name)
        val soundHolder = Holder.direct(SoundEvents.WITHER_SPAWN)

        for (viewer in server.playerList.players) {
            if (!runManager.isParticipant(viewer.getUUID())) continue

            viewer.connection.send(ClientboundSetTitleTextPacket(Component.literal(title)))
            viewer.connection.send(ClientboundSetSubtitleTextPacket(Component.literal(deathMessage)))
            ServerPlayNetworking.send(viewer, DeathFlashPayload())
            viewer.connection.send(
                ClientboundSoundPacket(
                    soundHolder,
                    SoundSource.HOSTILE,
                    viewer.getX(),
                    viewer.getY(),
                    viewer.getZ(),
                    1.0f,
                    1.0f,
                    viewer.level().random.nextLong(),
                ),
            )
        }
    }
}
