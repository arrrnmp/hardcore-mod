package com.aaronmompie.client.death

import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor

/** A fading full-screen red flash on death - HUD render pass, so it's Sodium/Iris-safe. */
object DeathFlashHud {
    fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        val alpha = DeathFlashState.currentAlpha()
        if (alpha <= 0f) return

        val alphaByte = (alpha * 0xA0).toInt().coerceIn(0, 255)
        val colorArgb = (alphaByte shl 24) or 0x990000
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), colorArgb)
    }
}
