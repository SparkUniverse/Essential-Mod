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
package gg.essential.gui.overlay

import gg.essential.Essential
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.events.UIClickEvent
import gg.essential.elementa.events.UIScrollEvent
import gg.essential.event.gui.GuiClickEvent
import gg.essential.event.gui.GuiDrawScreenEvent
import gg.essential.event.gui.GuiKeyTypedEvent
import gg.essential.event.gui.GuiMouseReleaseEvent
import gg.essential.event.gui.MouseScrollEvent
import gg.essential.event.render.RenderTickEvent
import gg.essential.mixins.impl.client.gui.EssentialScreenMayAllowPlayerInput
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMouse
import gg.essential.universal.UResolution
import gg.essential.universal.UScreen
import gg.essential.util.UDrawContext
import gg.essential.util.isSorted
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import org.slf4j.LoggerFactory
import kotlin.math.max

//#if MC >= 26.2
//$$ import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen
//#endif

//#if MC>=12109
//$$ import net.minecraft.client.gui.Click
//$$ import net.minecraft.client.input.MouseInput
//#endif

//#if MC>=12106
//$$ import gg.essential.util.AdvancedDrawContext
//#endif

//#if MC>=11600
//$$ import gg.essential.mixins.transformers.client.MouseHelperAccessor
//$$ import net.minecraft.client.gui.widget.Widget
//#else
import gg.essential.mixins.transformers.client.gui.GuiScreenAccessor
import org.lwjgl.input.Mouse
import java.lang.invoke.MethodHandles
//#endif

object OverlayManagerImpl : OverlayManager {
    private val LOGGER = LoggerFactory.getLogger(OverlayManagerImpl::class.java)
    private val mc = Minecraft.getMinecraft()
    private val layers = mutableListOf<LayerImpl>()
    private val layersAndSpecials = mutableListOf<LayerOrSpecial>()
    private var screenOrder: List<GuiScreen> = emptyList()

    private const val FAKE_MOUSE_POS = -1e6 // far off-screen but not too far so math doesn't break when cast to Int
    private var layersWithTrueMousePos = emptySet<LayerOrSpecial>()

    private var focus: Pair<Layer, UIComponent>? = null

    override fun createLayer(priority: LayerPriority): Layer {
        return LayerImpl(priority)
    }

    override fun addLayer(layer: Layer) {
        layer as LayerImpl

        val comparator = makeLayerOrSpecialComparator(screenOrder)

        layers.add(layers.indexOfLast { comparator.compare(LayerOrSpecial.Layer(it), LayerOrSpecial.Layer(layer)) <= 0 } + 1, layer)

        val layerOrSpecial = LayerOrSpecial.Layer(layer)
        layersAndSpecials.add(layersAndSpecials.indexOfLast { comparator.compare(it, layerOrSpecial) <= 0 } + 1, layerOrSpecial)

        if (!Events.registered) Events.register()
    }

    override fun removeLayer(layer: Layer) {

        layers.remove(layer)
        layersAndSpecials.remove(LayerOrSpecial.Layer(layer))
        clickedLayers.removeIf { (it.first as? LayerOrSpecial.Layer)?.layer == layer }
    }

    /**
     * Returns the layer which the mouse is currently hovering.
     *
     * That is the top-most layer which contains a visible component at the mouse position.
     * Invisible components are those which do not get picked by [UIComponent.hitTest], or which have an alpha of zero,
     * or which extend [UIContainer]. If an invisible components gets picked by [UIComponent.hitTest] but one of its
     * parents is visible, then that layer is eligible as well (even if hitTest can never return that parent directly).
     *
     * Note that this does not consider vanilla layers. A [LayerPriority.BelowScreen] layer may be returned by this
     * method, although it will never be considered hovered in practice because the vanilla screen above it will always
     * take precedence.
     */
    fun getHoveredLayer(): Layer? {
        val mouseX = (UMouse.Scaled.x + 0.5 / UResolution.scaleFactor).toFloat()
        val mouseY = (UMouse.Scaled.y + 0.5 / UResolution.scaleFactor).toFloat()
        return layers.findLast { it.isAnythingHovered(mouseX, mouseY) }
    }

