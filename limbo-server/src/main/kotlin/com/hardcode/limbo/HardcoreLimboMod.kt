package com.hardcode.limbo

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

/**
 * Entry point for the limbo-server mod. Phase 0: scaffolding only.
 *
 * The End-like void dimension, immortality/adventure+fly, dragon-free setup and the
 * Y<0 safety-net teleport land in Phase 3 (see the project plan).
 */
object HardcoreLimboMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-limbo")

    override fun onInitialize() {
        logger.info("Hardcore Limbo Server mod initializing (scaffolding phase)")
    }
}
