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
package gg.essential.gui.wardrobe.configuration

import gg.essential.elementa.UIComponent
import gg.essential.elementa.events.UIClickEvent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.about.components.ColoredDivider
import gg.essential.gui.common.ContextOptionMenu
import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.StyledButton
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialDoubleInput
import gg.essential.gui.common.input.essentialFloatInput
import gg.essential.gui.common.input.essentialISODateInput
import gg.essential.gui.common.input.essentialIntInput
import gg.essential.gui.common.input.essentialLongInput
import gg.essential.gui.common.input.essentialManagedNullableISODateInput
import gg.essential.gui.common.input.essentialNullableISODateInput
import gg.essential.gui.common.input.essentialNullableStringInput
import gg.essential.gui.common.input.essentialStringInput
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.common.outlineButton
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.util.focusedState
import gg.essential.mod.EssentialAsset
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.network.connectionmanager.cosmetics.CosmeticsDataWithChanges
import gg.essential.network.connectionmanager.cosmetics.removeCosmeticProperty
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.USound
import gg.essential.util.lwjgl3.api.*
import gg.essential.util.onLeftClick
import gg.essential.vigilance.utils.onLeftClick
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

object ConfigurationUtils {

    private val LOGGER = LoggerFactory.getLogger(ConfigurationUtils::class.java)

    // 6x6 white png used as a default icon
    private const val BLANK_IMAGE_URI =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAYAAAAGCAIAAABvrngfAAAAAXNSR0IArs4c6QAAAMZlWElmTU0AKgAAAAgABgESAAMAAAABAAEAAAEaAAUAAAABAAAAVgEbAAUAAAABAAAAXgEoAAMAAAABAAIAAAExAAIAAAAVAAAAZodpAAQAAAABAAAAfAAAAAAAAABIAAAAAQAAAEgAAAABUGl4ZWxtYXRvciBQcm8gMy40LjMAAAAEkAQAAgAAABQAAACyoAEAAwAAAAEAAQAAoAIABAAAAAEAAAAGoAMABAAAAAEAAAAGAAAAADIwMjM6MTA6MTcgMTc6MTM6MjkA7laYZgAAAAlwSFlzAAALEwAACxMBAJqcGAAAA65pVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IlhNUCBDb3JlIDYuMC4wIj4KICAgPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4KICAgICAgPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIKICAgICAgICAgICAgeG1sbnM6dGlmZj0iaHR0cDovL25zLmFkb2JlLmNvbS90aWZmLzEuMC8iCiAgICAgICAgICAgIHhtbG5zOmV4aWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20vZXhpZi8xLjAvIgogICAgICAgICAgICB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iPgogICAgICAgICA8dGlmZjpZUmVzb2x1dGlvbj43MjAwMDAvMTAwMDA8L3RpZmY6WVJlc29sdXRpb24+CiAgICAgICAgIDx0aWZmOlhSZXNvbHV0aW9uPjcyMDAwMC8xMDAwMDwvdGlmZjpYUmVzb2x1dGlvbj4KICAgICAgICAgPHRpZmY6UmVzb2x1dGlvblVuaXQ+MjwvdGlmZjpSZXNvbHV0aW9uVW5pdD4KICAgICAgICAgPHRpZmY6T3JpZW50YXRpb24+MTwvdGlmZjpPcmllbnRhdGlvbj4KICAgICAgICAgPGV4aWY6UGl4ZWxZRGltZW5zaW9uPjY8L2V4aWY6UGl4ZWxZRGltZW5zaW9uPgogICAgICAgICA8ZXhpZjpQaXhlbFhEaW1lbnNpb24+NjwvZXhpZjpQaXhlbFhEaW1lbnNpb24+CiAgICAgICAgIDx4bXA6TWV0YWRhdGFEYXRlPjIwMjMtMTAtMTdUMTc6MTQ6NTIrMDI6MDA8L3htcDpNZXRhZGF0YURhdGU+CiAgICAgICAgIDx4bXA6Q3JlYXRlRGF0ZT4yMDIzLTEwLTE3VDE3OjEzOjI5KzAyOjAwPC94bXA6Q3JlYXRlRGF0ZT4KICAgICAgICAgPHhtcDpDcmVhdG9yVG9vbD5QaXhlbG1hdG9yIFBybyAzLjQuMzwveG1wOkNyZWF0b3JUb29sPgogICAgICA8L3JkZjpEZXNjcmlwdGlvbj4KICAgPC9yZGY6UkRGPgo8L3g6eG1wbWV0YT4K5w86hAAAABZJREFUCB1j/P//PwMqYELlgnjUFAIAUd0DCc8FRvYAAAAASUVORK5CYII="
    private const val BLANK_IMAGE_HASH = "bf6d320d5b75603be8f5756f5644d094"
    val blankImageEssentialAsset = EssentialAsset(BLANK_IMAGE_URI, BLANK_IMAGE_HASH)