    /**
     * Returns the currently focused layer if any.
     * The returned layer will have a [Window.focusedComponent] set.
     */
    fun getFocusedLayer(): Layer? {
        return focus
            ?.takeIf { (layer, component) -> layer.window.focusedComponent == component }
            ?.first
    }

    /**
     * Disposes of any layers with a Window where [Window.hasErrored] is set to true.
     */
    private fun cleanupLayers() {
        layers.removeIf { it.window.hasErrored }
    }

    /**
     * If there are any layers which require mouse interaction as per [Layer.unlocksMouse], this method will open an
     * empty screen to facilitate that.
     */
    private fun unlockMouseIfRequired() {
        val layer = layers.findLast { it.unlocksMouse } ?: return
        if (UScreen.currentScreen == null) {
            UScreen.displayScreen(OverlayInteractionScreen(layer))
        }
    }

    private fun recomputeLayersAndSpecials() {
        val comparator = makeLayerOrSpecialComparator(screenOrder)

        layersAndSpecials.clear()
        layersAndSpecials.add(SpecialBelowAllScreens)
        var prevScreen: GuiScreen? = null
        for (screen in screenOrder) {
            if (prevScreen != null) {
                layersAndSpecials.add(SpecialBetweenScreens(prevScreen, screen))
            }
            prevScreen = screen

            layersAndSpecials.add(VanillaScreenLayer(screen))
        }
        layersAndSpecials.add(SpecialAboveAllScreens)
        for (layer in layers) {
            val layerOrSpecial = LayerOrSpecial.Layer(layer)
            layersAndSpecials.add(layersAndSpecials.indexOfLast { comparator.compare(it, layerOrSpecial) <= 0 } + 1, layerOrSpecial)
        }

        assert(layersAndSpecials.isSorted(comparator))

        layers.clear()
        for (layerOrSpecial in layersAndSpecials) {
            if (layerOrSpecial is LayerOrSpecial.Layer) {
                layers.add(layerOrSpecial.layer as LayerImpl)
            }
        }
    }

    private fun makeLayerOrSpecialComparator(screenOrder: List<GuiScreen>): Comparator<LayerOrSpecial> {
        // Ordering with two screen A and B, where B is in the foreground / draw on top of A:
        //   BelowScreen
        //   SpecialBelowAllScreens
        //     BelowScreenContent(A)
        //     VanillaScreenLayer(A)
        //     AboveScreenContent(A)
        //     SpecialBetweenScreens(A, B)
        //     BelowScreenContent(B)
        //     VanillaScreenLayer(B)
        //     AboveScreenContent(B)
        //   SpecialAboveAllScreens
        //   AboveScreen
        //   Modal
        //   Notifications
        //   Highest
        return compareBy { layerOrSpecial ->
            when (layerOrSpecial) {
                is LayerOrSpecial.Layer -> {
                    when (val priority = layerOrSpecial.layer.priority) {
                        is LayerPriority.BelowScreen -> Int.MIN_VALUE + 1
                        is LayerPriority.BelowScreenContent -> screenOrder.indexOf(priority.screen) * 4 + 0
                        is LayerPriority.AboveScreenContent -> screenOrder.indexOf(priority.screen) * 4 + 2
                        LayerPriority.AboveScreen -> Int.MAX_VALUE - 4
                        LayerPriority.Modal -> Int.MAX_VALUE - 3
                        LayerPriority.Notifications -> Int.MAX_VALUE - 2
                        LayerPriority.Highest -> Int.MAX_VALUE - 1
                    }
                }
                SpecialBelowAllScreens -> Int.MIN_VALUE + 2
                is SpecialBetweenScreens -> screenOrder.indexOf(layerOrSpecial.before) * 4 + 3
                is VanillaScreenLayer -> screenOrder.indexOf(layerOrSpecial.screen) * 4 + 1
                SpecialAboveAllScreens -> Int.MAX_VALUE - 5
            }
        }
    }

