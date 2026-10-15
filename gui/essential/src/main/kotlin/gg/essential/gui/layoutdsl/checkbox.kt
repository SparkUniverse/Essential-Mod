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
package gg.essential.gui.layoutdsl

import gg.essential.elementa.components.inspector.Inspector
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.Checkbox
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.universal.USound
import gg.essential.util.onLeftClick

fun LayoutScope.checkbox(
    initialValue: Boolean,
    onValueChange: (Boolean) -> Unit
): Checkbox {
    return Checkbox(initialValue)().apply {
        isChecked.onChange(this) {
            onValueChange(it)
        }
    }
}

fun LayoutScope.checkbox(
    initialValue: Boolean,
): Checkbox {
    return Checkbox(initialValue)()
}

// Alternative checkbox style used in modern modals
fun LayoutScope.checkboxAlt(selected: MutableState<Boolean>, modifier: Modifier = Modifier, disabled: State<Boolean> = stateOf(false), outline: Boolean = true) {
    val selectedModifier = Modifier.color(EssentialPalette.CHECKBOX_SELECTED_BACKGROUND)
        .whenTrue(disabled, Modifier.color(EssentialPalette.CHECKBOX_SELECTED_BACKGROUND.darker()), Modifier.hoverColor(EssentialPalette.CHECKBOX_SELECTED_BACKGROUND_HOVER))
    val outlineColorModifier = Modifier.whenTrue(selected, selectedModifier, Modifier.color(EssentialPalette.CHECKBOX_OUTLINE)
        .whenTrue(disabled, Modifier.color(EssentialPalette.CHECKBOX_OUTLINE.darker())))
    val innerColorModifier = Modifier.whenTrue(selected, selectedModifier, Modifier.color(EssentialPalette.CHECKBOX_BACKGROUND)
        .whenTrue(disabled, Modifier.color(EssentialPalette.CHECKBOX_BACKGROUND.darker()), Modifier.hoverColor(EssentialPalette.CHECKBOX_BACKGROUND_HOVER)))
    val outerColorModifier = if (outline) outlineColorModifier else innerColorModifier

    box(Modifier.width(9f).heightAspect(1f).hoverScope() then outerColorModifier then modifier) {
        box(Modifier.width(7f).heightAspect(1f) then innerColorModifier) {
            if_(selected) {
                image(EssentialPalette.CHECKMARK_7X5, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT).whenTrue(disabled, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT.darker())))
            }
        }
    }.onLeftClick {
        it.stopPropagation()
        if (!disabled.getUntracked()) {
            USound.playButtonPress()
            selected.set { !it }
        }
    }
}

@Suppress("unused")
private val init = run {
    Inspector.registerComponentFactory(null)
}
