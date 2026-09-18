package com.hardcode.client.ui

import com.hardcode.common.model.LeaderboardEntry
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.CommonColors

/** Shared "pretty" Hall of Shame rendering for both the admin panel's tab and the
 *  standalone screen any player can open - gold/silver/bronze top three, zebra-striped rows. */
object LeaderboardRenderer {
    private const val ROW_HEIGHT = 18
    private const val ROW_BG_EVEN = 0x30FFFFFF
    private const val GOLD = 0xFFFFD700.toInt()
    private const val SILVER = 0xFFD0D0D0.toInt()
    private const val BRONZE = 0xFFCD8032.toInt()

    /** Returns the Y position after the last row drawn. */
    fun render(
        graphics: GuiGraphicsExtractor,
        font: Font,
        entries: List<LeaderboardEntry>,
        x: Int,
        y: Int,
        width: Int,
    ): Int {
        if (entries.isEmpty()) {
            graphics.text(font, "No deaths recorded yet - nicely done.", x, y, CommonColors.LIGHT_GRAY)
            return y + ROW_HEIGHT
        }

        var rowY = y
        for ((index, entry) in entries.withIndex()) {
            val rank = index + 1
            if (index % 2 == 0) graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT, ROW_BG_EVEN)

            val rankColor = when (rank) {
                1 -> GOLD
                2 -> SILVER
                3 -> BRONZE
                else -> CommonColors.LIGHT_GRAY
            }
            graphics.text(font, "#$rank", x + 4, rowY + 5, rankColor)
            graphics.text(font, entry.name, x + 32, rowY + 5, CommonColors.WHITE)

            val deathsText = "${entry.deaths} " + if (entry.deaths == 1) "death" else "deaths"
            val deathsWidth = font.width(deathsText)
            graphics.text(font, deathsText, x + width - deathsWidth - 6, rowY + 5, CommonColors.SOFT_RED)

            rowY += ROW_HEIGHT
        }
        return rowY
    }
}
