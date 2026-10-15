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

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CopyConstraintFloat
import gg.essential.elementa.constraints.PixelConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.renderer.ElementaExtractor

/**
 * A wrapper used to add an effect using a fake component
 * Extend this class and edit the constraints of the dummy component
 */
abstract class EffectWithFakeComponent(private val effect: Effect) : Effect() {

    protected val dummyComponent = UIContainer()
    protected var initialised = false

    open fun preFirstDraw() {
        dummyComponent.parent = boundComponent
        dummyComponent.effect(effect)
        initialised = true
    }

    override fun setup() {
        effect.setup()
    }

    @Deprecated("See [ElementaVersion.V8].")
    @Suppress("DEPRECATION")
    override fun animationFrame() {
        dummyComponent.animationFrame()
    }

    override fun extractBefore(extractor: ElementaExtractor) {
        if (!initialised) {
            this.preFirstDraw()
        }
        effect.extractBefore(extractor)
    }

    override fun extractAfter(extractor: ElementaExtractor) {
        effect.extractAfter(extractor)
    }

    override fun extractBeforeChildren(extractor: ElementaExtractor) {
        effect.extractBeforeChildren(extractor)
    }
}

/**
 * A scissor effect that cuts out horizontally across the whole window
 */
class HorizontalScissorEffect(
    private val topMargin: PixelConstraint = 0.pixels,
    private val bottomMargin: PixelConstraint = 0.pixels
) : EffectWithFakeComponent(ScissorEffect()) {

    override fun preFirstDraw() {
        dummyComponent.constrain {
            x = 0.percentOfWindow
            y = (CopyConstraintFloat() boundTo boundComponent) - topMargin
            width = 100.percentOfWindow
            height = (CopyConstraintFloat() boundTo boundComponent) + bottomMargin
        }
        super.preFirstDraw()
    }

}