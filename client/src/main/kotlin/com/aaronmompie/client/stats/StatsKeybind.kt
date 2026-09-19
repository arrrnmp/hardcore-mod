package com.aaronmompie.client.stats

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.SharedConstants
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

/** Registers the toggle key for [StatsHud] and consumes its clicks each client tick. */
object StatsKeybind {
    // 26.2 reads input via GLFW (keysyms), 26.3+ via SDL (scancodes), so the default "J"
    // key is a different raw int per backend and can't be a single compile-time constant
    // when both targets build from this one source tree. KeyMapping(String, int, Category)
    // itself is identical in both versions - only the int differs, picked at runtime below.
    private const val KEY_J_GLFW = 74 // GLFW_KEY_J
    private const val KEY_J_SDL = 13 // SDL_SCANCODE_J

    fun register() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aaronmompie", "keys"))
        val keyMapping = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.aaronmompie.stats", defaultJKey(), category),
        )

        ClientTickEvents.END_CLIENT_TICK.register {
            while (keyMapping.consumeClick()) {
                StatsClientState.toggle()
            }
        }
    }

    private fun defaultJKey(): Int {
        val minor = SharedConstants.getCurrentVersion().id()
            .removePrefix("26.")
            .substringBefore(".")
            .toIntOrNull() ?: 3
        return if (minor >= 3) KEY_J_SDL else KEY_J_GLFW
    }
}
