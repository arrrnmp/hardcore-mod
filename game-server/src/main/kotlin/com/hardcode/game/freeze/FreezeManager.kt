package com.hardcode.game.freeze

import com.hardcode.common.model.RunState
import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import com.hardcode.game.config.ConfigManager
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
    private val configManager: ConfigManager,
) {
    private val logger = LoggerFactory.getLogger("hardcore-game")
    private val missing = linkedSetOf<UUID>()
    private var lastMissingName: String = ""
    private var adminForced = false
    private val frozenPositions = mutableMapOf<UUID, DoubleArray>()
    private val frozenVitals = mutableMapOf<UUID, FrozenVitals>()
    private var frozenSinceMs: Long = 0

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

    /** Call every server tick: pins frozen participants back to their freeze-start state. */
    fun tick() {
        if (!isFrozen) return
        val timeoutMinutes = configManager.config.maxFreezeMinutes
        if (timeoutMinutes > 0 && frozenSinceMs > 0 &&
            System.currentTimeMillis() - frozenSinceMs >= timeoutMinutes * 60_000L
        ) {
            forceUnfreeze("Freeze timed out after $timeoutMinutes min - the run resumes.")
            return
        }
        for ((uuid, pos) in frozenPositions) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.getX() != pos[0] || player.getY() != pos[1] || player.getZ() != pos[2]) {
                player.teleportTo(pos[0], pos[1], pos[2])
            }
            // A participant standing in a portal block when the freeze lands would otherwise
            // slip to the Nether mid-freeze; holding a short cooldown blocks initiation and it
            // decays on its own within ticks of unfreezing.
            if (player.getPortalCooldown() < 5) player.setPortalCooldown(5)
            restoreVitals(player, uuid)
            freezePotionDurations(player)
        }
    }

    /**
     * Food/saturation and air keep ticking down while frozen (same hole potion durations had),
     * so snapshot them at freeze-start and put them back every tick - again the same
     * self-correcting pattern as position pinning. Damage itself was never the issue
     * ([registerEnforcement] already cancels starvation/drowning); this is about the levels.
     */
    private fun restoreVitals(player: ServerPlayer, uuid: UUID) {
        val vitals = frozenVitals[uuid] ?: return
        if (player.getHealth() != vitals.health) player.setHealth(vitals.health)
        val food = player.getFoodData()
        if (food.getFoodLevel() != vitals.food) food.setFoodLevel(vitals.food)
        if (food.getSaturationLevel() != vitals.saturation) food.setSaturation(vitals.saturation)
        if (player.getAirSupply() != vitals.air) player.setAirSupply(vitals.air)
    }

    /**
     * Vanilla's tick-freeze stops the world but player effect timers keep ticking down, so a
     * long frozen wait would silently eat everyone's potions. Each server tick vanilla consumes
     * exactly one tick of duration, so put one tick back on every finite effect - net zero,
     * amplifier/particles/timers untouched. Runs in the same pass as position pinning, after
     * vanilla's own player tick, so ordering is always consume-then-restore.
     */
    private fun freezePotionDurations(player: ServerPlayer) {
        for (instance in player.getActiveEffects()) {
            if (instance.isInfiniteDuration()) continue
            instance.mapDuration { duration -> duration + 1 }
        }
    }

    /** Call on every join, including ones unrelated to freezing, to sync current state. */
    fun syncStateTo(player: ServerPlayer) {
        if (isFrozen && runManager.isParticipant(player.getUUID())) {
            capturePosition(player)
        }
        ServerPlayNetworking.send(player, payloadFor(player))
    }

    fun onDisconnect(uuid: UUID, playerName: String) {
        if (!runManager.isParticipant(uuid)) return
        // Only a live run is worth protecting. Once someone has died (VOTE_PENDING /
        // RUN_ENDED) everyone left is spectating in place and disconnects are expected
        // noise, not something to halt the world over - and during REROLLING the server is
        // already tearing down (players getting moved off to Limbo trigger their own
        // disconnect from this server), so freezing a run that's about to be destroyed
        // anyway is meaningless noise.
        if (runManager.state != RunState.RUN_ACTIVE) return
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

    /**
     * Force-resume no matter why we're frozen: stops waiting for disconnected players *and*
     * clears any admin-forced freeze. This is what the admin panel's unfreeze button means -
     * [setAdminForced] alone can never clear a disconnect-freeze (it early-returns when the
     * flag already matches, and `isFrozen` stays true while [missing] is non-empty), which is
     * why force-unfreezing a run with someone still offline used to silently do nothing.
     * Missing players stay on the roster; if they reconnect later, [onReconnect] simply
     * finds nothing to remove and the run carries on.
     */
    fun forceUnfreeze(message: String = "An operator force-resumed the run.") {
        val wasFrozen = isFrozen
        missing.clear()
        lastMissingName = ""
        adminForced = false
        frozenSinceMs = 0
        applyTransition(wasFrozen, message)
    }

    private fun applyTransition(wasFrozen: Boolean, message: String) {
        val nowFrozen = isFrozen
        if (nowFrozen != wasFrozen) {
            server.tickRateManager().setFrozen(nowFrozen)
            if (nowFrozen) {
                frozenSinceMs = System.currentTimeMillis()
                captureAllPositions()
                // Cancel any in-progress item use (mid-bite food, drawn bow): it would otherwise
                // finish while frozen since use ticks aren't gated. New uses can't start anyway
                // (attack/use cancelled + client input locked).
                for (player in server.playerList.players) {
                    if (runManager.isParticipant(player.getUUID()) && player.isUsingItem()) {
                        player.stopUsingItem()
                    }
                }
            } else {
                frozenPositions.clear()
                frozenVitals.clear()
                frozenSinceMs = 0
            }
            server.playerList.broadcastSystemMessage(Component.literal(message), false)
            logger.info(message)
        }
        broadcastState()
    }

    private fun captureAllPositions() {
        frozenPositions.clear()
        frozenVitals.clear()
        for (player in server.playerList.players) {
            if (runManager.isParticipant(player.getUUID())) capturePosition(player)
        }
    }

    private fun capturePosition(player: ServerPlayer) {
        frozenPositions[player.getUUID()] = doubleArrayOf(player.getX(), player.getY(), player.getZ())
        val food = player.getFoodData()
        frozenVitals[player.getUUID()] = FrozenVitals(
            player.getHealth(),
            food.getFoodLevel(),
            food.getSaturationLevel(),
            player.getAirSupply(),
        )
    }

    /** Health/food/saturation/air snapshot taken at freeze-start, restored every frozen tick. */
    private data class FrozenVitals(val health: Float, val food: Int, val saturation: Float, val air: Int)

    private fun broadcastState() {
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payloadFor(player))
        }
    }

    /** Non-participants (kicked players, spectating outside the roster) are never enforced
     *  against (see [isBlocked]), so they must never be told they're frozen either - otherwise
     *  their client would show the freeze fog/banner and (via the client's own input lock)
     *  suppress their controls for no reason. */
    private fun payloadFor(player: ServerPlayer): FreezeStatePayload =
        if (runManager.isParticipant(player.getUUID())) {
            FreezeStatePayload(isFrozen, lastMissingName, frozenSinceMs)
        } else {
            FreezeStatePayload(false, "", 0)
        }

    private fun <T> publish(channel: String, serializer: kotlinx.serialization.KSerializer<T>, event: () -> T) {
        val bus = redis ?: return
        runCatching { bus.publish(channel, serializer, event()) }
            .onFailure { logger.warn("Failed to publish to {}: {}", channel, it.message) }
    }
}
