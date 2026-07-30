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
package gg.essential.gui.wardrobe.configuration.cosmetic.properties

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.state.toConstraint
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.bindParent
import gg.essential.gui.common.effect.CheckerboardBackgroundEffect
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.common.input.essentialStringInput
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.common.or
import gg.essential.gui.common.shadow.ShadowEffect
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.util.hoveredState
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.model.util.Color
import gg.essential.model.util.toJavaColor
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.USound
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.universal.vertex.UVertexConsumer
import gg.essential.util.*
import gg.essential.vigilance.utils.onLeftClick
import org.lwjgl.opengl.GL11

class VariantsPropertyConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.Variants>(
    CosmeticPropertyType.VARIANTS,
    CosmeticProperty.Variants::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.Variants) {
        val variants = property.data.variants.toMutableList()

        fun update() {
            property.update(property.copy(data = property.data.copy(variants = variants.toList())))
        }

        column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
            for ((index, variant) in variants.withIndex()) {
                row(Modifier.fillWidth(), Arrangement.spacedBy(5f, FloatPosition.CENTER)) {
                    essentialStringInput(mutableStateOf(variant.name)).state.onChange(stateScope) {
                        variants[index] = variant.copy(name = it)
                        update()
                    }
                    text("RGB #")
                    essentialStateTextInput(
                        mutableStateOf(variant.color),
                        { String.format("%08X", it.rgba.toInt()).substring(0, 6) },
                        { Color.rgba(it.padEnd(8, 'F').toUInt(16)) },
                        Modifier.width(50f)
                    ).state.onChange(stateScope) {
                        variants[index] = variant.copy(color = it)
                        update()
                    }
                    box(Modifier.height(10f).widthAspect(1f)) {
                        val colorState = mutableStateOf(variant.color)
                        val colorPicker = VariantColorPicker(colorState)
                        colorPicker.showMenu.onSetValue {
                            if (!it) {
                                variants[index] = variant.copy(color = colorState.get())
                                update()
                            }
                        }
                        colorPicker()
                    }
                    icon(EssentialPalette.ARROW_UP_7X5).onLeftClick {
                        if (index > 0) {
                            USound.playButtonPress()
                            variants.removeAt(index)
                            variants.add(index - 1, variant)
                            update()
                        }
                    }
                    icon(EssentialPalette.ARROW_DOWN_7X5).onLeftClick {
                        if (index + 1 < variants.size) {
                            USound.playButtonPress()
                            variants.removeAt(index)
                            variants.add(index + 1, variant)
                            update()
                        }
                    }
                    icon(EssentialPalette.CANCEL_5X).onLeftClick {
                        USound.playButtonPress()
                        variants.removeAt(index)
                        update()
                    }
                }
            }
            row(Modifier.fillWidth().height(10f).hoverColor(EssentialPalette.LIGHT_DIVIDER).hoverScope(), Arrangement.spacedBy(5f, FloatPosition.START)) {
                text("Add")
                icon(EssentialPalette.PLUS_5X)
            }.onLeftClick {
                USound.playButtonPress()
                variants.add(CosmeticProperty.Variants.Variant("new_variant", Color(0.toUInt())))
                update()
            }
        }
    }

    inner class VariantColorPicker(val currentColorState: MutableState<Color>) : UIContainer() {

        val showMenu = BasicState(false)
        private val hue = BasicState(HSBColor(currentColorState.getUntracked().toJavaColor()).hue)
        private val saturation = BasicState(HSBColor(currentColorState.getUntracked().toJavaColor()).saturation)
        private val brightness = BasicState(HSBColor(currentColorState.getUntracked().toJavaColor()).brightness)
        private val hueSaturationSide = 69f

        private val hueColorList: List<java.awt.Color> =
            (0..hueSaturationSide.toInt()).map { i -> java.awt.Color(java.awt.Color.HSBtoRGB(i / hueSaturationSide, 1f, 0.9f)) }
        private val componentCrossDimension =
            9f // The other side of the rectangle that isn't of equal side fo [hueSaturationSide]

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
        }.bindParent(showMenu.map { if (it) Window.ofOrNull(this) else null })

        private val selectorBlock by UIBlock(EssentialPalette.BUTTON_HIGHLIGHT).constrain {
            x = 0.pixels boundTo collapsedBlock
            y = SiblingConstraint(0f, alignOpposite = true) boundTo buttonConnector
            width = ChildBasedSizeConstraint() + 4.pixels
            height = ChildBasedSizeConstraint() + 4.pixels
        }.bindParent(showMenu.map { if (it) Window.ofOrNull(this) else null })

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

        private val hueContainer by UIBlock(EssentialPalette.BUTTON).constrain {
            x = SiblingConstraint(5f)
            height = hueSaturationSide.pixels
            width = componentCrossDimension.pixels
        } childOf hueSaturationBrightnessRow

        private val hueArea by object : UIComponent() {
            override fun draw(matrixStack: UMatrixStack) {
                beforeDraw(matrixStack)
                drawHueLine(matrixStack, this)

                super.draw(matrixStack)
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
            override fun draw(matrixStack: UMatrixStack) {
                beforeDraw(matrixStack)
                drawColorPicker(matrixStack, this)

                super.draw(matrixStack)
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

        init {

            createMouseDragListener(hueContainer).onSetValue { (_, yPercent) ->
                hue.set(yPercent)
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

            hsb.onSetValueAndNow { hsb ->
                val (hs, brightness) = hsb
                val (hue, saturation) = hs

                val color = HSBColor(hue, saturation, brightness, 1f)
                val javaColor = color.toColor()
                currentColorState.set(Color(javaColor.red.toUByte(), javaColor.green.toUByte(), javaColor.blue.toUByte(), javaColor.alpha.toUByte()))
                //penTool.color = color.toColor()
                currentColor.setColor(javaColor)
            }

            effect(ShadowEffect(java.awt.Color.BLACK))
        }

        private fun drawColorPicker(matrixStack: UMatrixStack, component: UIComponent) {
            val left = component.getLeft().toDouble()
            val top = component.getTop().toDouble()
            val right = component.getRight().toDouble()
            val bottom = component.getBottom().toDouble()

            setupDraw()
            val graphics = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)

            val horizontalSize = (right - left).toInt()

            for (x in (0..horizontalSize)) {
                val curLeft = left + x
                val curRight = curLeft + 1

                var first = true
                val verticalSize = (bottom - top).toInt()
                for (y in 0..verticalSize) {
                    val yPos = top + y
                    val color = getColor(x.toFloat() / horizontalSize, 1 - y.toFloat() / verticalSize, hue.get())

                    if (!first) {
                        drawVertex(graphics, matrixStack, curLeft, yPos, color)
                        drawVertex(graphics, matrixStack, curRight, yPos, color)
                    }

                    if (y < horizontalSize) {
                        drawVertex(graphics, matrixStack, curRight, yPos, color)
                        drawVertex(graphics, matrixStack, curLeft, yPos, color)
                    }
                    first = false
                }

            }

            graphics.build()?.drawAndClose(COLOR_PICKER_PIPELINE)
            cleanupDraw()
        }

        private fun getColor(x: Float, y: Float, hue: Float): java.awt.Color {
            return java.awt.Color(java.awt.Color.HSBtoRGB(hue, x, y))
        }

        private fun drawHueLine(matrixStack: UMatrixStack, component: UIComponent) {
            val left = component.getLeft().toDouble()
            val top = component.getTop().toDouble()
            val right = component.getRight().toDouble()
            val height = component.getHeight().toDouble()

            setupDraw()
            val graphics = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)

            var first = true
            for ((i, color) in hueColorList.withIndex()) {
                val yPos = top + (i.toFloat() * height / hueSaturationSide)
                if (!first) {
                    drawVertex(graphics, matrixStack, left, yPos, color)
                    drawVertex(graphics, matrixStack, right, yPos, color)
                }

                drawVertex(graphics, matrixStack, right, yPos, color)
                drawVertex(graphics, matrixStack, left, yPos, color)

                first = false
            }

            graphics.build()?.drawAndClose(COLOR_PICKER_PIPELINE)
            cleanupDraw()
        }

        private fun setupDraw() {
            UGraphics.shadeModel(GL11.GL_SMOOTH)
        }

        private fun cleanupDraw() {
            UGraphics.shadeModel(GL11.GL_FLAT)
        }

        private fun drawVertex(graphics: UVertexConsumer, matrixStack: UMatrixStack, x: Double, y: Double, color: java.awt.Color) {
            graphics
                .pos(matrixStack, x, y, 0.0)
                .color(
                    color.red.toFloat() / 255f,
                    color.green.toFloat() / 255f,
                    color.blue.toFloat() / 255f,
                    color.alpha.toFloat() / 255f
                )
                .endVertex()
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
    }

    companion object {
        private val COLOR_PICKER_PIPELINE = URenderPipeline.builderWithDefaultShader(
            "essential:variant_color_picker",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}
