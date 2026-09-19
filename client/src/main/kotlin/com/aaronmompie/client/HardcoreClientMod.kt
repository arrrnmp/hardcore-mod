package com.aaronmompie.client

import com.aaronmompie.client.admin.AdminActionPayload
import com.aaronmompie.client.admin.AdminScreen
import com.aaronmompie.client.admin.AdminSnapshotPayload
import com.aaronmompie.client.death.DeathFlashHud
import com.aaronmompie.client.death.DeathFlashPayload
import com.aaronmompie.client.death.DeathFlashState
import com.aaronmompie.client.freeze.FreezeClientState
import com.aaronmompie.client.freeze.FreezeHud
import com.aaronmompie.client.freeze.FreezeInputLock
import com.aaronmompie.client.freeze.FreezeStatePayload
import com.aaronmompie.client.stats.StatsClientState
import com.aaronmompie.client.stats.StatsHud
import com.aaronmompie.client.stats.StatsKeybind
import com.aaronmompie.client.stats.StatsSnapshotPayload
import com.aaronmompie.client.storage.HallOfShamePayload
import com.aaronmompie.client.storage.HallOfShameScreen
import com.aaronmompie.common.model.AdminSnapshot
import com.aaronmompie.common.model.LeaderboardEntry
import com.aaronmompie.common.model.StatsSnapshot
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.resources.Identifier
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
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
        PayloadTypeRegistry.clientboundPlay().register(HallOfShamePayload.TYPE, HallOfShamePayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(FreezeStatePayload.TYPE) { payload, _ ->
            FreezeClientState.update(payload)
        }

        ClientPlayNetworking.registerGlobalReceiver(AdminSnapshotPayload.TYPE) { payload, context ->
            val snapshot = runCatching { json.decodeFromString<AdminSnapshot>(payload.snapshotJson) }.getOrNull() ?: return@registerGlobalReceiver
            val currentScreen = currentScreen(context.client())
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

        ClientPlayNetworking.registerGlobalReceiver(HallOfShamePayload.TYPE) { payload, context ->
            val entries = runCatching { json.decodeFromString<List<LeaderboardEntry>>(payload.leaderboardJson) }.getOrNull()
                ?: return@registerGlobalReceiver
            context.client().setScreenAndShow(HallOfShameScreen(entries))
        }

        StatsKeybind.register()
        FreezeInputLock.register()

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("aaronmompie", "freeze_hud"), FreezeHud::extractRenderState)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("aaronmompie", "death_flash_hud"), DeathFlashHud::extractRenderState)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("aaronmompie", "stats_hud"), StatsHud::extractRenderState)
    }

    /**
     * Current screen across MC targets: 26.2+ exposes it as `Gui.screen()`, while 26.1.x
     * only has the public `Minecraft.screen` field - neither exists on the other version,
     * so this resolves it reflectively instead of branching source sets for one call.
     */
    private fun currentScreen(client: Minecraft): Screen? {
        runCatching {
            client.gui.javaClass.getMethod("screen").invoke(client.gui) as? Screen
        }.getOrNull()?.let { return it }
        return runCatching {
            client.javaClass.getField("screen").get(client) as? Screen
        }.getOrNull()
    }
}
