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
package gg.essential.gui.common.shadow

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.effects.Effect
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.LoadingIcon
import gg.essential.gui.common.SequenceAnimatedUIImage
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import java.awt.Color

class ShadowEffect(private val shadowColorState: State<Color>) : Effect() {
    constructor(shadowColor: Color = EssentialPalette.COMPONENT_BACKGROUND) :
            this(stateOf(shadowColor))

    override fun beforeDraw(matrixStack: UMatrixStack) {
        val shadowColor = shadowColorState.getUntracked()
        when (val boundComponent = boundComponent) {
            is EssentialUIText -> {

                // Copied from UIText
                val text = boundComponent.getText()

                val constraints = boundComponent.constraints
                val scale = constraints.getTextScale()
                val fontProvider = constraints.fontProvider
                val x = boundComponent.getLeft()
                val y = boundComponent.getTop() + (if (constraints.y is CenterConstraint) fontProvider.getBelowLineHeight() * scale else 0f)

                UGraphics.enableBlend()

                fontProvider.drawString(
                    matrixStack,
                    text, shadowColor, x + 1, y + 1,
                    10f, scale, false
                )
            }
            is SequenceAnimatedUIImage -> {
                val child = boundComponent.currentFrameComponent ?: return
                child.drawImage(
                    matrixStack,
                    boundComponent.getLeft() + 1.0,
                    boundComponent.getTop() + 1.0,
                    boundComponent.getWidth().toDouble(),
                    boundComponent.getHeight().toDouble(),
                    shadowColor
                )
            }
            is UIBlock, is UIContainer -> {
                val x = boundComponent.getLeft().toDouble()
                val y = boundComponent.getTop().toDouble()
                val x2 = boundComponent.getRight().toDouble()
                val y2 = boundComponent.getBottom().toDouble()

                UIBlock.drawBlock(matrixStack, shadowColor, x+1, y+1, x2+1, y2+1)
            }
            is UIImage -> {
                boundComponent.drawImage(
                    matrixStack,
                    boundComponent.getLeft() + 1.0,
                    boundComponent.getTop() + 1.0,
                    boundComponent.getWidth().toDouble(),
                    boundComponent.getHeight().toDouble(),
                    shadowColor
                )
            }
            is LoadingIcon -> {
                val xCenter = (boundComponent.getLeft() + boundComponent.getRight()) / 2
                val yCenter = (boundComponent.getTop() + boundComponent.getBottom()) / 2

                LoadingIcon.draw(
                    matrixStack,
                    xCenter + boundComponent.scale.toInt(),
                    yCenter + boundComponent.scale.toInt(),
                    boundComponent.scale,
                    boundComponent.time,
                    shadowColor
                )
            }
            else -> {
                throw UnsupportedOperationException("Shadow effect cannot be applied to ${getDebugInfo()}")
            }
        }
    }

    private fun getDebugInfo(): String {
        return boundComponent.componentName + " " + boundComponent.javaClass.name
    }
}