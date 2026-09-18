package com.hardcode.client

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

/**
 * Entry point for the required client mod. Phase 0: scaffolding only.
 *
 * HUD (hearts/food/armor + compass direction), tab-list integration, freeze fog overlay
 * and death drama (red flash/titles) land in later phases. All rendering here must stick
 * to documented Fabric render hooks (HudRenderCallback / WorldRenderEvents) to stay
 * Sodium/Iris-safe - see the project plan, section 9.
 */
object HardcoreClientMod : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-client")

    override fun onInitializeClient() {
        logger.info("Hardcore Client mod initializing (scaffolding phase)")
    }
}
