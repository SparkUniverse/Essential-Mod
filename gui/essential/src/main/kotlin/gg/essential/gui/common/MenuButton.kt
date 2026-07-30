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
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.state.toConstraint
import gg.essential.elementa.utils.getStringSplitToWidthTruncated
import gg.essential.elementa.utils.withAlpha
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.MenuButton.Alignment
import gg.essential.gui.common.MenuButton.Alignment.*
import gg.essential.gui.common.MenuButton.Companion.DARK_GRAY
import gg.essential.gui.common.MenuButton.Companion.GRAY
import gg.essential.gui.common.MenuButton.Style
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.common.shadow.ShadowIcon
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.zip
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.image.ImageFactory
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.childBasedSize
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.util.Tag
import gg.essential.gui.util.getTag
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.USound
import gg.essential.universal.shader.BlendState
import gg.essential.util.ButtonTextureProvider
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.UIdentifier
import gg.essential.gui.util.hoveredState
import gg.essential.util.image.bitmap.Bitmap
import gg.essential.util.image.bitmap.bitmapState
import gg.essential.util.image.bitmap.bitmapStateIf
import gg.essential.util.image.bitmap.cropped
import gg.essential.gui.util.pollingState
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.util.NEAREST
import gg.essential.vigilance.utils.onLeftClick
import java.awt.Color

/**
 * A styled button for use in various menus.
 *
 * @constructor                 Creates a collapsable styled button containing the specified text and optional icon.
 * @param buttonText            The button's text as a [String] [] [State].
 * @param defaultStyle          The button's normal (non-hovered) [Style] [] [State]. Defaults to [DARK_GRAY].
 * @param hoverStyle            The button's [Style] [] [State] when hovered. Defaults to [GRAY].
 * @param textAlignment         The [Alignment] of the button's text. Defaults to [Alignment.CENTER].
 * @param textXOffset           The x offset to apply to the button's text.
 * @param collapsedText         The optional text as a [String] [] [State] to display when the button is collapsed.
 * @param truncate              Whether the text should be truncated to fit within the button. Defaults to False.
 * @param clickSound            Whether the button should play a click sound when pressed. Defaults to True.
 * @param shouldBeRetextured    Whether the button should be retextured. Defaults to null, this means it will only be retextured if it's on the main menu.
 * @param action                The optional callback to invoke when the button is pressed.
 */
