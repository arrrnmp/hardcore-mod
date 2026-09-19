package com.aaronmompie.client.stats

import com.aaronmompie.client.ui.HudSprites
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.CommonColors
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Keybind-toggled statistics overlay: each teammate's real vanilla sprites - hearts (with
 * hardcore/absorbing variants), armor, hunger, air bubbles while underwater, experience bar
 * + level - plus a direction relative to where the local player is currently facing,
 * computed client-side from the server's periodic
 * [com.aaronmompie.common.model.StatsSnapshot] broadcasts. Direction is an 8-way Unicode
 * arrow glyph (U+2190-U+2199, the basic Arrows block, which Minecraft's font renders
 * directly rather than falling back to a missing-glyph box).
 *
 * Sprite paths are identical across this repo's MC targets (verified by javap on 26.1.2,
 * 26.2 and 26.3); the one thing that moved is the RenderPipeline type, which [HudSprites]
 * isolates. Anchored top-right (not top-left, which the vanilla F3 debug screen and several
 * other HUD elements already claim) as a "card" per player in exact vanilla stacking order:
 * armor, absorption sharing a row with air (while underwater), heart rows with hunger
 * right-aligned on the last one, then the experience bar snug below with its level
 * centered in the gap between hearts and hunger. Absorption hearts render without the
 * dark container behind them, exactly like vanilla.
 */
object StatsHud {
    private const val PANEL_BG_ARGB = 0xC8101018.toInt()
    private const val BORDER_ARGB = 0xFF4A3A6A.toInt()
    private const val HEADER_COLOR = 0xFFC9A8FF.toInt()
    private const val SEPARATOR_ARGB = 0x804A3A6A.toInt()
    private const val EXP_LEVEL_COLOR = 0xFF80FF20.toInt()

    private const val MARGIN = 6
    private const val WIDTH = 190
    private const val HEADER_HEIGHT = 16
    private const val PADDING = 5
    private const val NAME_HEIGHT = 10
    private const val ICON_ROW_HEIGHT = 9
    private const val EXP_BAR_HEIGHT = 5
    private const val EXP_BAR_GAP = 1
    private const val CARD_BOTTOM_PAD = 4
    private const val ICONS_PER_ROW = 10
    private const val ICON_ROW_WIDTH = (ICONS_PER_ROW - 1) * HudSprites.STRIDE + HudSprites.ICON
    private const val EXP_BAR_FULL_WIDTH = 182

    fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!StatsClientState.visible) return

        val client = Minecraft.getInstance()
        val self = client.player ?: return
        val font = client.font
        val others = StatsClientState.snapshot.players.filter { it.uuid != self.getUUID().toString() }
        if (others.isEmpty()) return

        val left = graphics.guiWidth() - WIDTH - MARGIN
        val top = MARGIN
        val height = HEADER_HEIGHT + others.sumOf { cardHeight(it) } + PADDING

        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + height + 1, BORDER_ARGB)
        graphics.fill(left, top, left + WIDTH, top + height, PANEL_BG_ARGB)
        graphics.text(font, "RUN STATS", left + PADDING, top + 4, HEADER_COLOR)
        graphics.fill(left + PADDING, top + HEADER_HEIGHT - 2, left + WIDTH - PADDING, top + HEADER_HEIGHT - 1, SEPARATOR_ARGB)

        var y = top + HEADER_HEIGHT + 3
        for (stat in others) {
            drawPlayerCard(graphics, font, self, stat, left + PADDING, y, WIDTH - PADDING * 2)
            y += cardHeight(stat)
        }
    }

    private fun cardHeight(stat: com.aaronmompie.common.model.PlayerStat): Int {
        val rows = heartRowCount(stat)
        var height = NAME_HEIGHT + ICON_ROW_HEIGHT + maxOf(rows, 1) * ICON_ROW_HEIGHT +
            EXP_BAR_GAP + EXP_BAR_HEIGHT + CARD_BOTTOM_PAD
        if (hasAbsorptionOrAir(stat)) height += ICON_ROW_HEIGHT
        return height
    }

    private fun showsAir(stat: com.aaronmompie.common.model.PlayerStat): Boolean =
        stat.maxAir > 0 && stat.air < stat.maxAir

    private fun hasAbsorptionOrAir(stat: com.aaronmompie.common.model.PlayerStat): Boolean {
        val (_, absorption) = heartSlots(stat)
        return absorption > 0 || showsAir(stat)
    }

    /** Normal + absorption slot counts. */
    private fun heartSlots(stat: com.aaronmompie.common.model.PlayerStat): Pair<Int, Int> {
        val normal = if (stat.maxHealth > 0) ceil(stat.maxHealth / 2f).toInt() else 0
        val absorption = ceil(stat.absorption / 2f).toInt().coerceAtLeast(0)
        return normal to absorption
    }

    /** Heart rows for normal health; hunger shares the last one (or gets its own row). */
    private fun heartRowCount(stat: com.aaronmompie.common.model.PlayerStat): Int {
        val (normal, _) = heartSlots(stat)
        return ceil(normal / ICONS_PER_ROW.toFloat()).toInt()
    }

    private fun drawPlayerCard(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        self: net.minecraft.client.player.LocalPlayer,
        stat: com.aaronmompie.common.model.PlayerStat,
        x: Int,
        y: Int,
        width: Int,
    ) {
        val dx = stat.x - self.getX()
        val dz = stat.z - self.getZ()
        val distance = sqrt(dx * dx + dz * dz).toInt()
        val direction = relativeDirectionArrow(dx, dz, self.getYRot())
        val hardcore = Minecraft.getInstance().level?.levelData?.isHardcore() == true

        graphics.text(font, stat.name, x, y, CommonColors.YELLOW)

        val suffix = "$direction ${distance}m"
        val suffixWidth = font.width(suffix)
        graphics.text(font, suffix, x + width - suffixWidth, y, CommonColors.LIGHT_GRAY)

        var rowY = y + NAME_HEIGHT
        // Vanilla order: armor always (even empty), then absorption sharing a row with air,
        // then normal hearts with hunger on the last row.
        drawArmorRow(graphics, stat.armor, x, rowY)
        rowY += ICON_ROW_HEIGHT
        val (normalSlots, absorptionSlots) = heartSlots(stat)
        val foodX = x + width - ICON_ROW_WIDTH
        if (absorptionSlots > 0 || showsAir(stat)) {
            if (absorptionSlots > 0) {
                drawHeartSlots(graphics, stat, hardcore, x, rowY, normalSlots, normalSlots + absorptionSlots)
            }
            if (showsAir(stat)) {
                drawAirRow(graphics, stat.air, foodX, rowY)
            }
            rowY += ICON_ROW_HEIGHT
        }
        val rows = heartRowCount(stat)
        if (rows > 0) {
            drawHeartSlots(graphics, stat, hardcore, x, rowY, 0, normalSlots)
            drawFoodRow(graphics, stat.food, foodX, rowY + (rows - 1) * ICON_ROW_HEIGHT)
        } else {
            drawFoodRow(graphics, stat.food, foodX, rowY)
        }
        if (stat.expLevel > 0) {
            val levelText = "${stat.expLevel}"
            val heartsEnd = if (rows > 0) x + ICON_ROW_WIDTH else x
            val gapCenter = (heartsEnd + foodX) / 2
            val foodRowY = rowY + maxOf(rows - 1, 0) * ICON_ROW_HEIGHT
            graphics.text(font, levelText, gapCenter - font.width(levelText) / 2, foodRowY, EXP_LEVEL_COLOR)
        }
        rowY += maxOf(rows, 1) * ICON_ROW_HEIGHT
        drawExpBar(graphics, stat, x, rowY + EXP_BAR_GAP, width)
    }

    private fun iconX(x: Int, slot: Int) = x + slot * HudSprites.STRIDE

    private fun drawArmorRow(graphics: GuiGraphicsExtractor, armor: Int, x: Int, y: Int) {
        val full = armor / 2
        val half = armor % 2
        for (i in 0 until ICONS_PER_ROW) {
            val ix = iconX(x, i)
            HudSprites.sprite(graphics, "hud/armor_empty", ix, y)
            if (i < full) {
                HudSprites.sprite(graphics, "hud/armor_full", ix, y)
            } else if (i == full && half == 1) {
                HudSprites.sprite(graphics, "hud/armor_half", ix, y)
            }
        }
    }

    /** Draws heart slots [from, to) as a continuous left-to-right flow across rows. */
    private fun drawHeartSlots(
        graphics: GuiGraphicsExtractor,
        stat: com.aaronmompie.common.model.PlayerStat,
        hardcore: Boolean,
        x: Int,
        y: Int,
        from: Int,
        to: Int,
    ) {
        val (normalSlots, _) = heartSlots(stat)
        val halfHearts = ceil(stat.health).toInt().coerceAtLeast(0)
        val fullHearts = halfHearts / 2
        val halfHeart = halfHearts % 2
        val halfAbsorb = ceil(stat.absorption).toInt().coerceAtLeast(0)
        val fullAbsorb = halfAbsorb / 2
        val halfAbsorbRem = halfAbsorb % 2
        val hc = if (hardcore) "hardcore_" else ""

        for (i in from until to) {
            val ix = iconX(x, (i - from) % ICONS_PER_ROW)
            val iy = y + ((i - from) / ICONS_PER_ROW) * ICON_ROW_HEIGHT
            if (i < normalSlots) {
                HudSprites.sprite(graphics, "hud/heart/container${if (hardcore) "_hardcore" else ""}", ix, iy)
                when {
                    i < fullHearts -> HudSprites.sprite(graphics, "hud/heart/${hc}full", ix, iy)
                    i == fullHearts && halfHeart == 1 -> HudSprites.sprite(graphics, "hud/heart/${hc}half", ix, iy)
                }
            } else {
                // Absorption hearts render solid, with no dark container behind them (vanilla).
                val j = i - normalSlots
                when {
                    j < fullAbsorb -> HudSprites.sprite(graphics, "hud/heart/absorbing_${hc}full", ix, iy)
                    j == fullAbsorb && halfAbsorbRem == 1 -> HudSprites.sprite(graphics, "hud/heart/absorbing_${hc}half", ix, iy)
                }
            }
        }
    }

    private fun drawFoodRow(graphics: GuiGraphicsExtractor, food: Int, x: Int, y: Int) {
        val full = food / 2
        val half = food % 2
        for (i in 0 until ICONS_PER_ROW) {
            val ix = iconX(x, i)
            HudSprites.sprite(graphics, "hud/food_empty", ix, y)
            if (i < full) {
                HudSprites.sprite(graphics, "hud/food_full", ix, y)
            } else if (i == full && half == 1) {
                HudSprites.sprite(graphics, "hud/food_half", ix, y)
            }
        }
    }

    private fun drawAirRow(graphics: GuiGraphicsExtractor, air: Int, x: Int, y: Int) {
        for (i in 0 until ICONS_PER_ROW) {
            val ix = iconX(x, i)
            val remaining = air - i * 30
            HudSprites.sprite(graphics, "hud/air_empty", ix, y)
            if (remaining >= 30) {
                HudSprites.sprite(graphics, "hud/air", ix, y)
            } else if (remaining > 0) {
                HudSprites.sprite(graphics, "hud/air_bursting", ix, y)
            }
        }
    }

    private fun drawExpBar(
        graphics: GuiGraphicsExtractor,
        stat: com.aaronmompie.common.model.PlayerStat,
        x: Int,
        y: Int,
        width: Int,
    ) {
        val barWidth = minOf(EXP_BAR_FULL_WIDTH, width)
        HudSprites.sprite(graphics, "hud/experience_bar_background", x, y, barWidth, EXP_BAR_HEIGHT)
        val filled = (stat.expProgress.coerceIn(0f, 1f) * barWidth).toInt()
        if (filled > 0) {
            HudSprites.spriteCrop(
                graphics, "hud/experience_bar_progress",
                EXP_BAR_FULL_WIDTH, EXP_BAR_HEIGHT, x, y, filled, EXP_BAR_HEIGHT,
            )
        }
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
