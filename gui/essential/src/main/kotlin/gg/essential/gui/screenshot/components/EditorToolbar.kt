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
package gg.essential.gui.screenshot.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.state.toConstraint
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.bindParent
import gg.essential.gui.common.constraints.extractAlpha
import gg.essential.gui.common.constraints.extractColorPicker
import gg.essential.gui.common.constraints.extractHueLine
import gg.essential.gui.common.effect.CheckerboardBackgroundEffect
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.common.or
import gg.essential.gui.common.shadow.ShadowEffect
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.elementa.state.v2.State as StateV2
import gg.essential.gui.screenshot.editor.ScreenshotCanvas
import gg.essential.gui.screenshot.editor.change.EditHistory
import gg.essential.gui.screenshot.editor.tools.PenTool
import gg.essential.gui.util.hoveredState
import gg.essential.universal.UKeyboard
import gg.essential.universal.USound
import gg.essential.util.bindHoverEssentialTooltip
import gg.essential.util.centered
import gg.essential.gui.util.hoveredState
import gg.essential.lib.gson.Gson
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.HSBColor
import gg.essential.vigilance.utils.onLeftClick
import java.awt.Color
import java.io.IOException
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class EditorToolbar(
    isScreenOpen: StateV2<Boolean>,
    private val canvas: ScreenshotCanvas,
    private val editHistory: EditHistory,
    private val active: State<Boolean>
) : UIContainer() {

    private val penTool = PenTool(editHistory, canvas)

    private val colorPicker by ScreenshotColorPicker(
        penTool,
        active
    ).constrain {
        x = SiblingConstraint(3f)
        y = 0.pixels(alignOpposite = true)
    } childOf this

    private val strokeWidthComponent by StrokeWidthComponent(penTool).constrain {
        x = SiblingConstraint(3f)
        y = 0.pixels(alignOpposite = true)
    } childOf this

    private val undo by IconButton(EssentialPalette.UNDO_9X, tooltipText = "Undo").constrain {
        x = SiblingConstraint(3f)
        y = 0.pixels(alignOpposite = true)
        width = AspectConstraint(1f)
        height = 100.percent
    }.rebindEnabled(editHistory.undoEnabled.toV1(this)).onActiveClick {
        undo()
    } childOf this

    private val redo by IconButton(EssentialPalette.REDO_9X, tooltipText = "Redo").constrain {
        x = SiblingConstraint(3f)
        y = 0.pixels(alignOpposite = true)
        width = AspectConstraint(1f)
        height = 100.percent
    }.rebindEnabled(editHistory.redoEnabled.toV1(this)).onActiveClick {
        redo()
    } childOf this


    init {
        penTool.enable()
        constrain {
            height = 17.pixels
            width = ChildBasedSizeConstraint()
        }

        effect(this) {
            if (!isScreenOpen()) colorPicker.saveColors()
        }
    }

    override fun afterInitialization() {
        super.afterInitialization()

        Window.of(this).onKeyType { _, keyCode ->
            if (active.get()) {
                if (UKeyboard.isKeyComboCtrlZ(keyCode)) {
                    undo()
                } else if (UKeyboard.isKeyComboCtrlY(keyCode)) {
                    redo()
                }
            }
        }
    }

    private fun undo() {
        editHistory.undo()
    }

    private fun redo() {
        editHistory.redo()
    }
}

class StrokeWidthComponent(val penTool: PenTool) : UIBlock(EssentialPalette.COMPONENT_BACKGROUND) {

    private val container by UIContainer().centered().constrain {
        width = ChildBasedSizeConstraint()
        height = 100.percent
    } childOf this

    private val selectedWidth = BasicState(3)

    init {
        constrain {
            width = ChildBasedSizeConstraint() + 4.pixels
            height = 100.percent
        }

        for (i in 1..5) {
            Stoke(i) childOf container
        }

        selectedWidth.onSetValueAndNow {
            penTool.width = it.toFloat()
        }

        effect(ShadowEffect(Color.BLACK))
    }

