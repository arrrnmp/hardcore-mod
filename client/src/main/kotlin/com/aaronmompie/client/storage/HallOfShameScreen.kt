package com.aaronmompie.client.storage

import com.aaronmompie.client.ui.LeaderboardRenderer
import com.aaronmompie.common.model.LeaderboardEntry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors

/**
 * Standalone Hall of Shame leaderboard, open to any player via `/run halloffame` - not
 * gated on operator status, per the project plan section 10 ("reachable by any player").
 * Same "pretty" rendering as the admin panel's tab (see [LeaderboardRenderer]), just without
 * everything else the admin panel has.
 */
class HallOfShameScreen(private val entries: List<LeaderboardEntry>) : Screen(Component.literal("Hall of Shame")) {
    companion object {
        private const val BG_TOP_ARGB = 0xF0140A20.toInt()
        private const val BG_BOTTOM_ARGB = 0xF00A0614.toInt()
        private const val HEADER_ACCENT = 0xFF8A5CE0.toInt()
        private const val CARD_BG = 0x40FFFFFF
        private const val LEFT = 20
        private const val TOP = 44
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, graphics.guiWidth(), graphics.guiHeight(), BG_TOP_ARGB, BG_BOTTOM_ARGB)
        graphics.fill(0, 0, graphics.guiWidth(), 28, HEADER_ACCENT)
        graphics.text(font, "HALL OF SHAME", LEFT, 10, CommonColors.WHITE)

        val width = 360
        graphics.fill(LEFT, TOP, LEFT + width, TOP + 20 + entries.size * 18, CARD_BG)
        LeaderboardRenderer.render(graphics, font, entries, LEFT, TOP + 2, width)

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
