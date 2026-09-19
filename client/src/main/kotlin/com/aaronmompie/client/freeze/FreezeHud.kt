package com.aaronmompie.client.freeze

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.CommonColors

/**
 * Screen-space fog overlay + "Run frozen" banner, drawn in the HUD render pass (not a
 * world-space effect) so it stays Sodium/Iris-safe - see the project plan, section 5/9: no
 * mixins into either mod's rendering internals, just the documented HUD layer API.
 */
object FreezeHud {
    private const val FOG_COLOR_ARGB = 0x80101018.toInt()
    private const val BANNER_BG_ARGB = 0xB0400000.toInt()
    private const val BANNER_HEIGHT = 24

    fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!FreezeClientState.frozen) return

        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), FOG_COLOR_ARGB)
        graphics.fill(0, 0, graphics.guiWidth(), BANNER_HEIGHT, BANNER_BG_ARGB)

        val waitingFor = FreezeClientState.waitingForPlayerName
        val timer = elapsedSuffix()
        val message = if (waitingFor.isEmpty()) {
            "Run frozen$timer"
        } else {
            "Run frozen - waiting for $waitingFor to reconnect$timer"
        }
        graphics.centeredText(Minecraft.getInstance().font, message, graphics.guiWidth() / 2, 8, CommonColors.WHITE)
    }

    /** Live " · mm:ss" freeze timer; empty when the server didn't send a start timestamp. */
    private fun elapsedSuffix(): String {
        val since = FreezeClientState.frozenSinceEpochMs
        if (since <= 0) return ""
        val seconds = ((System.currentTimeMillis() - since).coerceAtLeast(0) / 1000)
        return " · ${seconds / 60}:${"%02d".format(seconds % 60)}"
    }
}