    inner class Stoke(val width: Int) : UIContainer() {

        private val selected = selectedWidth.map { it == width }
        private val hovered = hoveredState()

        private val block by UIBlock().centered().constrain {
            width = this@Stoke.width.pixels
            height = 100.percent - 4.pixels
        } childOf this

        init {
            constrain {
                x = SiblingConstraint(1f)
                y = CenterConstraint()
                width = ChildBasedSizeConstraint() + 2.pixels // Increase hitbox a little to make it easier to click
                height = 100.percent - 2.pixels
            }

            onLeftClick {
                selectedWidth.set(width)
            }

            selected.onSetValue {
                if (it) {
                    USound.playButtonPress()
                }
            }

            bindHoverEssentialTooltip(BasicState("Brush Size: $width"))

            block.setColor(selected.zip(hovered).map { (selected, hovered) ->
                if (selected) {
                    EssentialPalette.TEXT_HIGHLIGHT
                } else if (hovered) {
                    EssentialPalette.TEXT
                } else {
                    EssentialPalette.BUTTON_HIGHLIGHT
                }
            }.toConstraint())
        }
    }
}

class ScreenshotColorPicker(
    private val penTool: PenTool,
    active: State<Boolean>,
) : UIContainer() {

    private val showMenu = BasicState(false)
    private val hue = BasicState(0f)
    private val saturation = BasicState(1f)
    private val brightness = BasicState(1f)
    private val alpha = BasicState(1f)
    private val hueSaturationSide = 69f

    private val componentCrossDimension =
        9f // The other side of the rectangle that isn't of equal side fo [hueSaturationSide]

    private lateinit var currentColorState: State<HSBColor>

    private val hsb = hue.zip(saturation).zip(brightness)

    private val collapsedBlock by UIBlock().constrain {
        y = 0.pixels(alignOpposite = true)
        width = AspectConstraint(1f)
        height = 100.percent
    }.onLeftClick {
        showMenu.set { !it }
    } childOf this

    // color bound in init
    private val currentColor by UIBlock().centered().constrain {
        width = 100.percent - 6.pixels
        height = 100.percent - 6.pixels
    } childOf collapsedBlock effect CheckerboardBackgroundEffect()

    private val buttonConnector by UIBlock(EssentialPalette.BUTTON_HIGHLIGHT).constrain {
        x = 0.pixels boundTo collapsedBlock
        y = SiblingConstraint(0f, alignOpposite = true) boundTo collapsedBlock
        width = 100.percent boundTo collapsedBlock
        height = 3.pixels
    }.apply { isFloating = true }.onMouseClick { it.stopPropagation() }.bindParent(collapsedBlock, showMenu)

    private val selectorBlock by UIBlock(EssentialPalette.BUTTON_HIGHLIGHT).constrain {
        x = 0.pixels boundTo collapsedBlock
        y = SiblingConstraint(0f, alignOpposite = true) boundTo buttonConnector
        width = ChildBasedSizeConstraint() + 4.pixels
        height = ChildBasedSizeConstraint() + 4.pixels
    }.apply { isFloating = true }.onMouseClick { it.stopPropagation() }.bindParent(collapsedBlock, showMenu)

    private val selectorContent by UIBlock(EssentialPalette.COMPONENT_BACKGROUND).centered().constrain {
        width = ChildBasedSizeConstraint() + 6.pixels
        height = ChildBasedSizeConstraint() + 6.pixels
    } childOf selectorBlock

    private val selectorInnerContent by UIContainer().centered().constrain {
        width = ChildBasedMaxSizeConstraint()
        height = ChildBasedSizeConstraint()
    } childOf selectorContent

    private val hueSaturationBrightnessRow by UIContainer().constrain {
        width = ChildBasedSizeConstraint()
        height = ChildBasedMaxSizeConstraint()
    } childOf selectorInnerContent


    private val saturationBrightnessContainer by UIBlock(EssentialPalette.BUTTON).constrain {
        width = hueSaturationSide.pixels
        height = AspectConstraint(1f)
    } childOf hueSaturationBrightnessRow

    private val alphaContainer by UIBlock(EssentialPalette.BUTTON).constrain {
        y = SiblingConstraint(5f)
        height = componentCrossDimension.pixels
        width = hueSaturationSide.pixels
    } childOf selectorInnerContent

    private val colorHistoryBlock by UIBlock(EssentialPalette.BUTTON).constrain {
        y = SiblingConstraint(5f)
        height = componentCrossDimension.pixels
        width = ChildBasedSizeConstraint() + 2.pixels
    } childOf selectorInnerContent

    private val colorHistoryContainer by UIContainer().centered().constrain {
        width = ChildBasedSizeConstraint()
        height = 100.percent - 2.pixels
    } childOf colorHistoryBlock

    private val hueContainer by UIBlock(EssentialPalette.BUTTON).constrain {
        x = SiblingConstraint(5f)
        height = hueSaturationSide.pixels
        width = componentCrossDimension.pixels
    } childOf hueSaturationBrightnessRow

    private val alphaContentContainer by UIContainer().centered().constrain {
        width = 100.percent() - 4.pixels
        height = 100.percent() - 4.pixels
    } childOf alphaContainer

    private val alphaArea by object : UIComponent() {
        override fun extractComponent(extractor: ElementaExtractor) {
            extractAlpha(extractor, this, currentColorState.get().toColor())
        }
    }.constrain {
        width = 100.percent
        height = 100.percent
    } childOf alphaContentContainer effect CheckerboardBackgroundEffect()

    private val alphaSlider by UIContainer().constrain {
        x = RelativeConstraint().bindValue(alpha).coerceIn(0.pixels, 100.percent - 1.pixels)
        y = CenterConstraint()
        width = 1.pixel
        height = 100.percent
    } effect OutlineEffect(EssentialPalette.TEXT_HIGHLIGHT, 1f) childOf alphaArea


    private val hueArea by object : UIComponent() {
        override fun extractComponent(extractor: ElementaExtractor) {
            extractHueLine(extractor, this)
        }
    }.centered().constrain {
        width = 100.percent() - 4.pixels
        height = 100.percent() - 4.pixels
    } childOf hueContainer

    private val hueSlider by UIContainer().constrain {
        x = CenterConstraint()
        y = RelativeConstraint().bindValue(hue).coerceIn(0.pixels, 100.percent - 1.pixels)
        width = 100.percent
        height = 1.pixel
    } effect OutlineEffect(EssentialPalette.TEXT_HIGHLIGHT, 1f) childOf hueArea

    private val saturationBrightnessArea by object : UIComponent() {
        override fun extractComponent(extractor: ElementaExtractor) {
            extractColorPicker(extractor, this, hue.get())
        }
    }.centered().constrain {
        width = 100.percent() - 4.pixels
        height = 100.percent() - 4.pixels
    } childOf saturationBrightnessContainer

    private val saturationBrightnessSelection by UIContainer().constrain {
        x = RelativeConstraint().bindValue(saturation).coerceIn(0.pixels, 100.percent - 1.pixels)
        y = RelativeConstraint().bindValue(brightness.map { 1 - it }).coerceIn(0.pixels, 100.percent - 1.pixel)
        width = 1.pixel
        height = 1.pixel
    } childOf saturationBrightnessArea effect OutlineEffect(EssentialPalette.TEXT_HIGHLIGHT, 1f)

    private val activeColorIndex = BasicState(0)

    init {

        createMouseDragListener(hueContainer).onSetValue { (_, yPercent) ->
            hue.set(yPercent)
        }

        createMouseDragListener(alphaContainer).onSetValue { (xPercent, _) ->
            alpha.set(xPercent)
        }

        createMouseDragListener(saturationBrightnessContainer).onSetValue { (xPercent, yPercent) ->
            saturation.set(xPercent)
            brightness.set(1 - yPercent)
        }

        collapsedBlock.setColor(
            EssentialPalette.getButtonColor(collapsedBlock.hoveredState() or showMenu).toConstraint()
        )
        constrain {
            height = 100.percent
            width = ChildBasedMaxSizeConstraint()
        }

        for ((index, colorPreset) in loadEditorColors().withIndex()) {
            val colorChoice = ColorChoice(BasicState(colorPreset), index)
            colorChoice childOf colorHistoryContainer
        }

        activeColorIndex.onSetValueAndNow { index ->

            val colorChoice = colorHistoryContainer.childrenOfType(ColorChoice::class.java)[index]

            currentColorState = colorChoice.containedColor

            val color = currentColorState.get()

            hue.set(color.hue)
            saturation.set(color.saturation)
            brightness.set(color.brightness)
        }

        hsb.zip(alpha).onSetValueAndNow { (hsb, alpha) ->
            val (hs, brightness) = hsb
            val (hue, saturation) = hs

            val color = HSBColor(hue, saturation, brightness, alpha)
            currentColorState.set(color)
            penTool.color = color.toColor()
            currentColor.setColor(color.toColor())
        }


        // When the editor is no longer active, we must close the menu because it is bound to Window
        active.onSetValue {
            if (!it) {
                showMenu.set(false)
            }
        }

        effect(ShadowEffect(Color.BLACK))
    }

    fun saveColors() {
        saveEditorColors(
            colorHistoryContainer.childrenOfType(ColorChoice::class.java).map { it.containedColor.get() }
        )
    }

    private fun createMouseDragListener(component: UIComponent): State<Pair<Float, Float>> {
        var mouseHeld = false
        val basicState = BasicState(0f to 0f)


        fun updateState(mouseX: Float, mouseY: Float) {

            val child = component.children[0]
            val offsetX = child.getLeft() - component.getLeft()
            val offsetY = child.getTop() - component.getTop()

            basicState.set(
                ((mouseX - offsetX) / child.getWidth()).coerceIn(0f..1f) to ((mouseY - offsetY) / child.getHeight()).coerceIn(
                    0f..1f
                )
            )
        }

        component.onLeftClick {
            USound.playButtonPress()
            mouseHeld = true
            updateState(it.relativeX, it.relativeY)
        }
        component.onMouseRelease {
            mouseHeld = false
        }
        component.onMouseDrag { mouseX, mouseY, _ ->
            if (!mouseHeld) return@onMouseDrag

            updateState(mouseX, mouseY)
        }
        return basicState
    }


    inner class ColorChoice(val containedColor: State<HSBColor>, val index: Int) : UIBlock() {
        val active = activeColorIndex.map { it == index }

        private val colorBlock by UIBlock(containedColor.map { it.toColor() }).centered().constrain {
            width = 100.percent - 2.pixels
            height = 100.percent - 2.pixels
        } childOf this

        init {
            constrain {
                x = SiblingConstraint(2f)
                width = AspectConstraint(1f)
                height = 100.percent
            }
            setColor(active.zip(hoveredState()).map { (active, hovered) ->
                if (active) {
                    EssentialPalette.TEXT_HIGHLIGHT
                } else if (hovered) {
                    EssentialPalette.GRAY_OUTLINE
                } else {
                    EssentialPalette.COMPONENT_BACKGROUND
                }
            }.toConstraint())

            onLeftClick {
                // Only play the sound if the stroke is going to be selected
                if (activeColorIndex.get() != index) {
                    USound.playButtonPress()
                }
                activeColorIndex.set { index }
            }
        }
    }

    companion object {
        private val editorStateFile = platform.essentialBaseDir / "screenshot-editor.json"

        private val DEFAULT_COLORS = listOf(HSBColor(0xD32121), HSBColor(0xEAB600), HSBColor(0x3B8A2F), HSBColor(0x0085FF), HSBColor(0x000000))
        private var savedColors: List<HSBColor>? = null

        private fun saveEditorColors(colors: List<HSBColor>) {
            if (savedColors == colors) {
                return
            }
            savedColors = colors
            try {
                editorStateFile.writeText(Gson().toJson(colors))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        private fun loadEditorColors(): List<HSBColor> {
            if (savedColors == null) {
                savedColors = try {
                    if (editorStateFile.exists()) {
                        Gson().fromJson(editorStateFile.readText(), Array<HSBColor>::class.java).toList()
                    } else {
                        null
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }?.takeUnless { it.size != 5 } ?: DEFAULT_COLORS
            }
            return savedColors!!
        }
    }
}