    fun LayoutScope.divider() = box(Modifier.fillWidth().height(2f).color(EssentialPalette.LIGHT_DIVIDER))

    fun LayoutScope.configuratorButton(
        text: String,
        modifier: Modifier = Modifier,
        disabled: State<Boolean> = stateOf(false),
        style: StyledButton.Style = OutlineButtonStyle.GRAY,
        action: () -> Unit
    ) = configuratorButton(stateOf(text), modifier, disabled, style, action)

    fun LayoutScope.configuratorButton(
        text: State<String>,
        modifier: Modifier = Modifier,
        disabled: State<Boolean> = stateOf(false),
        style: StyledButton.Style = OutlineButtonStyle.GRAY,
        action: () -> Unit
    ) = outlineButton(text, disabled = disabled, style = style, modifier = Modifier.fillWidth(padding = 10f) then modifier).onLeftClick {
        USound.playButtonPress()
        action()
    }

    fun LayoutScope.configuratorCollapsibleSection(
        name: String,
        tooltip: String? = null,
        startExpanded: Boolean = false,
        hasDividerLine: Boolean = true,
        removeAction: (UIComponent.(UIClickEvent) -> Unit)? = null,
        block: LayoutScope.() -> Unit
    ) {
        val expanded = mutableStateOf(startExpanded)
        row(
            if (tooltip == null) Modifier.height(20f).fillWidth() else Modifier.height(16f).fillWidth().hoverScope()
                .hoverTooltip(tooltip, position = EssentialTooltip.Position.LEFT, wrapAtWidth = 200f)
        ) {
            row(Modifier.fillRemainingWidth()) {
                if (hasDividerLine) ColoredDivider(name)(Modifier.fillRemainingWidth())
                else box(Modifier.fillRemainingWidth()) { text(name, truncateIfTooSmall = true, modifier = Modifier.alignHorizontal(Alignment.Start)) }
                box(Modifier.width(14f).heightAspect(1f)) {
                    icon(expanded.letState { if (it) EssentialPalette.ARROW_UP_7X5 else EssentialPalette.ARROW_DOWN_7X5 })
                }
            }.onLeftClick { expanded.set { !it } }
            if (removeAction != null) {
                box(Modifier.width(10f).heightAspect(1f).hoverTooltip("Remove").hoverScope()) {
                    icon(EssentialPalette.CANCEL_5X)
                }.onLeftClick(removeAction)
            }
        }
        if_(expanded) {
            block()
        }
    }

    fun LayoutScope.configuratorWrappedLabeledRow(
        label: String,
        blockRowArrangement: Arrangement = Arrangement.spacedBy(),
        removeAction: (UIComponent.(UIClickEvent) -> Unit)? = null,
        block: (LayoutScope.() -> Unit)? = null
    ) {
        row(Modifier.fillWidth()) {
            box(Modifier.fillRemainingWidth()) {
                wrappedText(label, Modifier.alignHorizontal(Alignment.Start))
            }
            if (block != null) {
                row(blockRowArrangement) { block() }
            }
            if (removeAction != null) {
                box(Modifier.width(10f).heightAspect(1f).hoverTooltip("Remove").hoverScope()) {
                    icon(EssentialPalette.CANCEL_5X)
                }.onLeftClick(removeAction)
            }
        }
    }