    /**
     * Determines which layers get to see the true mouse position (layers below the hovered layer get a fake one so they
     * do not show any hover effects).
     */
    private fun computeLayersWithTrueMousePos(screenWithBackground: GuiScreen?) {
        val hovered = getHoveredLayer()
        val hoveredIndex = if (hovered != null) layersAndSpecials.indexOf(LayerOrSpecial.Layer(hovered)) else -1
        val backgroundIndex = layersAndSpecials.indexOfFirst { it is SpecialBetweenScreens && it.after == screenWithBackground }
        val belowAllScreensIndex = layersAndSpecials.indexOfFirst { it is SpecialBelowAllScreens }
        val topIndex = max(max(hoveredIndex, backgroundIndex), max(belowAllScreensIndex, 0))
        layersWithTrueMousePos = layersAndSpecials.asSequence().drop(topIndex).toSet()
    }

    /**
     * Checks if there are any newly focused components on any of the layers and if so, unfocuses all other layers.
     */
    private fun propagateFocus() {
        val foci = layers.mapNotNull { layer -> layer.window.focusedComponent?.let { layer to it } }
        focus =
            when {
                foci == listOfNotNull(focus) -> return // unchanged
                foci.isEmpty() -> null // nothing focused
                else -> foci.findLast { it != focus } // something focused, find the new focus
            }

        val (newLayer, _) = focus ?: return // if there's no focus, there's nothing to reset either

        for (layer in layersAndSpecials) {
            when (layer) {
                is LayerOrSpecial.Layer -> if (layer.layer != newLayer) layer.layer.window.unfocus()
                is VanillaScreenLayer -> layer.unfocus()
                SpecialAboveAllScreens, SpecialBelowAllScreens, is SpecialBetweenScreens -> {}
            }
        }
    }

    private fun handleDraw(drawContext: UDrawContext, vararg priority: LayerPriority) {
        //#if MC >= 26.2
        //$$ val hideGui = mc.gui.hud.isHidden && UScreen.currentScreen == null
        //#else
        val hideGui = mc.gameSettings.hideGUI && UScreen.currentScreen == null
        //#endif

        fun drawLayer(matrixStack: UMatrixStack, layer: Layer) {
            val layerMatrixStack =
                if (hideGui && layer.respectsHideGuiSetting || !layer.rendered) {
                    matrixStack.fork().also {
                        it.translate(FAKE_MOUSE_POS, FAKE_MOUSE_POS, 0.0)
                    }
                } else {
                    matrixStack
                }

            if (LayerOrSpecial.Layer(layer) in layersWithTrueMousePos) {
                layer.window.draw(layerMatrixStack)
            } else {
                withFakeMousePos {
                    layer.window.draw(layerMatrixStack)
                }
            }
        }

        for (layer in layers.filter { it.priority in priority }) {
            //#if MC>=12106
            //$$ AdvancedDrawContext.drawImmediate(drawContext.mc) { matrixStack ->
            //$$     drawLayer(matrixStack, layer)
            //$$ }
            //#else
            drawLayer(drawContext.matrixStack, layer)
            //#endif
        }

        propagateFocus()
    }

    private val clickedLayers = mutableSetOf<Pair<LayerOrSpecial, /*button*/ Int>>()

    private fun handleClick(event: GuiClickEvent, vararg priority: LayerPriority) {
        for (layer in layers.filter { it.priority in priority }.asReversed()) {
            var consumed = true
            val finalHandler: UIComponent.(UIClickEvent) -> Unit = {
                if (!it.propagationStopped && it.target.isPassThrough()) {
                    consumed = false
                }
            }

            clickedLayers.add(Pair(LayerOrSpecial.Layer(layer), event.button))

            layer.window.mouseClickListeners.add(finalHandler)
            layer.window.mouseClick(event.mouseX, event.mouseY, event.button)
            layer.window.mouseClickListeners.remove(finalHandler)

            if (consumed) {
                event.isCancelled = true
                break
            }
        }
        propagateFocus()
    }

