package com.hardcode.game

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

/**
 * Entry point for the game-server mod. Phase 0: scaffolding only - just proves the
 * toolchain (Loom + Kotlin + shaded common modules) loads correctly on a real server.
 *
 * Run lifecycle, roster tracking, death/vote handling, freeze mixin and Hall of Shame
 * persistence land in later phases (see the project plan).
 */
object HardcoreGameMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    override fun onInitialize() {
        logger.info("Hardcore Game Server mod initializing (scaffolding phase)")
    }
}