    fun LayoutScope.labeledRow(label: String, arrangement: Arrangement = Arrangement.SpaceBetween, inputComponent: LayoutScope.() -> Unit) {
        row(Modifier.fillWidth(), arrangement) {
            text(label, truncateIfTooSmall = true)
            inputComponent()
        }
    }

    fun <T> LayoutScope.labeledInputRow(label: String, arrangement: Arrangement = Arrangement.SpaceBetween, inputComponent: LayoutScope.() -> StateTextInput<T>): StateTextInput<T> {
        val input: StateTextInput<T>
        row(Modifier.fillWidth(), arrangement) {
            text(label, truncateIfTooSmall = true)
            input = inputComponent()
        }
        return input
    }

    fun LayoutScope.labeledBooleanInputRow(
        label: String,
        initialValue: Boolean,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        onSetValue: (Boolean) -> Unit
    ) {
        labeledListInputRow(
            label,
            initialValue,
            listStateOf(EssentialDropDown.Option("True", true), EssentialDropDown.Option("False", false)),
            inputModifier,
            horizontalArrangement,
            onSetValue
        )
    }

    fun LayoutScope.labeledIntInputRow(
        label: String,
        state: MutableState<Int>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialIntInput(state, inputModifier, min, max)
    }

    fun LayoutScope.labeledLongInputRow(
        label: String,
        state: MutableState<Long>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialLongInput(state, inputModifier, min, max)
    }

    fun LayoutScope.labeledFloatInputRow(
        label: String,
        state: MutableState<Float>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        min: Float = Float.NEGATIVE_INFINITY,
        max: Float = Float.POSITIVE_INFINITY,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialFloatInput(state, inputModifier, min, max)
    }

    fun LayoutScope.labeledDoubleInputRow(
        label: String,
        state: MutableState<Double>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialDoubleInput(state, inputModifier, min, max)
    }

    fun LayoutScope.labeledStringInputRow(
        label: String,
        state: MutableState<String>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialStringInput(state, inputModifier)
    }

    fun LayoutScope.labeledNullableStringInputRow(
        label: String,
        state: MutableState<String?>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialNullableStringInput(state, inputModifier)
    }

    fun LayoutScope.labeledManagedNullableISODateInputRow(
        label: String,
        state: MutableState<Instant?>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialManagedNullableISODateInput(state, inputModifier)
    }

    fun LayoutScope.labeledNullableISODateInputRow(
        label: String,
        state: MutableState<Instant?>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialNullableISODateInput(state, inputModifier)
    }

    fun LayoutScope.labeledISODateInputRow(
        label: String,
        state: MutableState<Instant>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
    ) = labeledInputRow(label, horizontalArrangement) {
        essentialISODateInput(state, inputModifier)
    }

    fun <E : Enum<E>> LayoutScope.labeledEnumInputRow(
        label: String,
        initialValue: E,
        inputModifier: Modifier = Modifier,
        enumFilter: (ListState<EssentialDropDown.Option<E>>) -> ListState<EssentialDropDown.Option<E>> = { it },
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        onSetValue: (E) -> Unit
    ) {
        labeledListInputRow(
            label,
            initialValue,
            enumFilter(stateOf(initialValue.declaringJavaClass.enumConstants.map { EssentialDropDown.Option(it.name, it) }).toListState()),
            inputModifier,
            horizontalArrangement,
            onSetValue
        )
    }

    fun <T> LayoutScope.labeledListInputRow(
        label: String,
        initialValue: T,
        optionsList: List<T>,
        nameMapper: (T) -> String,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        onSetValue: (T) -> Unit
    ) {
        labeledListInputRow(label, initialValue, stateOf(optionsList.map { EssentialDropDown.Option(nameMapper(it), it) }).toListState(), inputModifier, horizontalArrangement, onSetValue)
    }

