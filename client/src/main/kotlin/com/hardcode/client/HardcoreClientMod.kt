package com.hardcode.client

import com.hardcode.client.admin.AdminActionPayload
import com.hardcode.client.admin.AdminScreen
import com.hardcode.client.admin.AdminSnapshotPayload
import com.hardcode.client.death.DeathFlashHud
import com.hardcode.client.death.DeathFlashPayload
import com.hardcode.client.death.DeathFlashState
import com.hardcode.client.freeze.FreezeClientState
import com.hardcode.client.freeze.FreezeHud
import com.hardcode.client.freeze.FreezeStatePayload
import com.hardcode.client.stats.StatsClientState
import com.hardcode.client.stats.StatsHud
import com.hardcode.client.stats.StatsKeybind
import com.hardcode.client.stats.StatsSnapshotPayload
import com.hardcode.common.model.AdminSnapshot
import com.hardcode.common.model.StatsSnapshot
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * Entry point for the required client mod. Phase 6: adds death drama (red flash) and the
 * keybind-toggled statistics overlay on top of Phase 5's admin panel and Phase 4's freeze
 * fog/banner. All rendering here goes through Fabric's documented HUD/Screen layer APIs (2D
 * screen-space only), which is what keeps it Sodium/Iris-safe - see the project plan,
 * section 9.
 */
object HardcoreClientMod : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-client")
    private val json = Json { ignoreUnknownKeys = true }

    override fun onInitializeClient() {
        logger.info("Hardcore Client mod initializing")

        PayloadTypeRegistry.clientboundPlay().register(FreezeStatePayload.TYPE, FreezeStatePayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(AdminSnapshotPayload.TYPE, AdminSnapshotPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(AdminActionPayload.TYPE, AdminActionPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(DeathFlashPayload.TYPE, DeathFlashPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(FreezeStatePayload.TYPE) { payload, _ ->
            FreezeClientState.update(payload)
        }

        ClientPlayNetworking.registerGlobalReceiver(AdminSnapshotPayload.TYPE) { payload, context ->
            val snapshot = runCatching { json.decodeFromString<AdminSnapshot>(payload.snapshotJson) }.getOrNull() ?: return@registerGlobalReceiver
            val currentScreen = context.client().gui.screen()
            if (currentScreen is AdminScreen) {
                currentScreen.updateSnapshot(snapshot)
            } else {
                context.client().setScreenAndShow(AdminScreen(snapshot))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DeathFlashPayload.TYPE) { _, _ ->
            DeathFlashState.trigger()
        }

        ClientPlayNetworking.registerGlobalReceiver(StatsSnapshotPayload.TYPE) { payload, _ ->
            runCatching { json.decodeFromString<StatsSnapshot>(payload.snapshotJson) }
                .onSuccess { StatsClientState.snapshot = it }
        }

        StatsKeybind.register()

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hardcode", "freeze_hud"), FreezeHud::extractRenderState)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hardcode", "death_flash_hud"), DeathFlashHud::extractRenderState)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("hardcode", "stats_hud"), StatsHud::extractRenderState)
    }
}
