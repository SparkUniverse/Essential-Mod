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
package gg.essential.gui.common.modal

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.Window
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.MenuButton
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.StyledButton
import gg.essential.gui.common.outlineButton
import gg.essential.gui.common.textStyle
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.WrappedTextBuilder
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedMaxHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillHeight
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.outline
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.tag
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.util.Focusable
import gg.essential.gui.util.Tag
import gg.essential.gui.util.findChildrenByTag
import gg.essential.gui.util.focusable
import gg.essential.gui.util.simulateLeftClick
import gg.essential.universal.UKeyboard
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import kotlinx.coroutines.launch
import java.awt.Color

/**
 * A base-modal with some design defaults.
 * Intended to be the replacement for [EssentialModal].
 */
abstract class EssentialModal2(
    modalManager: ModalManager,
    private val requiresButtonPress: Boolean = false,
) : Modal(modalManager) {
    /**
     * Listens for keyboard navigation events on the modal's [Window].
     *
     * Only the component which holds the [Window]'s focus receives key events, so if this listener is being used,
     * that means that no component currently holds that focus.
     */
    private val keyEventListener: UIComponent.(Char, Int) -> Unit = keyListener@{ _, keyCode ->
        val window = Window.of(this)

        when (keyCode) {
            /** Passing focus from one element to another is done via [gg.essential.gui.util.setupKeyboardNavigation]. */
            UKeyboard.KEY_TAB -> {
                val focusable = window.findChildrenByTag<Focusable>(recursive = true) { !it.disabled.getUntracked() }

                val nextComponent = when {
                    UKeyboard.isShiftKeyDown() -> focusable.lastOrNull()
                    else -> focusable.firstOrNull()
                }

                nextComponent?.grabWindowFocus()
            }

            UKeyboard.KEY_ENTER, UKeyboard.KEY_NUMPADENTER -> {
                val primaryAction = window.findChildrenByTag<PrimaryAction>(recursive = true).singleOrNull()

                // The simulated left-click event may cause the component's hierarchy to change.
                // In order to prevent a CME, we need to use `enqueueRenderOperation`.
                Window.enqueueRenderOperation {
                    primaryAction?.simulateLeftClick()
                }
            }
        }
    }

    init {
        @Suppress("LeakingThis")
        platform.dismissModalOnScreenChange(this, ::close)
    }

    override fun onOpen() {
        super.onOpen()

        Window.of(this).onKeyType(keyEventListener)
    }

    override fun onClose() {
        // The location of this does not make a difference with the current implementation, but we should
        // close things in the reverse order in which they were opened.
        // i.e. We added our key listener after `Modal` added theirs, so we remove ours before they remove theirs.
        Window.of(this).keyTypedListeners.remove(keyEventListener)

        super.onClose()
    }

    override fun LayoutScope.layoutModal() {
        box(
            Modifier
                .childBasedHeight(16f)
                .color(EssentialPalette.MODAL_BACKGROUND)
                .outline(EssentialPalette.BUTTON_HIGHLIGHT, 1f)
        ) {
            column {
                // The spacing between the top of the modal to the content, and the bottom of the modal to the content
                // is un-even. We need to add an extra 3 pixels at the top.
                spacer(height = 3f)
                layoutContent()
            }
        }
    }

    open fun LayoutScope.layoutContent(modifier: Modifier = Modifier) = layoutContentImpl(modifier)

    /**
     * This is basically the "default implementation" for `layoutContent`.
     * If somebody wants to override `layoutContent` to just change something on the `modifier`, they can
     * override `layoutContent` and call this.
     *
     * We can't make `layoutContent` take a `Modifier`, and make the implementation call on `super`, since you can't
     * call a superclass' implementation of an extension function. ([KT-11488](https://youtrack.jetbrains.com/issue/KT-11488/When-overriding-a-member-extension-function-cannot-call-superclass-implementation))
     */
    fun LayoutScope.layoutContentImpl(modifier: Modifier = Modifier, verticalArrangement: Arrangement = Arrangement.spacedBy(17f)) {
        lateinit var titleContainer: UIComponent
        lateinit var bodyContainer: UIComponent
        lateinit var buttonsContainer: UIComponent
        val hasTitle = mutableStateOf(true)
        val hasBody = mutableStateOf(true)
        val hasButtons = mutableStateOf(true)

        // Width = 190 (Main) + 16 * 2 (Padding)
        column(Modifier.width(222f).then(modifier), verticalArrangement) {
            if_(hasTitle) {
                titleContainer = layoutTitleContainer()
            }
            if_(hasBody) {
                box(Modifier.fillWidth().childBasedMaxHeight()) {
                    box(Modifier.fillWidth(padding = 16f)) {
                        bodyContainer = containerDontUseThisUnlessYouReallyHaveTo
                        layoutBody()
                    }
                    box(Modifier.width(16f).alignHorizontal(Alignment.End)) {
                        layoutBodyScrollBar()
                    }
                }
            }
            if_(hasButtons) {
                box(Modifier.fillWidth(padding = 16f)) {
                    buttonsContainer = containerDontUseThisUnlessYouReallyHaveTo
                    layoutButtons()
                }
            }
        }

        titleContainer.children.addObserver { _, _ -> hasTitle.set(titleContainer.children.isNotEmpty()) }
        bodyContainer.children.addObserver { _, _ -> hasBody.set(bodyContainer.children.isNotEmpty()) }
        buttonsContainer.children.addObserver { _, _ -> hasButtons.set(buttonsContainer.children.isNotEmpty()) }
        hasTitle.set(titleContainer.children.isNotEmpty())
        hasBody.set(bodyContainer.children.isNotEmpty())
        hasButtons.set(buttonsContainer.children.isNotEmpty())
    }

    /** Mainly intended for the [title] of your modal, without having to worry about most implementation details. */
    open fun LayoutScope.layoutTitle() {}

    /**
     * Default implementation for the layout of the title of your modal. Can be overriden, to allow custom modal
     * superclasses to have common title elements while still allowing [layoutTitle] to be used for just [title] lines.
     */
    open fun LayoutScope.layoutTitleContainer(): UIComponent {
        val titleContainer: UIComponent
        box(Modifier.fillWidth(padding = 16f)) {
            titleContainer = containerDontUseThisUnlessYouReallyHaveTo
            layoutTitle()
        }
        return titleContainer
    }

    /** For the actual content of your modal, e.g. the [description] text. */
    open fun LayoutScope.layoutBody() {}

    /** For the scrollbar of your modal */
    open fun LayoutScope.layoutBodyScrollBar() {}

    /**
     * Implements a scrollbar for the layoutBody
     *
     * Use the example below to implement the scrollbar
     * ```kt
     * override fun LayoutScope.layoutBodyScrollBar() = layoutBodyScrollBarImpl(scroller)
     * ```
     */
    fun LayoutScope.layoutBodyScrollBarImpl(scroller: ScrollComponent) {
        val scrollbar: UIComponent
        box(Modifier.height(scroller).width(6f).alignHorizontal(Alignment.Start(1f)).hoverScope()) {
            scrollbar = box(
                Modifier.width(2f).fillHeight().color(EssentialPalette.LIGHTEST_BACKGROUND)
                    .hoverColor(EssentialPalette.SCROLLBAR)
            )
        }
        scroller.setVerticalScrollBarComponent(scrollbar, hideWhenUseless = true)
    }

    /** For the action buttons of your modal. See [primaryButton], [cancelButton] & [primaryAndCancelButtons]. */
    abstract fun LayoutScope.layoutButtons()

    override fun handleEscapeKeyPress() {
        if (requiresButtonPress) {
            return
        }

        super.handleEscapeKeyPress()
    }

    /** A [text] with some defaults for a modal title. */
    open fun LayoutScope.title(text: State<String>, modifier: Modifier = Modifier) {
        text(
            text,
            Modifier.color(EssentialPalette.TEXT_HIGHLIGHT).shadow(Color.BLACK).then(modifier),
        )
    }

    fun LayoutScope.title(text: String, modifier: Modifier = Modifier) = title(stateOf(text), modifier)

    /** A [wrappedText] with some defaults for a modal's description. Intended for use in [layoutBody]. */
    fun LayoutScope.description(
        text: String,
        textModifier: Modifier = Modifier,
        block: WrappedTextBuilder.() -> Unit = {},
    ) {
        wrappedText(
            text,
            textModifier = Modifier.color(EssentialPalette.TEXT_MID_GRAY).shadow(Color.BLACK).then(textModifier),
            block = block,
        )
    }

    /** See [primaryButton], [cancelButton]. */
    fun LayoutScope.primaryAndCancelButtons(
        primaryText: String,
        cancelText: String,
        primaryAction: suspend () -> Unit,
        cancelAction: () -> Unit = ::close,
        primaryStyle: StyledButton.Style = OutlineButtonStyle.BLUE,
    ) {
        row(Arrangement.spacedBy(8f)) {
            cancelButton(cancelText, action = cancelAction)
            primaryButton(primaryText, style = primaryStyle, action = primaryAction)
        }
    }

    /**
     * [action] is a suspend function which is called on left-click. While the [action] is executing,
     * the button will be in a "disabled" state, where no click events will propagate.
     *
     * The [StyledButton.Style.disabledStyle] will be used while the [action] is being executed.
     *
     * NOTE: This will *not* close the modal on left-click, you must do that manually in your [action].
     */
    fun LayoutScope.primaryButton(
        text: String,
        modifier: Modifier = Modifier,
        style: StyledButton.Style = OutlineButtonStyle.BLUE,
        disabled: State<Boolean> = stateOf(false),
        action: suspend () -> Unit,
    ) {
        outlineButton(
            Modifier
                .width(91f)
                .shadow()
                .tag(PrimaryAction)
                .then(modifier),
            style = { style },
            disabled = disabled,
            action = action,
        ) { currentStyle ->
            text(text, Modifier.textStyle(currentStyle))
        }
    }

    fun LayoutScope.cancelButton(
        text: String,
        modifier: Modifier = Modifier,
        action: () -> Unit = ::close,
    ) {
        outlineButton(
            Modifier
                .width(91f)
                .shadow()
                .onLeftClick {
                    USound.playButtonPress()
                    action()
                }
                .focusable()
                .then(modifier),
        ) { style ->
            text(text, Modifier.textStyle(style))
        }
    }

    /**
     * An overload of [gg.essential.gui.common.outlineButton].
     *
     * [action] is a suspend function which is called on left-click. While the [action] is executing,
     * the button will be in a "disabled" state, where no click events will propagate.
     *
     * The [StyledButton.Style.disabledStyle] will be used while the [action] is being executed.
     */
    fun LayoutScope.outlineButton(
        modifier: Modifier = Modifier,
        style: State<StyledButton.Style> = stateOf(OutlineButtonStyle.GRAY),
        disabled: State<Boolean> = stateOf(false),
        action: suspend () -> Unit,
        content: LayoutScope.(style: State<MenuButton.Style>) -> Unit,
    ) {
        val actionRunning = mutableStateOf(false)
        val effectiveDisabled = State { disabled() || actionRunning() }

        outlineButton(
            Modifier
                .onLeftClick {
                    USound.playButtonPress()

                    coroutineScope.launch {
                        actionRunning.set(true)
                        try {
                            action()
                        } finally {
                            actionRunning.set(false)
                        }
                    }
                }
                .focusable(effectiveDisabled)
                .then(modifier),
            style,
            effectiveDisabled,
            content
        )
    }

    /** See [outlineButton], just an overload without any state to make it easier to call. */
    fun LayoutScope.outlineButton(
        modifier: Modifier = Modifier,
        style: StyledButton.Style,
        disabled: Boolean = false,
        action: suspend () -> Unit,
        content: LayoutScope.(style: State<MenuButton.Style>) -> Unit,
    ) {
        outlineButton(
            modifier,
            stateOf(style),
            stateOf(disabled),
            action,
            content,
        )
    }

    /** Indicates that this component is considered to be the [EssentialModal2]'s primary action. */
    data object PrimaryAction : Tag
}
