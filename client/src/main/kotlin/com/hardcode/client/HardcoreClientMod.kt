package com.hardcode.client

import com.hardcode.client.freeze.FreezeClientState
import com.hardcode.client.freeze.FreezeHud
import com.hardcode.client.freeze.FreezeStatePayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * Entry point for the required client mod. Phase 4: renders the freeze fog overlay/banner
 * driven by the server's [FreezeStatePayload]; the statistics screen (hearts/food/armor +
 * compass direction) and death drama land in later phases. All rendering here goes through
 * Fabric's documented HUD layer API (2D screen-space only), which is what keeps it
 * Sodium/Iris-safe - see the project plan, section 9.
 */
object HardcoreClientMod : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-client")

    override fun onInitializeClient() {
        logger.info("Hardcore Client mod initializing")

        PayloadTypeRegistry.clientboundPlay().register(FreezeStatePayload.TYPE, FreezeStatePayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(FreezeStatePayload.TYPE) { payload, _ ->
            FreezeClientState.update(payload)
        }

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hardcode", "freeze_hud"), FreezeHud::extractRenderState)
    }
}