class MenuButton @JvmOverloads constructor(
    private val buttonText: State<String>,
    defaultStyle: State<Style> = BasicState(DARK_GRAY),
    hoverStyle: State<Style> = BasicState(GRAY),
    disabledStyle: State<Style> = defaultStyle.map { it.copy(textColor = EssentialPalette.TEXT_DISABLED) },
    private val textAlignment: Alignment = CENTER,
    textXOffset: State<Float> = BasicState(0f),
    private val collapsedText: State<String?> = BasicState(null),
    private val truncate: Boolean = false,
    private val clickSound: Boolean = true,
    private var shouldBeRetextured: Boolean? = null,
    private val action: () -> Unit = {},
) : UIComponent() {

    val hoveredStyleOverrides = BasicState(false) // For manually activating hovered style
    private val tooltipHover = hoveredState()
    val styleHover = hoveredState() or hoveredStyleOverrides
    private val collapsed = BasicState(false).map { it }
    private val enabledState = BasicState(true).map { it }
    private var collapsedWidth = 0f
    private var collapsedHeight = 0f

    private val defaultStyle = defaultStyle.map { it }
    private val hoverStyle = hoverStyle.map { it }
    private val disabledStyle = disabledStyle.map { it }
    private val textXOffset = textXOffset.map { it }

    private val textState = collapsed.zip(buttonText.zip(collapsedText)).map { (collapsed, texts) ->
        val (regularText, collapsedText) = texts
        if (collapsed) collapsedText else regularText
    }
    private val availableLabelWidth = pollingState(91f) {
        getWidth() - (icon?.getWidth()?.plus(12f) ?: 9f)
    }
    private val labelState = textState.zip(availableLabelWidth).map { (text, width) ->
        truncateLabel(text ?: "", width)
    }
    val isTruncated = textState.zip(labelState).map { (text, label) -> text != label }

    val styleState =
        styleHover.zip(enabledState).zip(this.defaultStyle.zip(this.hoverStyle.zip(this.disabledStyle))).map { (hoveredEnabled, styles) ->
            val (hovered, enabled) = hoveredEnabled
            val (standardStyle, hoveredDisableStyles) = styles
            val (hoveredStyle, disabledStyleState) = hoveredDisableStyles

            if (enabled) {
                if (hovered) hoveredStyle else standardStyle
            } else {
                disabledStyleState
            }
        }

    private val iconVisible = BasicState(false).map { it }

    private var icon: UIComponent? = null
    private var tooltip: Tooltip? = null
    private var originalWidth = constraints.width
    private var originalHeight = constraints.height

    private val hasLeft = styleState.map { OutlineEffect.Side.Left in it.sides }
    private val hasRight = styleState.map { OutlineEffect.Side.Right in it.sides }
    private val hasTop = styleState.map { OutlineEffect.Side.Top in it.sides }
    private val hasBottom = styleState.map { OutlineEffect.Side.Bottom in it.sides }

    // For accessing enabled state value
    val enabled by ReadOnlyState(enabledState)

    var drawsBackground = mutableStateOf(stateOf(true))

    @JvmOverloads
    constructor(
        buttonText: String = "",
        defaultStyle: State<Style> = BasicState(DARK_GRAY),
        hoverStyle: State<Style> = BasicState(GRAY),
        disabledStyle: State<Style> = defaultStyle.map { it.copy(textColor = EssentialPalette.TEXT_DISABLED) },
        textAlignment: Alignment = CENTER,
        textXOffset: State<Float> = BasicState(0f),
        collapsedText: State<String?> = BasicState(null),
        truncate: Boolean = true,
        clickSound: Boolean = true,
        shouldBeRetextured: Boolean? = null,
        action: () -> Unit = {},
    ) : this(
        BasicState(buttonText),
        defaultStyle,
        hoverStyle,
        disabledStyle,
        textAlignment,
        textXOffset,
        collapsedText,
        truncate,
        clickSound,
        shouldBeRetextured,
        action,
    )

    private val label by EssentialUIText().bindText(labelState).bindShadowColor(styleState.map { it.textShadow }).constrain {
        y = CenterConstraint() - 0.pixels // Otherwise the text will add 1 pixel to the height
        color = styleState.map { it.textColor }.toConstraint()
    }.bindConstraints(collapsed.zip(iconVisible.zip(this.textXOffset))) { (collapsed, iconVisibleAndOffset) ->
        val (iconVisible, textOffset) = iconVisibleAndOffset
        x = if (collapsed && !iconVisible) CenterConstraint() else textAlignment.constraint() + textOffset.pixels
    }.bindParent(this, labelState.map { it.isNotEmpty() }, index = 0)

    init {
        constrain {
            width = ChildBasedSizeConstraint() + 10.pixels
            height = ChildBasedMaxSizeConstraint() + 12.pixels
        }

        onLeftClick {
            runAction()
        }

        collapsed.onSetValue {
            if (it) {
                // Store width and height constraints for expand
                originalWidth = constraints.width
                originalHeight = constraints.height

                if (collapsedWidth > 0f) {
                    setWidth(collapsedWidth.pixels)
                } else {
                    setWidth(AspectConstraint().coerceAtLeast(ChildBasedSizeConstraint() + 10.pixels))
                }

                if (collapsedHeight > 0f) {
                    setHeight(collapsedHeight.pixels)
                }
            } else {
                setWidth(originalWidth)
                setHeight(originalHeight)
            }
        }
    }

    /** Invokes the button's [action] if the button is enabled */
    fun runAction() {
        if (enabledState.get()) {
            if (clickSound) {
                USound.playButtonPress()
            }
            action.invoke()
        }
    }

    /** Binds the button's collapsed state to [state] with an optional [width] and [height] */
    fun bindCollapsed(state: State<Boolean>, width: Float = 0f, height: Float = 0f) = apply {
        collapsedWidth = width
        collapsedHeight = height
        collapsed.rebind(state)
    }

    /** Rebind the button's enabled state to [enabled] */
    fun rebindEnabled(enabled: State<Boolean>) {
        enabledState.rebind(enabled)
    }

    /**
     * Sets the icon that is visible on the button.
     *
     * @param icon          The icon as an [ImageFactory] [] [State].
     * @param rightAligned  True if the icon should be aligned to the right of the button, false otherwise.
     * @param color         The optional [Color] [] [State] of the icon. Defaults to [EssentialPalette.TEXT_HIGHLIGHT].
     * @param iconWidth     The optional width of the icon. Defaults to the icon's actual width.
     * @param iconHeight    The optional height of the icon. Defaults to the icon's actual height.
     * @param xOffset       The additional offset to apply to the X constraint.
     * @param yOffset       The additional offset to apply to the Y constraint.
     * @param visibleState  A [Boolean] [] [State] that controls the visibility of the icon.
     */
    fun setIcon(
        icon: State<ImageFactory>,
        rightAligned: Boolean = false,
        color: State<Color> = BasicState(EssentialPalette.TEXT_HIGHLIGHT),
        iconWidth: Float? = null,
        iconHeight: Float? = null,
        xOffset: Float = 0f,
        yOffset: Float = 0f,
        visibleState: State<Boolean> = BasicState(true),
    ) = apply {
        this.icon = ShadowIcon(icon, BasicState(true), color, styleState.map { it.textShadow }).constrain {
            iconWidth?.let { width = it.pixels }
            iconHeight?.let { height = it.pixels }
        }.bindConstraints(labelState) {
            x = if (it.isNotEmpty()) 5.pixels(alignOpposite = rightAligned) + xOffset.pixels else CenterConstraint()
            y = if (it.isNotEmpty()) (1.pixels(alignOpposite = true) boundTo label) + yOffset.pixels else CenterConstraint()
        }.bindParent(this, visibleState, index = if (rightAligned) 1 else 0)

        iconVisible.rebind(visibleState)
    }

    /**
     * Sets the icon that is visible on the button.
     *
     * @param iconLayout    The icon layout.
     * @param rightAligned  True if the icon should be aligned to the right of the button, false otherwise.
     * @param xOffset       The additional offset to apply to the X constraint.
     * @param yOffset       The additional offset to apply to the Y constraint.
     */
    fun setIcon(
        iconLayout: LayoutScope.() -> Unit,
        rightAligned: Boolean = false,
        xOffset: Float = 0f,
        yOffset: Float = 0f,
    ) = apply {
        this@MenuButton.icon = UIContainer().layoutAsBox(Modifier.childBasedSize()) {
            iconLayout.invoke(this)
        }.bindConstraints(labelState) {
            x = if (it.isNotEmpty()) 5.pixels(alignOpposite = rightAligned) + xOffset.pixels else CenterConstraint()
            y = if (it.isNotEmpty()) (1.pixels(alignOpposite = true) boundTo label) + yOffset.pixels else CenterConstraint()
        }.bindParent(this@MenuButton, true.state(), index = if (rightAligned) 1 else 0)
    }

    /**
     * Sets the tooltip that is visible when hovering over the button.
     *
     * @param tooltipText   The tooltip text as a [String] [] [State]
     * @param above         True to render the tooltip above the button, False to render below. Defaults to True.
     * @param visibleState  The optional [Boolean] [] [State] that determines whether the tooltip is shown.
     * @param xAlignment    The optional horizontal [Alignment] of the tooltip relative to the button. Defaults to [Alignment.CENTER]
     * @param followCursorX Whether the tooltip should move with the cursor horizontally. Defaults to False.
     * @param followCursorY Whether the tooltip should move with the cursor vertically. Defaults to False.
     * @param xOffset       The optional horizontal offset of the tooltip relative to the button. Defaults to zero offset.
     * @param yOffset       The optional vertical offset of the tooltip relative to the button. Defaults to zero offset.
     * @param notchSize     The optional size of the tooltip notch. A size of zero disables the notch.
     */
    fun setTooltip(
        tooltipText: State<String>,
        above: Boolean = true,
        visibleState: State<Boolean> = BasicState(true),
        xAlignment: Alignment = CENTER,
        followCursorX: Boolean = false,
        followCursorY: Boolean = false,
        xOffset: Float = 0f,
        yOffset: Float = 1f,
        notchSize: Int = 3,
    ) = apply {
        tooltip = EssentialTooltip(
            this,
            position = if (above) EssentialTooltip.Position.ABOVE else EssentialTooltip.Position.BELOW,
            notchSize = notchSize
        ).constrain {
            x = (if (followCursorX) {
                MousePositionConstraint()
            } else {
                when (xAlignment) {
                    LEFT, LEFT_SMALL_PADDING -> 0.pixels
                    CENTER -> CenterConstraint()
                    RIGHT -> 0.pixels(alignOpposite = true)
                } boundTo this@MenuButton
            }) + xOffset.pixels
            y = (if (followCursorY) {
                MousePositionConstraint()
            } else {
                SiblingConstraint(4f, alignOpposite = above) boundTo this@MenuButton
            }) + (if (above) -yOffset else yOffset).pixels
        }
            .bindLine(tooltipText)
            .bindVisibility(tooltipHover and !tooltipText.empty() and visibleState)
    }

    /**
     * Sets the tooltip that is visible when hovering over the [MenuButton], containing [tooltipText], and whether it should
     * render [above] the button.
     *
     * @see setTooltip
     */
    fun setTooltip(tooltipText: String, above: Boolean = true) = setTooltip(BasicState(tooltipText), above)

    /**
     * Shrinks the button to an optionally specified [width] and [height].
     *
     * If no [width] is specified, the button will shrink to match either its height or its content, whichever is larger.
     *
     * If no [height] is specified, the height of the button will not change when collapsed.
     *
     * If no collapsed text was specified when constructing the button, any text will be hidden and any specified icon will be centered.
     * Otherwise, the collapsed button will display the collapsed text.
     */
    fun collapse(width: Float = 0f, height: Float = 0f) {
        if (!collapsed.get()) {
            collapsedWidth = width
            collapsedHeight = height
            collapsed.set(true)
        }
    }

    /**
     * Expands the button to its original size and text prior to it being collapsed.
     */
    fun expand() {
        if (collapsed.get()) {
            collapsed.set(false)
        }
    }

    /**
     * Truncate [text] to fit within [width] with suffix "...".
     */
    private fun truncateLabel(text: String, width: Float): String {
        if (truncate && text.isNotEmpty() && text.length > 1) {
            if (text.width() > width) {
                val truncated = getStringSplitToWidthTruncated(text, width, 1f, 1)
                if (truncated.isNotEmpty()) {
                    return truncated[0]
                }
            }
        }
        return text
    }

    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val style = styleState.get()
        if (drawsBackground.getUntracked().getUntracked() && style.buttonColor.alpha != 0) {
            if (shouldBeRetextured ?: (Window.of(this).getTag<WindowSupportsButtonRetexturingMarker>() != null)) {
                val hovered = styleHover.get()
                val (type, texture) = ButtonTextures.currentTexture(hovered)

                if (texture == null) {
                    drawDefaultButton(matrixStack, style)
                } else {
                    // If the button is one of these states, we don't want to tint it unless the user has darkening
                    // enabled, which is handled in `drawTexturedButton`.
                    // - DARK_GRAY is our default button state.
                    // - GRAY is our default hover state.
                    // This check is mirrored in [MenuButtonProxy.requiresTinting()], be sure to replicate changes there
                    val isDefaultOrHoveredBaseColor = style.buttonColor == (if (hovered) GRAY else DARK_GRAY).buttonColor

                    drawTexturedButton(
                        matrixStack,
                        getLeft().toDouble(),
                        getTop().toDouble(),
                        getRight().toDouble(),
                        getBottom().toDouble(),
                        style.buttonColor,
                        isDefaultOrHoveredBaseColor,
                        type == ButtonTextures.Type.Essential,
                        texture
                    )
                }
            } else {
                drawDefaultButton(matrixStack, style)
            }
        }

        super.draw(matrixStack)
    }

    private fun drawDefaultButton(matrixStack: UMatrixStack, style: Style) {
        drawButton(
            matrixStack,
            getLeft().toDouble() + 1.0,
            getTop().toDouble() + 1.0,
            getRight().toDouble() - 1.0,
            getBottom().toDouble() - 1.0,
            style.buttonColor,
            style.highlightColor,
            style.buttonColor.darker().darker().withAlpha(0.5f),
            style.outlineColor,
            hasTop = hasTop.get(),
            hasBottom = hasBottom.get(),
            hasLeft = hasLeft.get(),
            hasRight = hasRight.get(),
        )
    }

    object WindowSupportsButtonRetexturingMarker : Tag

    companion object {
        private val PIPELINE = URenderPipeline.builderWithDefaultShader(
            "essential:menu_button",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always
        }.build()

        private val PIPELINE_TEXTURED = URenderPipeline.builderWithDefaultShader(
            "essential:menu_button_textured",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
            depthTest = URenderPipeline.DepthTest.Always
        }.build()

        fun drawButton(
            matrixStack: UMatrixStack,
            left: Double,
            top: Double,
            right: Double,
            bottom: Double,
            baseColor: Color,
            highlightColor: Color,
            shadowColor: Color,
            outlineColor: Color,
            hasTop: Boolean,
            hasBottom: Boolean,
            hasLeft: Boolean,
            hasRight: Boolean,
        ) {
            UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR).apply {
                // Base
                pos(matrixStack, left, top, 0.0).color(baseColor).endVertex()
                pos(matrixStack, left, bottom, 0.0).color(baseColor).endVertex()
                pos(matrixStack, right, bottom, 0.0).color(baseColor).endVertex()
                pos(matrixStack, right, top, 0.0).color(baseColor).endVertex()

                // Highlight left
                pos(matrixStack, left, top, 0.0).color(highlightColor).endVertex()
                pos(matrixStack, left, bottom, 0.0).color(highlightColor).endVertex()
                pos(matrixStack, left + 1.0, bottom, 0.0).color(highlightColor).endVertex()
                pos(matrixStack, left + 1.0, top, 0.0).color(highlightColor).endVertex()
                // Highlight top
                pos(matrixStack, left + 1.0, top, 0.0).color(highlightColor).endVertex()
                pos(matrixStack, left + 1.0, top + 1.0, 0.0).color(highlightColor).endVertex()
                pos(matrixStack, right, top + 1.0, 0.0).color(highlightColor).endVertex()
                pos(matrixStack, right, top, 0.0).color(highlightColor).endVertex()

                // Shadow right
                pos(matrixStack, right, bottom, 0.0).color(shadowColor).endVertex()
                pos(matrixStack, right, top, 0.0).color(shadowColor).endVertex()
                pos(matrixStack, right - 1.0, top, 0.0).color(shadowColor).endVertex()
                pos(matrixStack, right - 1.0, bottom, 0.0).color(shadowColor).endVertex()
                // Shadow bottom
                pos(matrixStack, right - 1.0, bottom, 0.0).color(shadowColor).endVertex()
                pos(matrixStack, right - 1.0, bottom - 2.0, 0.0).color(shadowColor).endVertex()
                pos(matrixStack, left, bottom - 2.0, 0.0).color(shadowColor).endVertex()
                pos(matrixStack, left, bottom, 0.0).color(shadowColor).endVertex()

                // Outline
                drawOutline(matrixStack, left, top, right, bottom, outlineColor, hasTop, hasBottom, hasLeft, hasRight)
            }.build()?.drawAndClose(PIPELINE)
        }

        fun drawTexturedButton(
            matrixStack: UMatrixStack,
            left: Double,
            top: Double,
            right: Double,
            bottom: Double,
            baseColor: Color,
            isDefaultOrHoveredBaseColor: Boolean,
            isEssentialButtonTexture: Boolean,
            image: Bitmap,
        ) {
            val alwaysTint = if (isEssentialButtonTexture) false else platform.config.shouldDarkenRetexturedButtons
            val shouldTint = !isDefaultOrHoveredBaseColor || alwaysTint
            val texture = ButtonTextureProvider.provide(image, baseColor.takeIf { shouldTint })

            val textureView = texture.gpuTextureView
            UGraphics.color4f(1f, 1f, 1f, 1f)

            UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR).apply {
                val width = right - left
                val buttonMidPoint = left + (width / 2)
                val textureMidpoint = (width / 2) / 200

                fun drawHalf(first: Boolean) {
                    // WARNING: Awesome ascii art ahead
                    // On the second half, we want to get the last section of the texture instead of squishing
                    // all the way from the first section to the very end of the texture.
                    // ┌─────┬────────────────────────────────────────────────┬─────┐
                    // │     │                                                │     │
                    // │     │                                                │     │
                    // └─────┴────────────────────────────────────────────────┴─────┘
                    //       ^ We don't want to start mapping the second      ^ We want to start here
                    //         half here (scaledRectMidpoint)                   (1.0 - scaledRectMidpoint)
                    val textureLeft = if (first) 0.0 else 1.0 - textureMidpoint
                    val textureRight = if (first) textureMidpoint else 1.0
                    val textureTop = 0.0
                    val textureBottom = 1.0

                    val buttonLeft = if (first) left else buttonMidPoint
                    val buttonRight = if (first) buttonMidPoint else right

                    pos(matrixStack, buttonLeft, top, 0.0)
                        .tex(textureLeft, textureTop)
                        .color(1f, 1f, 1f, 1f)
                        .endVertex()

                    pos(matrixStack, buttonLeft, bottom, 0.0)
                        .tex(textureLeft, textureBottom)
                        .color(1f, 1f, 1f, 1f)
                        .endVertex()

                    pos(matrixStack, buttonRight, bottom, 0.0)
                        .tex(textureRight, textureBottom)
                        .color(1f, 1f, 1f, 1f)
                        .endVertex()

                    pos(matrixStack, buttonRight, top, 0.0)
                        .tex(textureRight, textureTop)
                        .color(1f, 1f, 1f, 1f)
                        .endVertex()
                }

                // We draw the texture in two halves, just like the vanilla button
                drawHalf(true)
                drawHalf(false)
            }.build()?.drawAndClose(PIPELINE_TEXTURED) {
                texture(0, textureView, UGpuSampler.NEAREST)
            }
        }

        private fun UBufferBuilder.drawOutline(
            matrixStack: UMatrixStack,
            left: Double,
            top: Double,
            right: Double,
            bottom: Double,
            outlineColor: Color,
            hasTop: Boolean,
            hasBottom: Boolean,
            hasLeft: Boolean,
            hasRight: Boolean,
        ) {
            if (hasTop) {
                pos(matrixStack, left - if (hasLeft) 1.0 else 0.0, top - 1.0, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, left - if (hasLeft) 1.0 else 0.0, top, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right + if (hasRight) 1.0 else 0.0, top, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right + if (hasRight) 1.0 else 0.0, top - 1.0, 0.0).color(outlineColor).endVertex()
            }
            if (hasBottom) {
                pos(matrixStack, left - if (hasLeft) 1.0 else 0.0, bottom, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, left - if (hasLeft) 1.0 else 0.0, bottom + 1.0, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right + if (hasRight) 1.0 else 0.0, bottom + 1.0, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right + if (hasRight) 1.0 else 0.0, bottom, 0.0).color(outlineColor).endVertex()
            }
            if (hasLeft) {
                pos(matrixStack, left - 1.0, top, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, left - 1.0, bottom, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, left, bottom, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, left, top, 0.0).color(outlineColor).endVertex()
            }
            if (hasRight) {
                pos(matrixStack, right, top, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right, bottom, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right + 1.0, bottom, 0.0).color(outlineColor).endVertex()
                pos(matrixStack, right + 1.0, top, 0.0).color(outlineColor).endVertex()
            }
        }

        // Pre-made styles for common buttons
        val GRAY = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(89, 89, 89), Color(255, 255, 255))
        val DARK_GRAY = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(56, 56, 56), Color(0, 4, 0))
        val GRAY_DISABLED = Style(EssentialPalette.TEXT_DISABLED, Color(37, 37, 37), Color(0, 0, 0))
        val NOTICE_GREEN = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(50, 123, 68), Color(255, 255, 255))
        val GREEN = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(34, 97, 50), Color(0, 4, 0))
        val LIGHT_GREEN = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(45, 121, 65), Color(255, 255, 255))
        val GREEN_DISABLED = Style(EssentialPalette.TEXT_DISABLED, Color(17, 60, 28), Color(0, 4, 0))
        val BLUE = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(39, 70, 115), Color(0, 4, 0))
        val BLUE_DISABLED = Style(EssentialPalette.TEXT_DISABLED, Color(30, 42, 60), Color(0, 4, 0))
        val LIGHT_BLUE = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(48, 115, 212), Color(255, 255, 255))
        val RED = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(159, 68, 68), Color(0, 4, 0))
        val LIGHT_RED = Style(EssentialPalette.TEXT_HIGHLIGHT, Color(192, 37, 37), Color(255, 255, 255))
        val RED_DISABLED = Style(EssentialPalette.TEXT_DISABLED, Color(64, 27, 27), Color(0, 4, 0))
    }

    /**
     * Represents a button's color styles, where [textColor] is the color of the text,
     * [buttonColor] is the color of the button itself, and [outlineColor] is the color of the button's outline.
     */
    data class Style(
        val textColor: Color,
        val buttonColor: Color,
        val outlineColor: Color,
        val highlightColor: Color = buttonColor.brighter(),
        val textShadow: Color = EssentialPalette.TEXT_SHADOW,

        @Deprecated("Use ZIndexEffect instead.")
        val sides: Set<OutlineEffect.Side> = OutlineEffect.Side.values().toSet(),
    )

    /**
     * Represents the horizontal alignment of text or icons.
     * By default, Alignment adds some padding to avoid positioning the text/icon directly on the edge of a [MenuButton].
     * Use Alignment.noPadding to remove the padding.
     *
     * @see LEFT
     * @see CENTER
     * @see RIGHT
     */
    enum class Alignment(val constraint: () -> XConstraint, val noPadding: () -> XConstraint) {
        /** Aligns text to the left of a button or 4 pixels to the right of a left-aligned icon. */
        LEFT({ SiblingConstraint(4f).coerceAtLeast(6.pixels) }, { SiblingConstraint() }),

        /** This is specific to [NoticeFlag]. It does not belong in here but Mitch put it in here and now here it is, and I'm not touching any of this code until we've got automated tests for it. */
        LEFT_SMALL_PADDING({ 5.pixels }, { 0.pixels }),

        /** Aligns text to the center of a button. */
        CENTER({ CenterConstraint() }, { CenterConstraint() }),

        /** Aligns text to the right of a button. */
        RIGHT({ 6.pixels(alignOpposite = true) }, { 0.pixels(alignOpposite = true) });
    }

    /**
     * Holds the different textures that a [MenuButton] can have.
     */
    object ButtonTextures {
        enum class Type {
            Vanilla,
            Essential
        }

        /**
         * Returns the best texture for the button.
         * If [EssentialConfig.useVanillaButtonForRetexturing] is true, then the vanilla texture will be used.
         * Otherwise, the essential texture will be used if available, and no texture will be used if it is not.
         */
        fun currentTexture(hovered: Boolean): Pair<Type, Bitmap?> {
            if (platform.config.useVanillaButtonForRetexturing.get()) {
                val vanillaButtonTexture = if (hovered) {
                    vanillaHighlightedButtonTexture.get()
                } else {
                    vanillaDefaultButtonTexture.get()
                }

                return Type.Vanilla to vanillaButtonTexture
            } else {
                val essentialButtonTexture = if (hovered) {
                    essentialHighlightedButtonTexture.get()
                } else {
                    essentialDefaultButtonTexture.get()
                }

                return Type.Essential to essentialButtonTexture

            }
        }

        /**
         * The full path for this texture is: /assets/essential/textures/button.png.
         * The texture is 200x40 pixels, with the top 20 pixels being the default button texture and the bottom 20 pixels being the highlighted button texture.
         * The button textures must include the outline.
         *
         * This texture is only used when [EssentialConfig.useVanillaButtonForRetexturing] is disabled.
         */
        private val essentialButtonTexture = UIdentifier("essential", "textures/button.png").bitmapState()

        private val vanillaWidgetsTexture =
            UIdentifier("minecraft", "textures/gui/widgets.png")
                .bitmapStateIf(platform.config.useVanillaButtonForRetexturing)

        /**
         * This is only on 1.20.2+ where the pack format is 16+, older versions will use [vanillaWidgetsTexture].
         */
        private val vanillaButtonSpriteTexture =
            UIdentifier("minecraft", "textures/gui/sprites/widget/button.png")
                .bitmapStateIf(platform.config.useVanillaButtonForRetexturing)

        /**
         * This is only on 1.20.2+ where the pack format is 16+, older versions will use [vanillaWidgetsTexture].
         */
        private val vanillaHighlightedButtonSpriteTexture =
            UIdentifier("minecraft", "textures/gui/sprites/widget/button_highlighted.png")
                .bitmapStateIf(platform.config.useVanillaButtonForRetexturing)

        private val essentialDefaultButtonTexture = essentialButtonTexture.map {
            val image = it ?: return@map null

            val textureResolution = image.width / 200
            image.cropped(0, 0, 200 * textureResolution, 20 * textureResolution)
        }

        private val essentialHighlightedButtonTexture = essentialButtonTexture.map {
            val image = it ?: return@map null

            val textureResolution = image.width / 200
            image.cropped(0, 20 * textureResolution, 200 * textureResolution, 20 * textureResolution)
        }

        private val vanillaDefaultButtonTexture = vanillaWidgetsTexture.zip(vanillaButtonSpriteTexture).map { (widgets, sprite) ->
            if (sprite != null) {
                return@map sprite
            }

            if (widgets == null) {
                return@map null
            }

            val textureResolution = widgets.width / 256
            widgets.cropped(0, 66 * textureResolution, 200 * textureResolution, 20 * textureResolution)
        }

        private val vanillaHighlightedButtonTexture = vanillaWidgetsTexture.zip(vanillaHighlightedButtonSpriteTexture).map { (widgets, sprite) ->
            if (sprite != null) {
                return@map sprite
            }

            if (widgets == null) {
                return@map null
            }

            val textureResolution = widgets.width / 256
            widgets.cropped(0, 86 * textureResolution, 200 * textureResolution, 20 * textureResolution)
        }
    }
}