    private fun handleMouseRelease(button: Int) {
        for (layer in layers.asReversed()) {
            if (clickedLayers.remove(Pair(LayerOrSpecial.Layer(layer), button))) {
                layer.window.mouseRelease()
            }
        }
    }

    private fun fireSyntheticMouseRelease() {
        val affected = clickedLayers.filter { it.first !in layersWithTrueMousePos }
        for (entry in affected) {
            val (layer, button) = entry
            // Something in this layer has been clicked, but then a new layer opened up and stole the focus.
            // Since we're about to supply a vastly different mouse position to this layer during draw, we need to first
            // send it a synthetic `mouseRelease`, otherwise it'll act as if the user click-dragged from wherever the
            // mouse was last frame all the way to our fake position.
            if (layer is LayerOrSpecial.Layer) {
                layer.layer.window.mouseRelease()
            } else if (layer is VanillaScreenLayer) {
                Events.ignoreMouseReleaseEvent = true
                try {
                    val screen = layer.screen
                    //#if MC>=12109
                    //$$ screen?.mouseReleased(Click(UMouse.Scaled.x, UMouse.Scaled.y, MouseInput(button, 0)))
                    //#elseif MC>=11600
                    //$$ screen?.mouseReleased(UMouse.Scaled.x, UMouse.Scaled.y, button)
                    //#else
                    (screen as GuiScreenAccessor?)?.invokeMouseReleased(UMouse.Scaled.x.toInt(), UMouse.Scaled.y.toInt(), button)
                    //#endif
                } finally {
                    Events.ignoreMouseReleaseEvent = false
                }
            }
            clickedLayers.remove(entry)
        }
    }

    private fun handleKey(event: GuiKeyTypedEvent, vararg priority: LayerPriority) {
        for (layer in layers.filter { it.priority in priority }.asReversed()) {
            layer.passThroughEvent = false

            layer.window.keyType(event.typedChar, event.keyCode)

            if (!layer.passThroughEvent) {
                event.isCancelled = true
                break
            }
        }
    }

    private fun handleScroll(event: MouseScrollEvent, vararg priority: LayerPriority) {
        for (layer in layers.filter { it.priority in priority }.asReversed()) {
            var consumed = true
            val finalHandler: UIComponent.(UIScrollEvent) -> Unit = {
                if (!it.propagationStopped && it.target.isPassThrough()) {
                    consumed = false
                }
            }

            layer.window.mouseScrollListeners.add(finalHandler)
            layer.window.mouseScroll(event.amount.coerceIn(-1.0, 1.0))
            layer.window.mouseScrollListeners.remove(finalHandler)

            if (consumed) {
                event.isCancelled = true
                break
            }
        }
    }

    private fun Layer.isAnythingHovered(mouseX: Float, mouseY: Float): Boolean {
        val hovered =
            window.hoveredFloatingComponent?.hitTest(mouseX, mouseY)
                ?: window.hitTest(mouseX, mouseY)
        return !hovered.isPassThrough()
    }

    private fun UIComponent.isPassThrough(): Boolean {
        return when (this) {
            is Window -> true
            is UIContainer -> parent.isPassThrough() // these have a default alpha of 1 but they don't render anything
            is UIBlock -> getColor().alpha == 0 && parent.isPassThrough()
            else -> false
        }
    }

    private inline fun withFakeMousePos(block: () -> Unit) {
        val orgX = UMouse.Raw.x
        val orgY = UMouse.Raw.y
        GlobalMouseOverride.set(FAKE_MOUSE_POS, FAKE_MOUSE_POS)
        block()
        GlobalMouseOverride.set(orgX, orgY)
    }

    /**
     * Runs the given [block] with the real mouse position restored globally, even if it has been modified by [Events].
     * The block can access the true mouse position as required via [UMouse].
     */
    @JvmStatic
    fun withRealMousePos(block: () -> Unit) = Events.withRealMousePos(block)

    @JvmStatic
    fun isOverridingMousePos() = Events.isOverridingMousePos()

    private object Events {
        var registered = false

        fun register() {
            registered = true
            Essential.EVENT_BUS.register(this)
        }

