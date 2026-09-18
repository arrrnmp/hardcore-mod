package com.hardcode.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors

/**
 * A fully custom-rendered button - no vanilla button sprite, so admin/leaderboard screens
 * actually look like something built for this mod rather than a re-skinned menu, per the
 * project plan's explicit call for the admin panel to "not look default". [active] toggles a
 * highlighted style, used for the admin panel's current-tab indicator.
 */
class PanelButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    var highlighted: Boolean = false,
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, width, height, message) {
    companion object {
        private const val BG_NORMAL = 0xFF2A2035.toInt()
        private const val BG_HOVER = 0xFF3D2E52.toInt()
        private const val BG_HIGHLIGHTED = 0xFF5B3E8A.toInt()
        private const val BORDER = 0xFF6EC6FF.toInt()
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val bg = if (highlighted) BG_HIGHLIGHTED else if (isHoveredOrFocused) BG_HOVER else BG_NORMAL
        graphics.fill(x, y, x + width, y + height, bg)
        if (highlighted || isHoveredOrFocused) {
            graphics.fill(x, y + height - 2, x + width, y + height, BORDER)
        }
        val font = Minecraft.getInstance().font
        graphics.centeredText(font, message, x + width / 2, y + (height - 8) / 2, CommonColors.WHITE)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        onPress()
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}
