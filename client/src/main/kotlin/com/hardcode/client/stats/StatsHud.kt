package com.hardcode.client.stats

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.CommonColors
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Keybind-toggled statistics overlay: each teammate's health/food/armor plus a direction
 * relative to where the local player is currently facing, computed client-side from the
 * server's periodic [com.hardcode.common.model.StatsSnapshot] broadcasts. Direction is an
 * 8-way Unicode arrow glyph (U+2190-U+2199, the basic Arrows block, which Minecraft's font
 * renders directly rather than falling back to a missing-glyph box) rather than a rotated
 * arrow graphic or icon sprites - no asset pipeline set up yet - see the project plan
 * section 9 for the fuller vision this is a stand-in for.
 *
 * Anchored top-right (not top-left, which the vanilla F3 debug screen and several other HUD
 * elements already claim) as a two-line "card" per player: name + direction on the first
 * line, color-coded HP/Food/Armor on the second.
 */
object StatsHud {
    private const val PANEL_BG_ARGB = 0xC8101018.toInt()
    private const val BORDER_ARGB = 0xFF4A3A6A.toInt()
    private const val HEADER_COLOR = 0xFFC9A8FF.toInt()
    private const val SEPARATOR_ARGB = 0x804A3A6A.toInt()
    private const val FOOD_COLOR = 0xFFE8A33D.toInt()
    private const val ARMOR_COLOR = 0xFF6EC6FF.toInt()

    private const val MARGIN = 6
    private const val WIDTH = 190
    private const val ROW_HEIGHT = 22
    private const val HEADER_HEIGHT = 16
    private const val PADDING = 5

    fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!StatsClientState.visible) return

        val client = Minecraft.getInstance()
        val self = client.player ?: return
        val font = client.font
        val others = StatsClientState.snapshot.players.filter { it.uuid != self.getUUID().toString() }
        if (others.isEmpty()) return

        val left = graphics.guiWidth() - WIDTH - MARGIN
        val top = MARGIN
        val height = HEADER_HEIGHT + others.size * ROW_HEIGHT + PADDING

        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + height + 1, BORDER_ARGB)
        graphics.fill(left, top, left + WIDTH, top + height, PANEL_BG_ARGB)
        graphics.text(font, "RUN STATS", left + PADDING, top + 4, HEADER_COLOR)
        graphics.fill(left + PADDING, top + HEADER_HEIGHT - 2, left + WIDTH - PADDING, top + HEADER_HEIGHT - 1, SEPARATOR_ARGB)

        var y = top + HEADER_HEIGHT + 3
        for (stat in others) {
            drawPlayerCard(graphics, font, self, stat, left + PADDING, y, WIDTH - PADDING * 2)
            y += ROW_HEIGHT
        }
    }

    private fun drawPlayerCard(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        self: net.minecraft.client.player.LocalPlayer,
        stat: com.hardcode.common.model.PlayerStat,
        x: Int,
        y: Int,
        width: Int,
    ) {
        val dx = stat.x - self.getX()
        val dz = stat.z - self.getZ()
        val distance = sqrt(dx * dx + dz * dz).toInt()
        val direction = relativeDirectionArrow(dx, dz, self.getYRot())

        graphics.text(font, stat.name, x, y, CommonColors.YELLOW)

        val suffix = "$direction ${distance}m"
        val suffixWidth = font.width(suffix)
        graphics.text(font, suffix, x + width - suffixWidth, y, CommonColors.LIGHT_GRAY)

        val healthColor = if (stat.health <= stat.maxHealth * 0.3f) CommonColors.SOFT_RED else CommonColors.GREEN
        var cursorX = x
        cursorX += drawSegment(graphics, font, "HP ${stat.health.toInt()}/${stat.maxHealth.toInt()}", cursorX, y + 11, healthColor)
        cursorX += drawSegment(graphics, font, "  Food ${stat.food}", cursorX, y + 11, FOOD_COLOR)
        drawSegment(graphics, font, "  Armor ${stat.armor}", cursorX, y + 11, ARMOR_COLOR)
    }

    private fun drawSegment(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        text: String,
        x: Int,
        y: Int,
        color: Int,
    ): Int {
        graphics.text(font, text, x, y, color)
        return font.width(text)
    }

    /** 0 degrees = directly ahead of where the player is looking, increasing clockwise. */
    private fun relativeDirectionArrow(dx: Double, dz: Double, myYawDegrees: Float): String {
        val targetYaw = Math.toDegrees(atan2(-dx, dz))
        var relative = (targetYaw - myYawDegrees) % 360.0
        if (relative < 0) relative += 360.0

        return when {
            relative < 22.5 || relative >= 337.5 -> "↑"
            relative < 67.5 -> "↗"
            relative < 112.5 -> "→"
            relative < 157.5 -> "↘"
            relative < 202.5 -> "↓"
            relative < 247.5 -> "↙"
            relative < 292.5 -> "←"
            else -> "↖"
        }
    }
}
