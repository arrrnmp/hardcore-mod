package com.hardcode.game.stats

import com.hardcode.common.model.PlayerStat
import com.hardcode.common.model.StatsSnapshot
import com.hardcode.game.run.RunManager
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer

/**
 * Broadcasts everyone's health/food/armor/position every [intervalTicks] ticks (not every
 * tick - the client only needs this batched, per the project plan section 9) so the client's
 * statistics overlay has something to show.
 */
class StatsBroadcaster(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val intervalTicks: Int = 10,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var tickCounter = 0

    fun tick() {
        tickCounter++
        if (tickCounter % intervalTicks != 0) return

        val participants = server.playerList.players.filter { runManager.isParticipant(it.getUUID()) }
        if (participants.isEmpty()) return

        val stats = participants.map { player ->
            PlayerStat(
                uuid = player.getUUID().toString(),
                name = player.getGameProfile().name,
                health = player.getHealth(),
                maxHealth = player.getMaxHealth(),
                food = player.getFoodData().getFoodLevel(),
                armor = player.getArmorValue(),
                x = player.getX(),
                y = player.getY(),
                z = player.getZ(),
                dimension = player.level().dimension().identifier().toString(),
            )
        }

        val payload = StatsSnapshotPayload(json.encodeToString(StatsSnapshot(stats)))
        for (player in participants) {
            ServerPlayNetworking.send(player, payload)
        }
    }
}
