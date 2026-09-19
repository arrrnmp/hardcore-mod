package com.aaronmompie.limbo

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType

/**
 * Puts a joining/respawning player into the expected Limbo state: adventure mode, permanent
 * flight, and invulnerable. Invulnerable blocks nearly everything (fall, fire, mobs, ...) but
 * vanilla still lets void ("out of world") damage through even for invulnerable players -
 * that gap is exactly why [VoidSafetyNet] exists as a belt-and-braces Y<0 teleport.
 */
object LimboPlayerSetup {
    /** Center of the top block of the flat void platform - see dimension/overworld.json. */
    const val SPAWN_X: Double = 0.5
    const val SPAWN_Y: Double = 5.0
    const val SPAWN_Z: Double = 0.5

    fun apply(player: ServerPlayer) {
        player.setGameMode(GameType.ADVENTURE)

        val abilities = player.getAbilities()
        abilities.mayfly = true
        abilities.flying = true
        abilities.invulnerable = true
        player.onUpdateAbilities()

        player.teleportTo(SPAWN_X, SPAWN_Y, SPAWN_Z)
    }
}
