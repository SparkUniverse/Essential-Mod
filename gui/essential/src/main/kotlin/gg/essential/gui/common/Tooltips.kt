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
import gg.essential.elementa.components.*
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillMcScale
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.state.toConstraint
import gg.essential.elementa.utils.ObservableClearEvent
import gg.essential.elementa.utils.ObservableRemoveEvent
import gg.essential.elementa.utils.getStringSplitToWidth
import gg.essential.elementa.utils.roundToRealPixels
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.util.isInComponentTree
import java.awt.Color
import gg.essential.gui.elementa.state.v2.State as StateV2

abstract class AbstractTooltip(private val logicalParent: UIComponent) : UIContainer() {

    private var removalListeners = mutableListOf<() -> Unit>()

    init {
        isFloating = true
    }

    fun bindVisibility(visible: StateV2<Boolean>): AbstractTooltip {
        val toggle = { show: Boolean ->
            if (show) {
                showTooltip()
            } else {
                hideTooltip()
            }
        }

        effect(logicalParent) {
            toggle(visible())
        }

        return this
    }

    /** What the user requested by calling [showTooltip]/[hideTooltip] or via [bindVisibility]. */
    private var requestedVisible = false
    /** Whether this [AbstractTooltip] is currently mounted in the [Window]. */
    private var isTooltipMounted = false
    /** Whether [updateFunc] is currently mounted in [logicalParent]. See [updateFunc] docs. */
    private var isUpdateFuncMounted = false

    fun showTooltip() {
        requestedVisible = true
        Window.enqueueRenderOperation { updateMountState() }
    }

    fun hideTooltip() {
        requestedVisible = false
        Window.enqueueRenderOperation { updateMountState() }
    }

    private fun updateMountState() {
        if (requestedVisible) {
            val isParentMounted = logicalParent.isInComponentTree()
            if (isParentMounted) {
                if (!isTooltipMounted) mountTooltip()
                if (isUpdateFuncMounted) unmountUpdateFunc()
            } else {
                if (isTooltipMounted) unmountTooltip()
                if (!isUpdateFuncMounted) mountUpdateFunc()
            }
        } else {
            if (isUpdateFuncMounted) unmountUpdateFunc()
            if (isTooltipMounted) unmountTooltip()
        }
    }

    /**
     * When the [logicalParent] is not yet (or currently) in a [Window], we register this [UpdateFunc] on it to get
     * notified once it is mounted, so we can then mount the tooltip.
     */
    private val updateFunc: UpdateFunc = { _, _ ->
        updateMountState()
    }

    private fun mountUpdateFunc() {
        assert(!isUpdateFuncMounted)
        isUpdateFuncMounted = true
        logicalParent.addUpdateFunc(updateFunc)
    }

    private fun unmountUpdateFunc() {
        assert(isUpdateFuncMounted)
        isUpdateFuncMounted = false
        logicalParent.removeUpdateFunc(updateFunc)
    }

    private fun mountTooltip() {
        assert(!isTooltipMounted)
        isTooltipMounted = true

        val window = Window.of(logicalParent)
        window.addChild(this)

        // When our logical parent is removed from the component tree, we also need to remove ourselves (our actual
        // parent is the window, so that is not going to happen by itself).
        // We need to do that asap because our constraints may depend on our logical parent and may error when evaluated
        // after our logical parent got removed.
        // Elementa has no unmount event, so instead we listen for changes to the children list of all our parents.
        fun UIComponent.onRemoved(listener: () -> Unit) {
            if (parent == this) {
                return
            }

            val observer = java.util.Observer { _, event ->
                if (event is ObservableClearEvent<*> || event is ObservableRemoveEvent<*> && event.element.value == this) {
                    listener()
                }
            }
            parent.children.addObserver(observer)
            removalListeners.add { parent.children.deleteObserver(observer) }

            parent.onRemoved(listener)
        }
        logicalParent.onRemoved {
            updateMountState()
        }
    }

    private fun unmountTooltip() {
        assert(isTooltipMounted)
        isTooltipMounted = false

        val window = Window.of(this)

        window.removeChild(this)

        removalListeners.forEach { it() }
        removalListeners.clear()
    }

    override fun isPointInside(x: Float, y: Float): Boolean = false
}

open class LayoutDslTooltip(
    logicalParent: UIComponent,
    private val layout: (LayoutScope.() -> Unit)?,
) : AbstractTooltip(logicalParent) {

    init {
        constrain {
            width = ChildBasedMaxSizeConstraint()
            height = ChildBasedMaxSizeConstraint()
        }
        this.layoutAsBox {
            layout()
        }
    }

    fun LayoutScope.layout() {
        layout?.invoke(this)
    }

}

abstract class Tooltip(logicalParent: UIComponent) : AbstractTooltip(logicalParent) {

    var textColorState = BasicState(Color.WHITE)
    var textShadowColorState: State<Color?> = BasicState(EssentialPalette.BLACK)
    var textShadowState = BasicState(true)

    init {
        constrain {
            width = ChildBasedMaxSizeConstraint() + 8.pixels
            height = ChildBasedSizeConstraint() + 8.pixels
        }
    }

