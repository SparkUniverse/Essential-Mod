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
package gg.essential.gui.common

import gg.essential.elementa.effects.Effect
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillMcScale
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.utils.withAlpha
import java.awt.Color

/**
 * Modifies the alpha value of an entire component tree as a whole (e.g. render the entire hierarchy to a framebuffer
 * first, then render the framebuffer texture with this alpha value).
 * There is no easy way to do that in OpenGL (without actually using a framebuffer, and that requires that the alpha
 * values stored in there are properly maintained, which isn't currently the case with GradientComponent) so this
 * implementation cheats by knowing the background color and then simply drawing it on top. The result is the same (so
 * long as there is a uniform background color).
 */
class FadeEffect(val backgroundColor: State<Color>, val alpha: Float) : Effect() {

    constructor(backgroundColor: Color, alpha: Float) : this(BasicState(backgroundColor), alpha)

    override fun extractAfter(extractor: ElementaExtractor) {
        extractor.fillMcScale(
            boundComponent.getLeft(),
            boundComponent.getTop(),
            boundComponent.getRight(),
            boundComponent.getBottom(),
            backgroundColor.get().withAlpha(1f - alpha),
        )
    }
}
