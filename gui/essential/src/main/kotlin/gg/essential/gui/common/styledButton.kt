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
import gg.essential.elementa.utils.withAlpha
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.toV2
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedWidth
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.util.hoverScope
import gg.essential.vigilance.utils.onLeftClick

/** Used by a [Modifier] in a [styledButton] block in order to style text. */
fun Modifier.textStyle(style: State<MenuButton.Style>): Modifier {
    return this then Modifier.color(style.map { it.textColor }).shadow(style.map { it.textShadow })
}

/**
 * An immutable "styled" button.
 * To build one of these, see [styledButton].
 */
class StyledButton(
    /** The style for this button. */
    private val style: State<Style>,

    /** Whether this button should be re-textured or not. */
    private val enableRetexturing: State<Boolean>,

    /** Whether this button is disabled or not. When true, click events will not be propagated. */
    private val disabled: State<Boolean>,

    private val content: LayoutScope.(style: State<MenuButton.Style>) -> Unit,
) : UIComponent() {
    private val hovered = hoverScope().toV2()

    private val currentStyle = memo {
        val style = style()
        when {
            disabled() -> style.disabledStyle
            hovered() -> style.hoveredStyle
            else -> style.defaultStyle
        }
    }

    init {
        layoutAsBox(Modifier.childBasedWidth(5f).childBasedHeight(6f).hoverScope()) {
            content(currentStyle)
        }

        onLeftClick { event ->
            if (disabled.getUntracked()) {
                event.stopImmediatePropagation()
            }
        }
    }

    /** Copied from [MenuButton.extractComponent]. */
    override fun extractComponent(extractor: ElementaExtractor) {
        val style = currentStyle.getUntracked()
        val hovered = hovered.getUntracked()

        if (style.buttonColor.alpha != 0) {
            if (enableRetexturing.getUntracked()) {
                val (type, texture) = MenuButton.ButtonTextures.currentTexture(hovered)

                if (texture == null) {
                    extractDefaultButton(extractor, style)
                } else {
                    // If the button is one of these states, we don't want to tint it unless the user has darkening
                    // enabled, which is handled in `drawTexturedButton`.
                    // - DARK_GRAY is our default button state.
                    // - GRAY is our default hover state.
                    val isDefaultOrHoveredBaseColor =
                        style.buttonColor == (if (hovered) MenuButton.GRAY else MenuButton.DARK_GRAY).buttonColor

                    MenuButton.extractTexturedButton(
                        extractor,
                        getLeft().toDouble(),
                        getTop().toDouble(),
                        getRight().toDouble(),
                        getBottom().toDouble(),
                        style.buttonColor,
                        isDefaultOrHoveredBaseColor,
                        type == MenuButton.ButtonTextures.Type.Essential,
                        texture
                    )
                }
            } else {
                extractDefaultButton(extractor, style)
            }
        }
    }

    private fun extractDefaultButton(extractor: ElementaExtractor, style: MenuButton.Style) {
        MenuButton.extractButton(
            extractor,
            getLeft().toDouble() + 1.0,
            getTop().toDouble() + 1.0,
            getRight().toDouble() - 1.0,
            getBottom().toDouble() - 1.0,
            style.buttonColor,
            style.highlightColor,
            style.buttonColor.darker().darker().withAlpha(0.5f),
            style.outlineColor,
            hasTop = true,
            hasBottom = true,
            hasLeft = true,
            hasRight = true
        )
    }

    data class Style(
        val defaultStyle: MenuButton.Style,
        val hoveredStyle: MenuButton.Style,
        val disabledStyle: MenuButton.Style,
    ) {
        companion object {
            val GRAY = Style(MenuButton.DARK_GRAY, MenuButton.GRAY, MenuButton.GRAY_DISABLED)
            val BLUE = Style(MenuButton.BLUE, MenuButton.LIGHT_BLUE, MenuButton.BLUE_DISABLED)
            val RED = Style(MenuButton.RED, MenuButton.LIGHT_RED, MenuButton.RED_DISABLED)
            val GREEN = Style(MenuButton.GREEN, MenuButton.LIGHT_GREEN, MenuButton.GREEN_DISABLED)

        }
    }
}