        // Note: only safe to unregister at end of frame, not in the middle of one (because mouse pos needs to be restored)
        fun unregister() {
            registered = false
            Essential.EVENT_BUS.unregister(this)
            focus = null
            // Clear all references to any Screen objects, so they can be GCed
            screenOrder = emptyList()
            observedScreenOrder.clear()
            observedTopMostScreenWithBackground = null
            recomputeLayersAndSpecials()
        }

        private val observedScreenOrder = mutableListOf<GuiScreen>()
        private fun updateScreenOrder() {
            if (screenOrder != observedScreenOrder) {
                screenOrder = observedScreenOrder.toList()
                recomputeLayersAndSpecials()
            }
            observedScreenOrder.clear()
        }

        private var observedTopMostScreenWithBackground: GuiScreen? = null

        private var originalMousePos: Pair<Double, Double>? = null
        private var originalMousePosEvent: Pair<Int, Int>? = null

        fun isOverridingMousePos() = originalMousePos != null

        fun withRealMousePos(block: () -> Unit) {
            originalMousePos?.let { (x, y) ->
                val inX = UMouse.Raw.x
                val inY = UMouse.Raw.y
                GlobalMouseOverride.set(x, y)
                block()
                GlobalMouseOverride.set(inX, inY)
            } ?: block()
        }

        private fun firstDraw(event: GuiDrawScreenEvent) {
            cleanupLayers()
            updateScreenOrder()
            computeLayersWithTrueMousePos(observedTopMostScreenWithBackground.also { observedTopMostScreenWithBackground = null })
            fireSyntheticMouseRelease()

            handleDraw(event.drawContext, LayerPriority.BelowScreen)

            // We're about to draw parts of the screen, so now's the time to suppress the mouse position if we need to
            if (SpecialBelowAllScreens !in layersWithTrueMousePos) {
                originalMousePos = Pair(UMouse.Raw.x, UMouse.Raw.y)
                GlobalMouseOverride.set(FAKE_MOUSE_POS, FAKE_MOUSE_POS)
                originalMousePosEvent = Pair(event.mouseX, event.mouseY)
                event.mouseX = FAKE_MOUSE_POS.toInt()
                event.mouseY = FAKE_MOUSE_POS.toInt()
            }
        }

        private fun preDraw(event: GuiDrawScreenEvent) {
            // Done drawing the screen background, restore mouse position
            originalMousePos?.let { (x, y) -> GlobalMouseOverride.set(x, y) }
            originalMousePos = null
            // Note: When a screen is drawn behind another screen (so outer.render calls inner.render), we'll see the
            //       event from the inner screen first, but we still also need to restore the mouseX/Y passed to the
            //       outer screen, so we must not reset `originalMousePosEvent` until `finalDraw`.
            //       (This is not the case for `originalMousePos`, since `GlobalMouseOverride` is global and so if it's
            //        restored in the inner screen, the outer screen will naturally also see the restored values.)
            originalMousePosEvent?.let { (x, y) -> event.mouseX = x; event.mouseY = y }

            handleDraw(event.drawContext, LayerPriority.BelowScreenContent(event.screen))

            // We're about to draw the screen content, so now's the time to suppress the mouse position if we need to
            if (VanillaScreenLayer(event.screen) !in layersWithTrueMousePos) {
                originalMousePos = Pair(UMouse.Raw.x, UMouse.Raw.y)
                GlobalMouseOverride.set(FAKE_MOUSE_POS, FAKE_MOUSE_POS)
                event.mouseX = FAKE_MOUSE_POS.toInt()
                event.mouseY = FAKE_MOUSE_POS.toInt()
            }
        }

