package com.hardcode.game.freeze

import com.hardcode.common.model.RunState
import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import com.hardcode.game.run.RunManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Freezes the whole run solid the moment any roster member disconnects, and un-freezes once
 * every missing member is back (or an operator kicks them from the run - see
 * [forgetMissingPlayer]). Also supports a manual operator override ([setAdminForced]) for the
 * admin panel's force-freeze/unfreeze action, layered on top of the same mechanism: frozen
 * whenever *either* someone's missing *or* an operator forced it.
 *
 * Uses vanilla's own `/tick freeze` primitive ([net.minecraft.server.ServerTickRateManager.setFrozen])
 * for passive world simulation (entity AI, random/block ticks, weather, time) - but that
 * alone does **not** stop player-driven actions (movement, block breaking, interaction all
 * still went through in testing, since those are handled via packet processing rather than
 * world ticking). So [tick] additionally pins every participant's position back to where
 * they were when the freeze started, and [registerEnforcement] cancels block break/place,
 * entity attack/interact, and damage for participants while frozen.
 */
class FreezeManager(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val redis: RedisEventBus?,
) {
    private val logger = LoggerFactory.getLogger("hardcore-game")
    private val missing = linkedSetOf<UUID>()
    private var lastMissingName: String = ""
    private var adminForced = false
    private val frozenPositions = mutableMapOf<UUID, DoubleArray>()

    val isFrozen: Boolean
        get() = missing.isNotEmpty() || adminForced

    val missingPlayers: Set<UUID>
        get() = missing.toSet()

    /** Registers the block/entity/damage cancellation - call once at mod init, not per-run. */
    fun registerEnforcement() {
        PlayerBlockBreakEvents.BEFORE.register { _, player, _, _, _ -> !isBlocked(player.getUUID()) }
        AttackBlockCallback.EVENT.register { player, _, _, _, _ ->
            if (isBlocked(player.getUUID())) InteractionResult.FAIL else InteractionResult.PASS
        }
        UseBlockCallback.EVENT.register { player, _, _, _ ->
            if (isBlocked(player.getUUID())) InteractionResult.FAIL else InteractionResult.PASS
        }
        AttackEntityCallback.EVENT.register { player, _, _, _, _ ->
            if (isBlocked(player.getUUID())) InteractionResult.FAIL else InteractionResult.PASS
        }
        UseEntityCallback.EVENT.register { player, _, _, _, _ ->
            if (isBlocked(player.getUUID())) InteractionResult.FAIL else InteractionResult.PASS
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            !(entity is ServerPlayer && isBlocked(entity.getUUID()))
        }
    }

    private fun isBlocked(uuid: UUID): Boolean = isFrozen && runManager.isParticipant(uuid)

    /** Call every server tick: pins frozen participants back to their position at freeze-start. */
    fun tick() {
        if (!isFrozen) return
        for ((uuid, pos) in frozenPositions) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.getX() != pos[0] || player.getY() != pos[1] || player.getZ() != pos[2]) {
                player.teleportTo(pos[0], pos[1], pos[2])
            }
        }
    }

    /** Call on every join, including ones unrelated to freezing, to sync current state. */
    fun syncStateTo(player: ServerPlayer) {
        if (isFrozen && runManager.isParticipant(player.getUUID())) {
            capturePosition(player)
        }
        ServerPlayNetworking.send(player, FreezeStatePayload(isFrozen, lastMissingName))
    }

    fun onDisconnect(uuid: UUID, playerName: String) {
        if (!runManager.isParticipant(uuid)) return
        // The server is already tearing down for a re-roll (players getting moved off to
        // Limbo trigger their own disconnect from this server) - freezing a run that's about
        // to be destroyed anyway is meaningless noise, confirmed by real testing where this
        // fired mid-shutdown for a player who'd already been moved to Limbo.
        if (runManager.state == RunState.REROLLING) return
        val wasFrozen = isFrozen
        missing.add(uuid)
        lastMissingName = playerName
        applyTransition(wasFrozen, "Run frozen - waiting for $playerName to reconnect.")

        publish(RedisSchema.Channels.ROSTER_MEMBER_DISCONNECTED, RedisEvent.RosterMemberDisconnected.serializer()) {
            RedisEvent.RosterMemberDisconnected(runManager.runId, uuid.toString())
        }
    }

    fun onReconnect(uuid: UUID) {
        if (!missing.remove(uuid)) return
        applyTransition(true, "Everyone's back - the run resumes!")

        publish(RedisSchema.Channels.ROSTER_MEMBER_RECONNECTED, RedisEvent.RosterMemberReconnected.serializer()) {
            RedisEvent.RosterMemberReconnected(runManager.runId, uuid.toString())
        }
    }

    /** Used when an operator kicks a still-missing player from the run - they're never
     *  coming back, so stop waiting for them instead of requiring a real reconnect. */
    fun forgetMissingPlayer(uuid: UUID) {
        if (!missing.remove(uuid)) return
        applyTransition(true, "An operator kicked $lastMissingName from the run - the run resumes!")
    }

    /** The admin panel's force-freeze/unfreeze action. */
    fun setAdminForced(frozen: Boolean) {
        if (adminForced == frozen) return
        val wasFrozen = isFrozen
        adminForced = frozen
        applyTransition(
            wasFrozen,
            if (frozen) "An operator froze the run." else "An operator resumed the run.",
        )
    }

    private fun applyTransition(wasFrozen: Boolean, message: String) {
        val nowFrozen = isFrozen
        if (nowFrozen != wasFrozen) {
            server.tickRateManager().setFrozen(nowFrozen)
            if (nowFrozen) {
                captureAllPositions()
            } else {
                frozenPositions.clear()
            }
            server.playerList.broadcastSystemMessage(Component.literal(message), false)
            logger.info(message)
        }
        broadcastState()
    }

    private fun captureAllPositions() {
        frozenPositions.clear()
        for (player in server.playerList.players) {
            if (runManager.isParticipant(player.getUUID())) capturePosition(player)
        }
    }

    private fun capturePosition(player: ServerPlayer) {
        frozenPositions[player.getUUID()] = doubleArrayOf(player.getX(), player.getY(), player.getZ())
    }

    private fun broadcastState() {
        val payload = FreezeStatePayload(isFrozen, lastMissingName)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    private fun <T> publish(channel: String, serializer: kotlinx.serialization.KSerializer<T>, event: () -> T) {
        val bus = redis ?: return
        runCatching { bus.publish(channel, serializer, event()) }
            .onFailure { logger.warn("Failed to publish to {}: {}", channel, it.message) }
    }
}
