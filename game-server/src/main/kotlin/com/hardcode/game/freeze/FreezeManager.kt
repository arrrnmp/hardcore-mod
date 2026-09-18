package com.hardcode.game.freeze

import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import com.hardcode.game.run.RunManager
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Freezes the whole run solid the moment any roster member disconnects, and un-freezes once
 * every missing member is back (or an operator kicks them from the run - see
 * [forgetMissingPlayer]). Also supports a manual operator override ([setAdminForced]) for the
 * admin panel's force-freeze/unfreeze action, layered on top of the same mechanism: frozen
 * whenever *either* someone's missing *or* an operator forced it.
 *
 * Built on vanilla's own `/tick freeze` primitive
 * ([net.minecraft.server.ServerTickRateManager.setFrozen]) rather than a custom mixin -
 * that's a real, shipped Mojang feature specifically designed to pause world simulation
 * (entity motion/AI, random/block ticks, weather, time) on a *live* multiplayer server
 * without kicking anyone, which is exactly this requirement (a player mid-jump stays
 * mid-jump; someone standing in lava stops taking damage) with far less risk than
 * hand-rolling the same thing via mixins into the tick loop.
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

    val isFrozen: Boolean
        get() = missing.isNotEmpty() || adminForced

    val missingPlayers: Set<UUID>
        get() = missing.toSet()

    /** Call on every join, including ones unrelated to freezing, to sync current state. */
    fun syncStateTo(player: ServerPlayer) {
        ServerPlayNetworking.send(player, FreezeStatePayload(isFrozen, lastMissingName))
    }

    fun onDisconnect(uuid: UUID, playerName: String) {
        if (!runManager.isParticipant(uuid)) return
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
            server.playerList.broadcastSystemMessage(Component.literal(message), false)
            logger.info(message)
        }
        broadcastState()
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