        private fun postDraw(event: GuiDrawScreenEvent) {
            observedScreenOrder.add(event.screen)

            // Done drawing the screen content, restore mouse position
            originalMousePos?.let { (x, y) -> GlobalMouseOverride.set(x, y) }
            originalMousePos = null

            handleDraw(event.drawContext, LayerPriority.AboveScreenContent(event.screen))

            // We're about to draw modded content, so now's the time to suppress the mouse position if we need to
            val nextScreen = screenOrder.getOrNull(screenOrder.indexOf(event.screen) + 1)
            val nextLayer = if (nextScreen != null) {
                SpecialBetweenScreens(event.screen, nextScreen)
            } else {
                SpecialAboveAllScreens
            }
            if (nextLayer !in layersWithTrueMousePos) {
                originalMousePos = Pair(UMouse.Raw.x, UMouse.Raw.y)
                GlobalMouseOverride.set(FAKE_MOUSE_POS, FAKE_MOUSE_POS)
            }
        }

        private fun finalDraw(event: GuiDrawScreenEvent) {
            // Done with rendering, restore the real mouse position
            originalMousePos?.let { (x, y) -> GlobalMouseOverride.set(x, y) }
            originalMousePos = null
            originalMousePosEvent = null

            handleDraw(event.drawContext, LayerPriority.AboveScreen, *NON_SCREEN_LAYERS)

            if (layers.isEmpty()) {
                unregister()
            }
        }

        private fun nonScreenDraw(drawContext: UDrawContext) {
            cleanupLayers()
            updateScreenOrder()
            observedTopMostScreenWithBackground = null
            layersWithTrueMousePos = emptySet() // mouse is captured, no one gets to see it

            // TODO could add more specific events in the HUD rendering code, but we only use Modal and above atm anyway
            handleDraw(drawContext, LayerPriority.BelowScreen, LayerPriority.AboveScreen, *NON_SCREEN_LAYERS)

            unlockMouseIfRequired()

            if (layers.isEmpty()) {
                unregister()
            }
        }

        private fun flushVanillaBuffers(drawContext: UDrawContext) {
            // We need to flush the vanilla vertex consumers so that our rendering doesn't mess up their state.
            // Minecraft already flushes these at the *end* of GUI rendering, but we inject somewhere in the "middle",
            // so that buffer can be full, but not flushed yet.
            // `bufferSource` / `entityVertexConsumers` is the only one that we need to flush at the time of writing
            // this comment, since it's the only one that Minecraft uses during GUI rendering.
            // As of 1.20, the DrawContext which MC passes around has an explicit `draw` method, so we can simply call
            // that one. This is also required with ImmediatelyFast as of 1.21.2 because it uses a custom vertex
            // consumer provider separate from the vanilla global one.

            //#if MC>=12106
            //$$ // Minecraft's new gui renderer no longer uses immediate rendering
            //#elseif MC>=12000
            //$$ drawContext.mc.draw()
            //#elseif MC>=11600
            //$$ Minecraft.getInstance().getRenderTypeBuffers().getBufferSource().finish()
            //#endif
        }

        private var sawPriorityPreDrawEvent = true // assume good on first frame
        private var sawPriorityPostDrawEvent = true // assume good on first frame

        @Subscribe
        fun handleDraw(event: GuiDrawScreenEvent.Priority) {
            if (!event.screen.isReal()) return

            flushVanillaBuffers(event.drawContext)
            if (event.isPre) {
                firstDraw(event)
                sawPriorityPreDrawEvent = true
            } else {
                finalDraw(event)
                sawPriorityPostDrawEvent = true
            }
        }

        @Subscribe
        fun handleDraw(event: GuiDrawScreenEvent) {
            flushVanillaBuffers(event.drawContext)

            if (event.isPre) {
                if (event.screen.isReal()) {
                    if (!sawPriorityPreDrawEvent) {
                        firstDraw(event)
                    }
                    sawPriorityPreDrawEvent = false
                }

                preDraw(event)
            } else {
                postDraw(event)

                if (event.screen.isReal()) {
                    if (!sawPriorityPostDrawEvent) {
                        finalDraw(event)
                    }
                    sawPriorityPostDrawEvent = false
                }
            }
        }

        @Subscribe
        fun handleBackground(event: GuiDrawScreenEvent.Background) {
            observedTopMostScreenWithBackground = event.screen
        }

