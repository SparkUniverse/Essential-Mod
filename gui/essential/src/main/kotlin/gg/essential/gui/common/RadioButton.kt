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

import gg.essential.elementa.UIComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillMcScale
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.combinators.bimap
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.width
import gg.essential.universal.USound
import java.awt.Color

class RadioButton(
    private val state: MutableState<Boolean>,
    private val selectedColor: Color = EssentialPalette.COMPONENT_SELECTED_OUTLINE
) : UIComponent() {

    init {
        Modifier.color(EssentialPalette.TEXT).width(7f).height(7f).hoverScope().onLeftClick { click ->
            if (!state.getUntracked()) {
                USound.playButtonPress()
                state.set(true)
            }
            click.stopPropagation()
        }.applyToComponent(this)
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        val x = getLeft()
        val y = getTop()

        extractInner(extractor, EssentialPalette.BLACK, x + 1, y + 1)

        extractInner(extractor, if (state.getUntracked()) selectedColor else getColor(), x, y)
    }

    private fun extractInner(extractor: ElementaExtractor, color: Color, x: Float, y: Float) {
        extractor.fillMcScale(x, y + 2, x + 1, y + 5, color)
        extractor.fillMcScale(x + 1, y + 1, x + 2, y + 2, color)
        extractor.fillMcScale(x + 1, y + 5, x + 2, y + 6, color)
        extractor.fillMcScale(x + 2, y, x + 5, y + 1, color)
        extractor.fillMcScale(x + 2, y + 6, x + 5, y + 7, color)
        extractor.fillMcScale(x + 5, y + 1, x + 6, y + 2, color)
        extractor.fillMcScale(x + 5, y + 5, x + 6, y + 6, color)
        extractor.fillMcScale(x + 6, y + 2, x + 7, y + 5, color)
        if (state.getUntracked()) {
            extractor.fillMcScale(x + 2, y + 2, x + 5, y + 5, color)
        }
    }
}

fun <T> LayoutScope.radioButton(
    value: T,
    group: MutableState<T>,
    modifier: Modifier = Modifier,
    selectedColor: Color = EssentialPalette.COMPONENT_SELECTED_OUTLINE
) {
    radioButton(group.bimap({ it == value }, { value }), modifier, selectedColor)
}

fun LayoutScope.radioButton(
    state: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    selectedColor: Color = EssentialPalette.COMPONENT_SELECTED_OUTLINE,
) {
    RadioButton(state, selectedColor = selectedColor)(modifier)
}