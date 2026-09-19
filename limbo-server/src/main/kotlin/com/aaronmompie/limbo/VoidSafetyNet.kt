package com.aaronmompie.limbo

import net.minecraft.server.MinecraftServer

/**
 * Vanilla's `invulnerable` ability still lets void/out-of-world damage through, so a flying
 * player who dips below Y=0 would otherwise die anyway. Checked every few ticks (not every
 * tick - this is a cheap, non-urgent safety net, not a physics system) for every connected
 * player, since this whole server is Limbo.
 */
object VoidSafetyNet {
    private const val CHECK_INTERVAL_TICKS = 10L
    private var tickCounter = 0L

    fun tick(server: MinecraftServer) {
        tickCounter++
        if (tickCounter % CHECK_INTERVAL_TICKS != 0L) return

        for (player in server.playerList.players) {
            if (player.getY() < 0.0) {
                player.teleportTo(LimboPlayerSetup.SPAWN_X, LimboPlayerSetup.SPAWN_Y, LimboPlayerSetup.SPAWN_Z)
            }
        }
    }
}