        @Subscribe
        fun handleNonScreenDraw(event: RenderTickEvent) {
            if (event.isPre) {
                return
            }
            val drawContext = event.drawContext!!

            if (event.isLoadingScreen) {
                flushVanillaBuffers(drawContext)
                layersWithTrueMousePos = emptySet() // the loading screen isn't a real screen and can't handle input
                // The loading screen is drawn on top of whatever screen is active, so the actual active screen isn't
                // visible, so we don't want to render screen-related layers either.
                handleDraw(drawContext, *NON_SCREEN_LAYERS)
                return
            }

            if (UScreen.currentScreen != null) {
                return // more specific GuiDrawScreenEvents will be emitted
            }

            flushVanillaBuffers(drawContext)
            nonScreenDraw(drawContext)
        }

        @Subscribe
        fun firstClick(event: GuiClickEvent.Priority) {
            if (!event.screen.isReal()) return

            handleClick(event, LayerPriority.AboveScreen, *NON_SCREEN_LAYERS)

            if (!event.isCancelled) {
                clickedLayers.add(Pair(VanillaScreenLayer(event.screen), event.button))
            }
        }

        @Subscribe
        fun preClick(event: GuiClickEvent) {
            handleClick(event, LayerPriority.AboveScreenContent(event.screen))

            // TODO don't yet have (nor need) a post-click event
            if (!event.isCancelled) {
                handleClick(event, LayerPriority.BelowScreenContent(event.screen))
            }

            // TODO don't yet have (nor need) a priority post-click event
            if (!event.isCancelled && event.screen.isReal()) {
                handleClick(event, LayerPriority.BelowScreen)
            }
        }

        var ignoreMouseReleaseEvent = false
        @Subscribe
        fun mouseRelease(event: GuiMouseReleaseEvent) {
            if (ignoreMouseReleaseEvent) return

            handleMouseRelease(event.button)
        }

        @Subscribe
        fun firstKey(event: GuiKeyTypedEvent) {
            // TODO don't yet have (nor need) a priority pre-type event
            if (event.screen.isReal()) {
                handleKey(event, LayerPriority.AboveScreen, *NON_SCREEN_LAYERS)
                if (event.isCancelled) return
            }

            handleKey(event, LayerPriority.AboveScreenContent(event.screen))

            // TODO don't yet have (nor need) a post-type event
            if (!event.isCancelled) {
                handleKey(event, LayerPriority.BelowScreenContent(event.screen))
            }

            // TODO don't yet have (nor need) a priority post-type event
            if (!event.isCancelled && event.screen.isReal()) {
                handleKey(event, LayerPriority.BelowScreen)
            }
        }

        @Subscribe
        fun firstScroll(event: MouseScrollEvent) {
            // TODO don't yet have (nor need) a priority pre-scroll event
            if (event.screen.isReal()) {
                handleScroll(event, LayerPriority.AboveScreen, *NON_SCREEN_LAYERS)
                if (event.isCancelled) return
            }

            val screen = event.screen
            if (screen != null) {
                handleScroll(event, LayerPriority.AboveScreenContent(screen))

                // TODO don't yet have (nor need) a post-scroll event
                if (!event.isCancelled) {
                    handleScroll(event, LayerPriority.BelowScreenContent(screen))
                }
            }

            // TODO don't yet have (nor need) a priority post-scroll event
            if (!event.isCancelled && event.screen.isReal()) {
                handleScroll(event, LayerPriority.BelowScreen)
            }
        }

        private fun GuiScreen?.isReal(): Boolean {
            return this != null && this == UScreen.currentScreen
        }

        private val NON_SCREEN_LAYERS = arrayOf(
            LayerPriority.Modal,
            LayerPriority.Notifications,
            LayerPriority.Highest,
        )
    }

    private class LayerImpl(override val priority: LayerPriority) : Layer {
        override val window: Window = Window(ElementaVersion.V10)
        override var rendered: Boolean = true
        override var respectsHideGuiSetting: Boolean = true
        override var unlocksMouse: Boolean = priority == LayerPriority.Modal
        override var pausesGame: Boolean = true
        override var allowPlayerInput: Boolean = false

