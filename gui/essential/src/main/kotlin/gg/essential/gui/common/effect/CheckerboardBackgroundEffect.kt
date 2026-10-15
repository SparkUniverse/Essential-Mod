/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.gui.common.effect

import gg.essential.elementa.effects.Effect
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.gui.EssentialPalette
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UVertexConsumer
import java.awt.Color
import kotlin.math.roundToInt

class CheckerboardBackgroundEffect : Effect() {
    override fun extractBefore(extractor: ElementaExtractor) {
        val component = boundComponent
        val scale = extractor.guiScale
        val unit = scale.roundToInt()
        val left = (component.getLeft().toDouble() * scale).roundToInt()
        val top = (component.getTop().toDouble() * scale).roundToInt()
        val right = (component.getRight().toDouble() * scale).roundToInt()
        val bottom = (component.getBottom().toDouble() * scale).roundToInt()
        val quads = (1 + (right - left) / unit) * (1 + (bottom - top) / unit)
        extractor.custom(left, top, right, bottom, PIPELINE, emptyList(), quads * 4) { builder, _, _ ->
            drawCheckerboard(builder, left, top, right, bottom, unit)
        }
    }

    private fun drawCheckerboard(builder: UVertexConsumer, left: Int, top: Int, right: Int, bottom: Int, unit: Int) {
        for (x in 0 until (right - left) / unit) {
            for (y in 0 until (bottom - top) / unit) {
                val color = if ((x + y) % 2 == 0) Color.LIGHT_GRAY else EssentialPalette.TEXT_HIGHLIGHT
                val x2 = x + 1
                val y2 = y + 1
                drawVertex(builder, left + x  * unit, top + y  * unit, color)
                drawVertex(builder, left + x  * unit, top + y2 * unit, color)
                drawVertex(builder, left + x2 * unit, top + y2 * unit, color)
                drawVertex(builder, left + x2 * unit, top + y  * unit, color)
            }
        }
    }
    private fun drawVertex(builder: UVertexConsumer, x: Int, y: Int, color: Color) {
        builder
            .pos(UMatrixStack.UNIT, x.toDouble(), y.toDouble(), 0.0)
            .color(
                color.red.toFloat() / 255f,
                color.green.toFloat() / 255f,
                color.blue.toFloat() / 255f,
                color.alpha.toFloat() / 255f
            )
            .endVertex()
    }

    companion object {
        private val PIPELINE = URenderPipeline.builderWithDefaultShader(
            "essential:checkerboard_background_effect",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
        ).build()
    }
}
