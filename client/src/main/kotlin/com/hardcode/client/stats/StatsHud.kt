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
 */
object StatsHud {
    private const val PANEL_BG_ARGB = 0xA0101018.toInt()
    private const val ROW_HEIGHT = 12
    private const val LEFT = 8
    private const val TOP = 40

    fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!StatsClientState.visible) return

        val client = Minecraft.getInstance()
        val self = client.player ?: return
        val others = StatsClientState.snapshot.players.filter { it.uuid != self.getUUID().toString() }
        if (others.isEmpty()) return

        val width = 220
        val height = 20 + others.size * ROW_HEIGHT
        graphics.fill(LEFT, TOP, LEFT + width, TOP + height, PANEL_BG_ARGB)
        graphics.text(client.font, "Run Stats", LEFT + 4, TOP + 4, CommonColors.WHITE)

        var y = TOP + 16
        for (stat in others) {
            val dx = stat.x - self.getX()
            val dz = stat.z - self.getZ()
            val distance = sqrt(dx * dx + dz * dz).toInt()
            val direction = relativeDirectionLabel(dx, dz, self.getYRot())

            val line = "${stat.name}  HP ${stat.health.toInt()}/${stat.maxHealth.toInt()}  " +
                "Food ${stat.food}  Armor ${stat.armor}  $direction ${distance}m"
            val color = if (stat.health <= stat.maxHealth * 0.3f) CommonColors.RED else CommonColors.WHITE
            graphics.text(client.font, line, LEFT + 4, y, color)
            y += ROW_HEIGHT
        }
    }

    /** 0 degrees = directly ahead of where the player is looking, increasing clockwise. */
    private fun relativeDirectionLabel(dx: Double, dz: Double, myYawDegrees: Float): String {
        val targetYaw = Math.toDegrees(atan2(-dx, dz))
        var relative = (targetYaw - myYawDegrees) % 360.0
        if (relative < 0) relative += 360.0

        return when {
            relative < 22.5 || relative >= 337.5 -> "↑" // AHEAD ↑
            relative < 67.5 -> "↗" // AHEAD-RIGHT ↗
            relative < 112.5 -> "→" // RIGHT →
            relative < 157.5 -> "↘" // BEHIND-RIGHT ↘
            relative < 202.5 -> "↓" // BEHIND ↓
            relative < 247.5 -> "↙" // BEHIND-LEFT ↙
            relative < 292.5 -> "←" // LEFT ←
            else -> "↖" // AHEAD-LEFT ↖
        }
    }
}