        /** Internal. For OverlayManagerImpl only. */
        var passThroughEvent = false
        init {
            window.onKeyType { _, _ -> passThroughEvent = true }
        }
    }

    sealed interface LayerOrSpecial {
        data class Layer(val layer: gg.essential.gui.overlay.Layer) : LayerOrSpecial

        sealed class Special : LayerOrSpecial
    }

    data object SpecialBelowAllScreens : LayerOrSpecial.Special()
    data class SpecialBetweenScreens(val before: GuiScreen, val after: GuiScreen) : LayerOrSpecial.Special()
    data object SpecialAboveAllScreens : LayerOrSpecial.Special()

    data class VanillaScreenLayer(val screen: GuiScreen) : LayerOrSpecial.Special() {
        fun unfocus() {
            //#if MC>=11600
            //$$ val focused = screen.listener as Widget? ?: return // nothing in focus, nothing to do
            //$$ if (!focused.isFocused) return // the thing in focus isn't actually in focus, nothing to do
            //#if MC<=11903
            //$$ if (focused.changeFocus(false)) return // the thing in focus refuses to unfocus, nothing we can do
            //#endif
            //$$ screen.listener = null
            //#else
            // There's no way to get the focused text field on old versions, so we have a mixin for it instead.
            // See: Mixin_UnfocusTextFieldWhileOverlayHasFocus
            //#endif
        }
    }

    internal class OverlayInteractionScreen(val layer: Layer) : UScreen(), EssentialScreenMayAllowPlayerInput {
        // The unlocksMouse layers are the only ones that need this screen to be opened
        private fun relevantLayers() = layers.filter { it.unlocksMouse }

        override fun allowsPlayerInput() = relevantLayers().all { it.allowPlayerInput }

        override fun onKeyPressed(keyCode: Int, typedChar: Char, modifiers: UKeyboard.Modifiers?) {
            // no-op to suppress Esc key
        }

        override fun doesGuiPauseGame(): Boolean {
            return relevantLayers().any { it.pausesGame }
        }

        override fun onTick() {
            // Allow the player to move while the screen is open if all unlocksMouse layers allow it.
            //#if MC>=12000
            //$$ // Now handled by Mixin_AllowMovementDuringScreens
            //#elseif MC>=11602
            //$$ this.passEvents = allowsPlayerInput()
            //#else
            this.allowUserInput = allowsPlayerInput()
            //#endif

            // This screen may be active even when its corresponding layer has already been removed.
            // This can e.g. happen if this screen gets replaced by another screen, then the layer is destroyed, and
            // then the other screen restores its previous screen (i.e. this screen).
            if (layer !in layers) {
                // In such cases, we simply close this screen. If there's another modal active, it'll re-open a new one.
                displayScreen(null)
            }
            super.onTick()
        }
    }

    private object GlobalMouseOverride {
        //#if MC>=11600
        //$$ fun set(mouseX: Double, mouseY: Double) {
        //$$     val mouse = mc.mouseHelper as MouseHelperAccessor
        //$$     mouse.setMouseX(mouseX)
        //$$     mouse.setMouseY(mouseY)
        //$$ }
        //#else
        private val cls = Mouse::class.java
        private val lookup = MethodHandles.lookup()
        private val xField = lookup.unreflectSetter(cls.getDeclaredField("x").apply { isAccessible = true })
        private val yField = lookup.unreflectSetter(cls.getDeclaredField("y").apply { isAccessible = true })
        private val eventXField = lookup.unreflectSetter(cls.getDeclaredField("event_x").apply { isAccessible = true })
        private val eventYField = lookup.unreflectSetter(cls.getDeclaredField("event_y").apply { isAccessible = true })

        fun set(mouseX: Double, mouseY: Double) {
            val trueX = mouseX.toInt()
            val trueY = UResolution.windowHeight - mouseY.toInt() - 1
            xField.invokeExact(trueX)
            yField.invokeExact(trueY)
            eventXField.invokeExact(trueX)
            eventYField.invokeExact(trueY)
        }
        //#endif
    }
}
