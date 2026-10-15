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
package gg.essential.gui.vigilancev2.components

import gg.essential.elementa.constraints.MinConstraint
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.effect
import gg.essential.elementa.dsl.percent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.common.FullEssentialToggle
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.common.shadow.ShadowEffect
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.vigilancev2.palette.VigilancePalette
import gg.essential.mod.vigilance2.builder.StateBackedPropertyValue
import gg.essential.mod.vigilance2.builder.VisibleDependencyPredicate
import gg.essential.vigilance.data.PropertyData
import gg.essential.vigilance.data.PropertyType
import gg.essential.vigilance.gui.settings.*
import gg.essential.vigilance.utils.onLeftClick
import java.awt.Color

fun LayoutScope.settingContainer(data: PropertyData) {
    box(
        Modifier
            .fillWidth(padding = 1f)
            .childBasedHeight(padding = 14f)
            .outline(VigilancePalette.SETTING_OUTLINE, 1f)
            .color(VigilancePalette.SETTING_BACKGROUND)
    ) {
        val container = containerDontUseThisUnlessYouReallyHaveTo

        row(Modifier.fillWidth(padding = 14f)) {
            box(Modifier.fillRemainingWidth()) {
                column(
                    Modifier.alignHorizontal(Alignment.Start).then(BasicWidthModifier {
                        MinConstraint(70.percent boundTo container, 100.percent)
                    }),
                    Arrangement.spacedBy(5f),
                    Alignment.Start
                ) {
                    wrappedText(
                        data.attributesExt.name,
                        Modifier.color(VigilancePalette.SETTING_TITLE).shadow(VigilancePalette.TEXT_SHADOW).bold(),
                    )

                    wrappedText(
                        data.attributesExt.description,
                        Modifier.color(VigilancePalette.TEXT).shadow(VigilancePalette.TEXT_SHADOW),
                        lineSpacing = 10f
                    )


                    val links = (data.dependencyPredicate as? VisibleDependencyPredicate)?.links
                    if (!links.isNullOrEmpty()) {
                        val modifier = Modifier.color(VigilancePalette.TEXT_URL).shadow(VigilancePalette.TEXT_URL_SHADOW).hoverColor(VigilancePalette.TEXT_URL_HOVER)
                        column {
                            spacer(height = 3f)
                            row(Arrangement.spacedBy(10f)) {
                                for ((name, action) in links) {
                                    row(Modifier.hoverScope(), Arrangement.spacedBy(4f)) {
                                        text(name, modifier.underline())
                                        icon(EssentialPalette.ARROW_UP_RIGHT_5X5, modifier)
                                    }.onLeftClick {
                                        action()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            spacer(width = 23f)
            setting(data)
            spacer(width = 9f)
        }
    }
}

// FIXME: Use LayoutDSL to make use of State
fun LayoutScope.setting(data: PropertyData) = when (data.attributesExt.type) {
    PropertyType.SWITCH -> FullEssentialToggle(getAsBooleanState(data))()
    PropertyType.SELECTOR -> {
        val options = mutableListStateOf(*data.attributesExt.options.mapIndexed { value, text ->
            EssentialDropDown.Option(text, value)
        }.toTypedArray())
        val dropdown = EssentialDropDown(data.value.getValue(data.instance) as Int, options)

        dropdown.selectedOption.onSetValue(stateScope) {
            (data.value as StateBackedPropertyValue<*>).setValue(it.value, data.instance)
        }

        (data.value as StateBackedPropertyValue<*>).state.onSetValueAndNow(dropdown) { newValue ->
            dropdown.select(options.get().first { it.value == newValue })
        }

        dropdown()
    }
    PropertyType.CHECKBOX -> CheckboxComponent(data.getAsBoolean()).registerStateValueChangeListener(data)()
    PropertyType.PERCENT_SLIDER -> PercentSliderComponent(data.getValue()).registerStateValueChangeListener(data)()
    PropertyType.SLIDER -> SliderComponent(data.getValue(), data.attributesExt.min, data.attributesExt.max).registerStateValueChangeListener(data)()
    PropertyType.DECIMAL_SLIDER -> DecimalSliderComponent(
        data.getValue(),
        data.attributesExt.minF,
        data.attributesExt.maxF,
        data.attributesExt.decimalPlaces
    ).registerStateValueChangeListener(data)()

    PropertyType.NUMBER -> NumberComponent(
        data.getValue(),
        data.attributesExt.min,
        data.attributesExt.max,
        data.attributesExt.increment
    ).registerStateValueChangeListener(data)()

    PropertyType.COLOR -> ColorComponent(data.getValue(), data.attributesExt.allowAlpha).registerStateValueChangeListener(data)()
    PropertyType.TEXT -> TextComponent(
        data.getValue(),
        data.attributesExt.placeholder,
        false,
        data.attributesExt.protected
    ).registerStateValueChangeListener(data)()

    PropertyType.PARAGRAPH -> TextComponent(
        data.getValue(),
        data.attributesExt.placeholder,
        wrap = true,
        protected = false
    ).registerStateValueChangeListener(data)()

    PropertyType.BUTTON -> ButtonComponent(data.attributesExt.placeholder, data)
        .effect(ShadowEffect(Color.BLACK))()

    PropertyType.CUSTOM -> {
        val propertyInfoClass = data.attributesExt.customPropertyInfo
        propertyInfoClass
            .getConstructor()
            .newInstance()
            .createSettingComponent(data.getValue())
            .registerStateValueChangeListener(data)()
    }
}

private fun SettingComponent.registerStateValueChangeListener(data: PropertyData): SettingComponent {
    val state = data.value as StateBackedPropertyValue<*>
    onValueChange {
        state.setValue(it, data.instance)
    }

    return this
}

@Suppress("UNCHECKED_CAST") // By using this function, you are saying that the T of `StateBackedPropertyValue` is `Boolean`.
private fun getAsBooleanState(data: PropertyData) =
    (data.value as StateBackedPropertyValue<Boolean>).state
