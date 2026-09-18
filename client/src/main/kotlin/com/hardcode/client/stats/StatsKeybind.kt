package com.hardcode.client.stats

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.sdl.SDLScancode

/** Registers the toggle key for [StatsHud] and consumes its clicks each client tick. */
object StatsKeybind {
    fun register() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hardcode", "keys"))
        val keyMapping = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.hardcode.stats", com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_J, category),
        )

        ClientTickEvents.END_CLIENT_TICK.register {
            while (keyMapping.consumeClick()) {
                StatsClientState.toggle()
            }
        }
    }
}