    val content by UIContainer().constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = ChildBasedMaxSizeConstraint()
        height = ChildBasedSizeConstraint()
    } childOf this

    @Deprecated("Using StateV1 is discouraged, use StateV2 instead")
    fun bindVisibility(visible: State<Boolean>): Tooltip {
        visible.onSetValueAndNow {
            if (it) {
                showTooltip()
            } else {
                hideTooltip()
            }
        }
        return this
    }

    fun clearLines() {
        content.clearChildren()
    }

    fun addLine(text: String = "", configure: UIText.() -> Unit = {}) : Tooltip = apply {
        val component = EssentialUIText(text, centeringContainsShadow = true)
            .bindShadow(textShadowState).bindShadowColor(textShadowColorState)
            .constrain {
                x = CenterConstraint()
                y = SiblingConstraint(3f)
                color = textColorState.toConstraint()
            } childOf content
        component.configure()
    }

    fun bindText(state: State<String>, wrapAtWidth: Float? = null, configure: UIText.() -> Unit = {}): Tooltip =
        bindLine(state, wrapAtWidth, configure)

    // Old name of bindText, contrary to its name it actually supports multiple lines and you cannot call it multiple times to add more lines
    fun bindLine(state: StateV2<String>, wrapAtWidth: Float? = null, configure: UIText.() -> Unit = {}): Tooltip {
        effect(this) {
            clearLines()
            state().lines().forEach { fullLine ->
                if (wrapAtWidth != null) {
                    val lines = getStringSplitToWidth(fullLine, wrapAtWidth, 1f, processColorCodes = false)
                    for (line in lines) {
                        addLine(line, configure)
                    }
                } else {
                    addLine(fullLine, configure)
                }
            }
        }
        return this
    }

    fun bindLine(state: State<String>, wrapAtWidth: Float? = null, configure: UIText.() -> Unit = {}) =
        bindLine(state.toV2(), wrapAtWidth, configure)

}

class EssentialTooltip(
    private val logicalParent: UIComponent,
    private val position: Position,
    private val notchSize: Int = 3,
) :
    Tooltip(logicalParent) {

    init {
        textColorState.set(EssentialPalette.TEXT_HIGHLIGHT)

        constrain {
            width = ChildBasedMaxSizeConstraint() + 8.pixels
            height = ChildBasedSizeConstraint() + 6.pixels
        }
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        // Background
        extractor.fillMcScale(
            getLeft() - 1,
            getTop() - 1,
            getRight() + 1,
            getBottom() + 1,
            EssentialPalette.BLACK,
        )
        extractor.fillMcScale(
            getLeft(),
            getTop(),
            getRight(),
            getBottom(),
            EssentialPalette.COMPONENT_BACKGROUND,
        )

        // Notch
        val hCenter = ((logicalParent.getLeft() + logicalParent.getRight()) / 2.0f).roundToRealPixels()
        val vCenter = ((logicalParent.getTop() + logicalParent.getBottom()) / 2.0f).roundToRealPixels()

        val left = getLeft() + 1
        val right = getRight() - 1
        val top = getTop() + 1
        val bottom = getBottom() - 1

        for (i in 1..notchSize) {
            extractor.fillMcScale(
                when (position) {
                    Position.LEFT -> right + 1 + i
                    Position.RIGHT -> left - 2 - i
                    Position.ABOVE -> hCenter - (notchSize - i) - 0.5f
                    Position.BELOW -> hCenter - (notchSize - i) - 0.5f
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                when (position) {
                    Position.LEFT -> vCenter - (notchSize - i) - 0.5f
                    Position.RIGHT -> vCenter - (notchSize - i) - 0.5f
                    Position.ABOVE -> bottom + i
                    Position.BELOW -> top - 2 - i
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                when (position) {
                    Position.LEFT -> right + 2 + i
                    Position.RIGHT -> left - 1 - i
                    Position.ABOVE -> hCenter + (notchSize - i) + 0.5f
                    Position.BELOW -> hCenter + (notchSize - i) + 0.5f
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                when (position) {
                    Position.LEFT -> vCenter + (notchSize - i) + 0.5f
                    Position.RIGHT -> vCenter + (notchSize - i) + 0.5f
                    Position.ABOVE -> bottom + i + 2
                    Position.BELOW -> top - i - 1
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                EssentialPalette.BLACK,
            )
            extractor.fillMcScale(
                when (position) {
                    Position.LEFT -> right + i
                    Position.RIGHT -> left - 1 - i
                    Position.ABOVE -> hCenter - (notchSize - i) - 0.5f
                    Position.BELOW -> hCenter - (notchSize - i) - 0.5f
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                when (position) {
                    Position.LEFT -> vCenter - (notchSize - i) - 0.5f
                    Position.RIGHT -> vCenter - (notchSize - i) - 0.5f
                    Position.ABOVE -> bottom + i
                    Position.BELOW -> top - 1 - i
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                when (position) {
                    Position.LEFT -> right + 1 + i
                    Position.RIGHT -> left - i
                    Position.ABOVE -> hCenter + (notchSize - i) + 0.5f
                    Position.BELOW -> hCenter + (notchSize - i) + 0.5f
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                when (position) {
                    Position.LEFT -> vCenter + (notchSize - i) + 0.5f
                    Position.RIGHT -> vCenter + (notchSize - i) + 0.5f
                    Position.ABOVE -> bottom + i + 1
                    Position.BELOW -> top - i
                    Position.MOUSE -> continue
                    is Position.MOUSE_OFFSET -> continue
                },
                EssentialPalette.COMPONENT_BACKGROUND,
            )
        }
    }

    sealed interface Position {
        data object LEFT : Position
        data object RIGHT : Position
        data object ABOVE : Position
        data object BELOW : Position
        data object MOUSE : Position
        data class MOUSE_OFFSET(val xOffset: Float = 0f, val yOffset: Float = 0f) : Position
    }
}


