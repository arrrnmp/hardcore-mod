package com.aaronmompie.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * Draws vanilla HUD sprites (hearts/armor/food/air/exp bar) through
 * [GuiGraphicsExtractor.blitSprite]. Two cross-version hazards, both verified by javap
 * against 26.1.2, 26.2 and 26.3 and isolated here so callers stay version-clean:
 *
 * - Sprite PATHS are byte-identical on all three targets (hud/heart/…, hud/armor_…,
 *   hud/food_…, hud/air…, hud/experience_bar_…), so they are plain string constants.
 * - The RenderPipeline TYPE moved between targets
 *   (com.mojang.blaze3d.pipeline on 26.1.2/26.2, com.mojang.renderpearl.api.pipeline on
 *   26.3), so neither the `RenderPipelines.GUI_TEXTURED` field nor the blitSprite overloads
 *   can be referenced statically - they are resolved reflectively once, then cached. If
 *   resolution ever fails the draws become silent no-ops (one warning, not per-frame spam).
 */
object HudSprites {
    const val ICON = 9

    /** Vanilla icon stride: 9px sprites overlapped by 1px, exactly like the vanilla HUD. */
    const val STRIDE = 8

    private val logger = LoggerFactory.getLogger("hardcore-client")

    private data class SpriteApi(val pipeline: Any, val blit: java.lang.reflect.Method, val blitCrop: java.lang.reflect.Method)

    private var resolved = false
    private var api: SpriteApi? = null

    private fun api(): SpriteApi? {
        if (!resolved) {
            resolved = true
            api = runCatching {
                val pipelineField = Class.forName("net.minecraft.client.renderer.RenderPipelines").getField("GUI_TEXTURED")
                val pipeline = pipelineField.get(null)
                val pipelineClass = pipelineField.type
                val int = Int::class.javaPrimitiveType!!
                val extractor = GuiGraphicsExtractor::class.java
                val blit = extractor.getMethod(
                    "blitSprite", pipelineClass, Identifier::class.java, int, int, int, int,
                )
                val blitCrop = extractor.getMethod(
                    "blitSprite", pipelineClass, Identifier::class.java,
                    int, int, int, int, int, int, int, int,
                )
                SpriteApi(pipeline, blit, blitCrop)
            }.onFailure {
                logger.warn("Vanilla HUD sprites unavailable ({}), stat icons disabled", it.message)
            }.getOrNull()
        }
        return api
    }

    /** Draws sprite [path] (e.g. "hud/heart/full") at (x, y) scaled to w*h. */
    fun sprite(graphics: GuiGraphicsExtractor, path: String, x: Int, y: Int, w: Int = ICON, h: Int = ICON) {
        val a = api() ?: return
        runCatching {
            a.blit.invoke(graphics, a.pipeline, Identifier.fromNamespaceAndPath("minecraft", path), x, y, w, h)
        }.onFailure {
            api = null
            logger.warn("Vanilla HUD sprite draw failed ({}), stat icons disabled", it.message)
        }
    }

    /**
     * Draws the left [w] pixels of sprite [path] (whose full size is texW*texH) at (x, y) -
     * the cropping form vanilla itself uses for the experience progress bar.
     */
    fun spriteCrop(
        graphics: GuiGraphicsExtractor,
        path: String,
        texW: Int,
        texH: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val a = api() ?: return
        runCatching {
            a.blitCrop.invoke(
                graphics, a.pipeline, Identifier.fromNamespaceAndPath("minecraft", path),
                texW, texH, 0, 0, x, y, w, h,
            )
        }.onFailure {
            api = null
            logger.warn("Vanilla HUD sprite draw failed ({}), stat icons disabled", it.message)
        }
    }
}
