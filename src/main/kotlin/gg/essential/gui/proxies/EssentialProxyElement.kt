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
package gg.essential.gui.proxies

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.util.Tag
import gg.essential.gui.util.addTag
import gg.essential.gui.util.getTag
import gg.essential.gui.util.hoveredStateV2
import gg.essential.gui.util.isInComponentTree
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.util.UDrawContext
import gg.essential.util.findChildOfTypeOrNull
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton

//#if MC>=12109
//$$ import net.minecraft.client.gui.Click
//$$ import net.minecraft.client.input.MouseInput
//#endif

//#if MC>=12000
//$$ import net.minecraft.client.gui.DrawContext
//#endif

//#if MC>=11600
//$$ import com.mojang.blaze3d.matrix.MatrixStack
//$$ import gg.essential.util.textLiteral
//#endif

abstract class EssentialProxyElement<T : UIComponent>(
    val essentialId: String,
    initialPosID: Int,
    private val clazz: Class<T>,
    private val expectedText: String = "<essential_$essentialId>",
) : GuiButton(
    // it is important that the initial x & y here, driven by initialPosID, remain consistent in the future,
    // as these values directly inform fancy menu button identification
    //#if MC>=11903
    //$$ initialPosID * 2, initialPosID, 20, 20, textLiteral(expectedText), {}, DEFAULT_NARRATION_SUPPLIER
    //#elseif MC>=11600
    //$$ initialPosID * 2, initialPosID, 20, 20, textLiteral(expectedText), {}
    //#else
    191852507 /* random starting id offset to avoid conflicts */ + initialPosID , initialPosID * 2, initialPosID, 20, 20, expectedText
    //#endif
) {

    protected val drawBehaviourState: MutableState<ProxyDrawBehaviour> = mutableStateOf(ProxyDrawBehaviour.ESSENTIAL_DRAWS)
    protected val drawBehaviour: ProxyDrawBehaviour get() = drawBehaviourState.getUntracked()

    // behaviour always defaults to essential control, and once changed shouldn't revert
    private var proxyInControl: Boolean = false

    private var positionStateSetByEssential: ProxyPositionState? = null

    private var essentialContainer: UIComponent? = null

    private var essentialContainerMountingState: MutableState<State<Boolean>>? = null

    val essentialComponentState: MutableState<T?> = mutableStateOf(null)
    val essentialComponent: T? get() = essentialComponentState.getUntracked()

    fun acceptNewEssentialContainer(container: UIComponent, mountingControl: MutableState<State<Boolean>>) {
        // reset these values
        proxyInControl = false
        positionStateSetByEssential = null

        // new component container
        essentialContainer = container
        essentialContainerMountingState = mountingControl

        mountingControl.set(State { drawBehaviourState() != ProxyDrawBehaviour.PROXY_DRAWS })
    }

    fun initAfterInitialLayout() {
        if (essentialContainer == null) {
            throw IllegalStateException("Proxy element for $essentialId was never bound. " +
                    "Ensure `mountWithProxy` is being called unconditionally and/or the hard-coded label-to-id " +
                    "mapping in `ScreenWithProxiesHandler` is feature-flagged correctly.")
        }

        // Apply position of Essential component to proxy
        updateProxyStateWhileNotInControl()

        // Detach from Essential component again (a dummy one is used for initial layout)
        // Intentionally not resetting `positionStateSetByEssential` so we can recognize when a mod moves the button
        // during screen initialization.
        essentialContainer = null
        essentialContainerMountingState = null
    }

    private var needsToDrawOnce = true

    //#if MC>=12004
    //$$ fun renderOverride(context: DrawContext, mouseX: Int, mouseY: Int, partialTicks: Float) {
    //#elseif MC>=12000
    //$$ override fun render(context: DrawContext, mouseX: Int, mouseY: Int, partialTicks: Float) {
    //#elseif MC>=11600
    //$$ override fun render(matrixStack: MatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
    //#elseif MC>=11200
    override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
    //#else
    //$$ override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int ) {
    //#endif

        // find the essential component, if it exists
        essentialComponentState.set(essentialContainer?.findChildOfTypeOrNull(clazz, true))

        // if the essential component is disabled/hidden then the proxy button should not render
        // we also want to avoid processing the draw / control behaviour in this case as this condition also disables other proxy button methods
        if (essentialComponentExistsButInactive) return

        // fancy menu in v3+ only modifies the button positions on render, so lets call super.render() just once after
        // our proxy has received it's position state
        if (needsToDrawOnce && positionStateSetByEssential != null) {
            needsToDrawOnce = false

            // we will hide these draws by moving them way outside the screen, or using the scissor stack in 1.20+
            //#if MC>=12000
            //$$ context.enableScissor(0, 0, 0, 0)
            //$$ super.render(context, mouseX, mouseY, partialTicks)
            //$$ context.disableScissor()
            //#elseif MC>=11600
            //$$ matrixStack.push()
            //$$ matrixStack.translate(1000000.0, 1000000.0, 0.0)
            //$$ super.render(matrixStack, mouseX, mouseY, partialTicks)
            //$$ matrixStack.pop()
            //#elseif MC>=11200
            UMatrixStack().apply { translate(1000000.0, 1000000.0, 0.0) }.runWithGlobalState {
                super.drawButton(mc, mouseX, mouseY, partialTicks)
            }
            //#else
            //$$ UMatrixStack().apply { translate(1000000.0, 1000000.0, 0.0) }.runWithGlobalState {
            //$$     super.drawButton(mc, mouseX, mouseY)
            //$$ }
            //#endif
        }

        updateProxyState() // MUST run, triggers control behaviour switching

        drawBehaviourState.set(drawBehaviourFromCurrentState()) // update draw behaviour

        if (!visible || !drawBehaviour.proxyDrawEnabled) return

        // circumvent the mouse positions being offset when an essential component is hovered
        // override conditionally with our components hover state
        val forcedHover = drawBehaviour == ProxyDrawBehaviour.ESSENTIAL_DRAWS_TEXT_PROXY_DRAWS_BACKGROUND && essentialComponentHovered
        val newMouseX = if (forcedHover) x else mouseX
        val newMouseY = if (forcedHover) y else mouseY

        val vanillaRender = { cont: UDrawContext ->
            //#if MC>=12000
            //$$ super.render(cont.mc, newMouseX, newMouseY, partialTicks)
            //#elseif MC>=11600
            //$$ super.render(matrixStack, newMouseX, newMouseY, partialTicks)
            //#elseif MC>=11200
            super.drawButton(mc, newMouseX, newMouseY, partialTicks)
            //#else
            //$$ super.drawButton(mc, newMouseX, newMouseY)
            //#endif
            UGraphics.color4f(1f, 1f, 1f, 1f) // global color not reset by vanilla render
        }

        //#if MC>=12000
        //$$ val uDC = UDrawContext(context, UMatrixStack(context.matrices))
        //#elseif MC>=11600
        //$$ val uDC = UDrawContext(UMatrixStack(matrixStack))
        //#elseif MC<11300
        val uDC = UDrawContext(UMatrixStack())
        //#endif

        handleRender(uDC, vanillaRender)
    }

    protected open fun handleRender(context: UDrawContext, vanillaRender: (UDrawContext)-> Unit) {
        vanillaRender(context)
    }

    //#if MC>=12111
    //$$ // Note: This method is misnamed, it's actually responsible for drawing the entire content. See `ButtonWidget.Text`
    //$$ override fun drawIcon(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
    //$$     drawButton(context)
    //$$     drawLabel(context.getHoverListener(this, DrawContext.HoverType.NONE))
    //$$ }
    //#endif

    private fun updateProxyState() {
        if (!proxyInControl ) {
            if (positionStateSetByEssential?.matches(this) == false || hasProxyContentBeenModified) {
                // the proxy button has been moved or modified by another mod, so we need to control the essential one now
                proxyInControl = true
            } else {
                // update or set state to capture any changes made by the essential component
                updateProxyStateWhileNotInControl()
                return
            }
        }

        assert(proxyInControl)
        essentialComponent?.applyPositionStateFromProxy()
    }

    private fun updateProxyStateWhileNotInControl() {
        val component = essentialContainer
        if (component == null) {
            applyFallbackPositionState()
            return
        }

        component.positionState().let {
            mapOfLastKnownPositionStates[essentialId] = it
            applyPositionState(it)
            positionStateSetByEssential = it
        }
    }

    // draw behaviour common to all proxy types
    private fun drawBehaviourFromCurrentState(): ProxyDrawBehaviour {

        // for cases where essential has not attached to this proxy, we need to let the proxy do the drawing
        // happens when a third-party mod uses this button in a different screen, e.g. the fancy menu editor in v3+
        if (essentialContainer == null) {
            return ProxyDrawBehaviour.PROXY_DRAWS
        }

        // draw behaviour specific to this component type
        return drawBehaviourFromComponentState()
    }

    protected abstract fun drawBehaviourFromComponentState(): ProxyDrawBehaviour

    protected fun uIsMouseOver(): Boolean =
        //#if MC>=11904
        //$$ // FIXME remap bug: converts to property access syntax in 1.19.4 fabric which then doesn't convert correctly to forge
        //$$ isSelected()
        //#else
        @Suppress("UsePropertyAccessSyntax") // same as above, breaks preprocessor replacement when converting to forge
        isMouseOver()
        //#endif

    override fun isMouseOver(): Boolean {
        return !essentialComponentExistsButInactive
                && (essentialComponentHovered || super.isMouseOver())
    }

    // use essentialComponentExistsButInactive to disable proxy button interactions
    // these 2 methods (and isMouseOver()) are the same ones also overridden by fancy menu when it disables ("deletes") a button
    //#if MC>=11904
    //$$ override fun getNavigationPath(navigation: net.minecraft.client.gui.navigation.GuiNavigation): net.minecraft.client.gui.navigation.GuiNavigationPath? {
    //$$     return if (essentialComponentExistsButInactive) null else super.getNavigationPath(navigation)
    //$$ }
    //#endif

    //#if MC>=11600
    //#if MC>=12109
    //$$ override fun isValidClickButton(button: MouseInput): Boolean {
    //#else
    //$$ override fun isValidClickButton(button: Int): Boolean {
    //#endif
    //$$     return !essentialComponentExistsButInactive && super.isValidClickButton(button)
    //$$ }
    //#endif

    //#if MC>=12109
    //$$ override fun mouseClicked(click: Click, doubleClick: Boolean): Boolean {
    //#elseif MC>=11600
    //$$ override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
    //#else
    override fun mousePressed(mc: Minecraft, mouseX: Int, mouseY: Int): Boolean {
    //#endif
        if (!drawBehaviour.proxyActionEnabled || !uIsMouseOver() || !enabled) return false

        essentialComponent?.click() ?: return false
        return true
    }

    protected abstract fun T.click()

    private fun applyPositionState(stateSetByEssential: ProxyPositionState) {
        this.x = stateSetByEssential.x
        this.y = stateSetByEssential.y
        this.width = stateSetByEssential.width
        //#if MC>=11600
        //$$ this.height = stateSetByEssential.height //fixme this gets auto-converted to the method getHeightRealms() which acts as val, rather than the still present var height property
        //#else
        this.height = stateSetByEssential.height
        //#endif
    }

    private fun applyFallbackPositionState() {
        // apply the last know state for this button, if it exists
        // this is only relevant when the proxy is created and our component doesn't attach, such as in the Fancy Menu editor
        mapOfLastKnownPositionStates[essentialId]?.let(::applyPositionState)
    }

    private fun UIComponent.positionState(): ProxyPositionState = ProxyPositionState(
        x = getLeft().toInt(),
        y = getTop().toInt(),
        width = getWidth().toInt(),
        height = getHeight().toInt(),
    )

    private fun T.applyPositionStateFromProxy(){

        // test if we have already modified this component before, as the components may change depending on the layout
        if (getTag<ModifiedConstraintsTag>() != null) return
        addTag(ModifiedConstraintsTag)

        isFloating = true // ensure that component is not constrained by the parent

        constrain {
            x = basicXConstraint { this@EssentialProxyElement.x.toFloat() }
            y = basicYConstraint { this@EssentialProxyElement.y.toFloat() }
            width = basicWidthConstraint { this@EssentialProxyElement.width.toFloat() }
            height = basicHeightConstraint { this@EssentialProxyElement.height.toFloat() }
        }
    }

    /**
     * true if the proxy button has been hidden by another mod
     * */
    protected val hasProxyBeenHidden: Boolean
        get() = !visible
            // fancy menu v3+ cancels button rendering via a mixin and custom flag
            // this flag also modifies the button to be invalid for clicks when deleted, which
            // means we can read isValidClickButton() to avoid having to depend on fancy menu directly
            //#if MC>=12109
            //$$ || !isValidClickButton(MouseInput(0, 0))
            //#elseif MC>=11800
            //$$ || !isValidClickButton(0)
            //#endif

    /**
     * true if the vanilla content has been modified in a way by another mod
     * e.g. text changed, disabled, or visibility affected
     * */
    protected val hasProxyContentBeenModified: Boolean
        get() = vanillaTextAccess != expectedText || hasProxyBeenHidden || !enabled

    protected var vanillaTextAccess: String
        //#if MC>=11600
        //$$ get() = this.message.string
        //$$ set(value) { this.message = textLiteral(value) }
        //#else
        get() = this.displayString
        set(value) { this.displayString = value }
        //#endif

    private val essentialComponentHovered: Boolean
        get() = essentialComponent?.hoveredStateV2()?.getUntracked() ?: false

    // used to disable proxy interaction/rendering when the essential component is hidden/disabled by something other than the proxy
    private val essentialComponentExistsButInactive get() =
        essentialContainerMountingState?.getUntracked()?.getUntracked() == true // ignore this if we have purposefully hidden the component
                && essentialComponent?.isInComponentTree() == false

    protected data class ProxyPositionState(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        fun matches(proxy: GuiButton): Boolean =
            x == proxy.x && y == proxy.y && width == proxy.width && height == proxy.height

    }

    protected enum class ProxyDrawBehaviour {
        ESSENTIAL_DRAWS,
        ESSENTIAL_DRAWS_TEXT_PROXY_DRAWS_BACKGROUND,
        PROXY_DRAWS;

        val proxyDrawEnabled: Boolean
            get() = this != ESSENTIAL_DRAWS

        val proxyActionEnabled: Boolean
            get() = this == PROXY_DRAWS

    }

    private object ModifiedConstraintsTag : Tag

    companion object {

        // store the last known state of our buttons for if the proxies ever get recreated without them (such as within the Fancy Menu editor)
        private val mapOfLastKnownPositionStates = mutableMapOf<String, ProxyPositionState>()
    }
}