    fun <T> LayoutScope.labeledListInputRow(
        label: String,
        initialValue: T,
        optionsList: ListState<T>,
        nameMapper: (T) -> String,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        onSetValue: (T) -> Unit
    ) {
        labeledListInputRow(label, initialValue, optionsList.mapEach { EssentialDropDown.Option(nameMapper(it), it) }, inputModifier, horizontalArrangement, onSetValue)
    }

    fun <T> LayoutScope.labeledListInputRow(
        label: String,
        initialValue: T,
        optionsList: ListState<EssentialDropDown.Option<T>>,
        inputModifier: Modifier = Modifier,
        horizontalArrangement: Arrangement = Arrangement.SpaceBetween,
        onSetValue: (T) -> Unit
    ) {
        labeledRow(label, horizontalArrangement) {
            val dropDown = EssentialDropDown(initialValue, optionsList)
            dropDown.selectedOption.onChange(stateScope) { onSetValue(it.value) }
            dropDown(inputModifier)
        }
    }

    fun LayoutScope.addAutoCompleteMenu(
        input: StateTextInput<*>,
        items: ListState<Pair<String, String>>,
    ) {
        val minChars = items.letState { if (it.size > 20) 2 else 0 }
        val options = memo {
            val text = input.textState()
            if (text.length < minChars()) return@memo listOf()
            return@memo items().filter { it.first.contains(text, ignoreCase = true) || it.second.contains(text, ignoreCase = true) }
        }.toListState().mapEach { (id, name) ->
            ContextOptionMenu.Option(if (id == name) id else "$name ($id)", null) { input.setText(id) }
        }
        var menu: ContextOptionMenu? = null
        input.focusedState().onChange(stateScope) { focused ->
            menu?.close()
            menu = null
            if (focused) {
                menu = ContextOptionMenu.create(input, options)
            }
        }
        // Fixes menu staying around if manually entering cosmetic
        input.state.onChange(stateScope) {
            menu?.close()
            menu = null
        }
    }

    suspend fun ModalFlow.configuratorDeleteCosmeticPropertyModal(
        cosmeticsData: CosmeticsDataWithChanges,
        cosmetic: Cosmetic,
        property: CosmeticProperty,
    ) {
        awaitModal<Unit> {
            object : EssentialModal2(modalManager, false) {
                override fun LayoutScope.layoutTitle() {
                    title("Are you sure you want to remove the property with id ${property.id}?")
                }

                override fun LayoutScope.layoutButtons() {
                    primaryAndCancelButtons(
                        "Delete",
                        "Cancel",
                        primaryStyle = OutlineButtonStyle.RED,
                        primaryAction = {
                            cosmeticsData.removeCosmeticProperty(cosmetic.id, property)
                            close()
                        }
                    )
                }

            }
        }
    }

    suspend fun ModalFlow.configuratorDangerModal(primaryText: String, title: String = "", description: String = "") {
        return awaitModal { continuation ->
            object : EssentialModal2(modalManager) {
                override fun LayoutScope.layoutTitle() {
                    wrappedText(title)
                }

                override fun LayoutScope.layoutBody() {
                    wrappedText(description)
                }

                override fun LayoutScope.layoutButtons() {
                    primaryAndCancelButtons(primaryText, "Cancel", primaryStyle = OutlineButtonStyle.RED, primaryAction = { replaceWith(continuation.resume(Unit)) })
                }
            }
        }
    }

    suspend fun ModalFlow.configuratorNoticeModal(title: String = "", description: String) {
        return awaitModal { continuation ->
            object : EssentialModal2(modalManager) {
                override fun LayoutScope.layoutTitle() {
                    wrappedText(title)
                }

                override fun LayoutScope.layoutBody() {
                    wrappedText(description)
                }

                override fun LayoutScope.layoutButtons() {
                    cancelButton("Ok")
                }
            }
        }
    }

}
