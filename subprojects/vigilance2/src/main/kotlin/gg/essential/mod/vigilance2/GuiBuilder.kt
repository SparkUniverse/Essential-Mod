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
package gg.essential.mod.vigilance2

import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.Observer
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.bimapState
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.mod.vigilance2.builder.StateBackedPropertyValue
import gg.essential.mod.vigilance2.builder.VisibleDependencyPredicate
import gg.essential.vigilance.Vigilant
import gg.essential.vigilance.data.KFunctionBackedPropertyValue
import gg.essential.vigilance.data.PropertyAttributesExt
import gg.essential.vigilance.data.PropertyData
import gg.essential.vigilance.data.PropertyInfo
import gg.essential.vigilance.data.PropertyType
import gg.essential.vigilance.data.PropertyValue
import java.awt.Color
import java.io.File
import kotlin.enums.enumEntries

class GuiBuilder internal constructor(
    private val instance: Vigilant,
) {
    private val categories = mutableListOf<CategoryBuilder>()

    companion object {
        fun build(title: String, block: GuiBuilder.() -> Unit): ListState<PropertyData> {
            val guiInstance = object : Vigilant(File.createTempFile("dummy-config", ".toml"), title, PassivePropertyCollector()) {}

            val builder = GuiBuilder(guiInstance).apply(block)
            val properties = State {
                buildList {
                    for (category in builder.categories) {
                        with(category) {
                            flattenInto(this@buildList)
                        }
                    }
                }
            }.toListState()

            return properties
        }
    }

    fun category(name: String, block: CategoryBuilder.() -> Unit) {
        categories.add(CategoryBuilder(this, name, "").apply(block))
    }

    open class CategoryBuilder internal constructor(
        private val guiBuilder: GuiBuilder,
        private val category: String,
        private val subcategory: String,
    ) {
        private val content: MutableList<CategoryContent> = mutableListOf()

        private sealed interface CategoryContent {
            class Static(val data: PropertyData): CategoryContent
            class Dynamic(val builderState: State<CategoryBuilder>): CategoryContent
        }

        internal fun Observer.flattenInto(result: MutableList<PropertyData>) {
            for (element in content) {
                when (element) {
                    is CategoryContent.Static -> result.add(element.data)
                    is CategoryContent.Dynamic -> with(element.builderState()) { flattenInto(result) }
                }
            }
        }

        fun dynamic(block: ObservingCategoryBuilder.() -> Unit) {
            content.add(CategoryContent.Dynamic(memo {
                ObservingCategoryBuilder(this, guiBuilder, category, subcategory).apply(block)
            }))
        }

        fun subcategory(name: String, block: CategoryBuilder.() -> Unit) {
            content.addAll(CategoryBuilder(guiBuilder, category, name).apply(block).content)
        }

        private fun property(value: PropertyValue, type: PropertyType, configure: PropertyBuilderImpl.() -> Unit) {
            val builder = PropertyBuilderImpl(type)
            builder.category = category
            builder.subcategory = subcategory
            configure(builder)

            content.add(CategoryContent.Static(builder.build(value, guiBuilder.instance)))
        }

        private fun <T> property(state: MutableState<T>, type: PropertyType, configure: PropertyBuilderImpl.() -> Unit) {
            property(StateBackedPropertyValue(state, false), type, configure)
        }

        fun switch(state: MutableState<Boolean>, configure: SwitchPropertyBuilder.() -> Unit) =
            property(state, PropertyType.SWITCH, configure)

        fun checkbox(state: MutableState<Boolean>, configure: CheckboxPropertyBuilder.() -> Unit) =
            property(state, PropertyType.CHECKBOX, configure)

        fun text(state: MutableState<String>, configure: TextPropertyBuilder.() -> Unit) =
            property(state, PropertyType.TEXT, configure)

        fun paragraph(state: MutableState<String>, configure: ParagraphPropertyBuilder.() -> Unit) =
            property(state, PropertyType.PARAGRAPH, configure)

        fun slider(state: MutableState<Int>, configure: SliderPropertyBuilder.() -> Unit) =
            property(state, PropertyType.SLIDER, configure)

        fun number(state: MutableState<Int>, configure: NumberPropertyBuilder.() -> Unit) =
            property(state, PropertyType.NUMBER, configure)

        fun color(state: MutableState<Color>, configure: ColorPropertyBuilder.() -> Unit) =
            property(state, PropertyType.COLOR, configure)

        fun selector(state: MutableState<Int>, configure: SelectorPropertyBuilder.() -> Unit) =
            property(state, PropertyType.SELECTOR, configure)

        fun <T> selector(state: MutableState<T>, options: List<T>, configure: SelectorPropertyBuilder.() -> Unit) =
            selector(state.bimapState({ options.indexOf(it) }, { options[it] }), configure)

        @OptIn(ExperimentalStdlibApi::class) // `enumEntries` became stable with 2.0
        @JvmName("selectorEnum")
        inline fun <reified T : Enum<T>> selector(state: MutableState<T>, noinline configure: SelectorPropertyBuilder.() -> Unit) =
            selector(state, enumEntries<T>(), configure)

        fun button(func: () -> Unit, configure: ButtonPropertyBuilder.() -> Unit) =
            property(KFunctionBackedPropertyValue(func), PropertyType.BUTTON, configure)

        fun <T> custom(state: MutableState<T>, cls: Class<out PropertyInfo>, configure: CommonPropertyBuilder.() -> Unit) =
            property(state, PropertyType.CUSTOM) {
                customPropertyInfo = cls
                configure()
            }
    }

    class ObservingCategoryBuilder(
        private val observer: Observer,
        guiBuilder: GuiBuilder,
        category: String,
        subcategory: String,
    ) : CategoryBuilder(guiBuilder, category, subcategory), Observer by observer

    interface CommonPropertyBuilder {
        var name: String // default ""
        var description: String // default ""
        var visible: State<Boolean> // default stateOf(true)
        var searchTags: List<String> // default emptyList()
        var links: List<Pair<String, () -> Unit>>
    }

    interface SwitchPropertyBuilder : CommonPropertyBuilder
    interface CheckboxPropertyBuilder : CommonPropertyBuilder

    interface TextPropertyBuilder : CommonPropertyBuilder {
        var placeholder: String // default ""
        var protected: Boolean // default false
    }

    interface ParagraphPropertyBuilder : CommonPropertyBuilder {
        var placeholder: String // default ""
    }

    interface PercentSliderPropertyBuilder : CommonPropertyBuilder

    interface SliderPropertyBuilder : CommonPropertyBuilder {
        var min: Int // default 0
        var max: Int // default 0
    }

    interface DecimalSliderPropertyBuilder : CommonPropertyBuilder {
        var minF: Float // default 0
        var maxF: Float // default 0
        var decimalPlaces: Int // default 1
    }

    interface NumberPropertyBuilder : CommonPropertyBuilder {
        var min: Int // default 0
        var max: Int // default 0
        var increment: Int // default 1
    }

    interface ColorPropertyBuilder : CommonPropertyBuilder {
        var allowAlpha: Boolean // default true
    }

    interface SelectorPropertyBuilder : CommonPropertyBuilder {
        var options: List<String> // default emptyList()
    }

    interface ButtonPropertyBuilder : CommonPropertyBuilder {
        var label: String // default ""
    }

    private class PropertyBuilderImpl(var type: PropertyType)
        : SwitchPropertyBuilder
        , CheckboxPropertyBuilder
        , TextPropertyBuilder
        , ParagraphPropertyBuilder
        , PercentSliderPropertyBuilder
        , SliderPropertyBuilder
        , DecimalSliderPropertyBuilder
        , NumberPropertyBuilder
        , ColorPropertyBuilder
        , SelectorPropertyBuilder
        , ButtonPropertyBuilder
    {
        var category: String = ""
        var subcategory: String = ""
        override var name: String = ""
        override var description: String = ""
        override var min: Int = 0
        override var max: Int = 0
        override var minF: Float = 0f
        override var maxF: Float = 0f
        override var decimalPlaces: Int = 1
        override var increment: Int = 1
        override var options: List<String> = listOf()
        override var allowAlpha: Boolean = true
        override var placeholder: String = ""
        override var label: String by ::placeholder
        override var protected: Boolean = false
        override var visible: State<Boolean> = stateOf(true)
        override var searchTags: List<String> = listOf()
        override var links: List<Pair<String, () -> Unit>> = emptyList()
        var customPropertyInfo: Class<out PropertyInfo> = Nothing::class.java

        fun build(value: PropertyValue, instance: Vigilant): PropertyData {
            val attr = PropertyAttributesExt(
                type,
                name,
                category,
                subcategory,
                description,
                min,
                max,
                minF,
                maxF,
                decimalPlaces,
                increment,
                options,
                allowAlpha,
                placeholder,
                protected,
                searchTags = searchTags,
                customPropertyInfo = customPropertyInfo,
            )
            val data = PropertyData(attr, value, instance)
            // We hijack Vigilance' built-in dependency system, so we want each property to update all others regardless
            // of whether there is actually anything depending on it.
            data.hasDependants = true
            // we don't know the actual dependency, this just needs to be fetch-able so the predicate is evaluated
            data.dependsOn = PropertyData(
                PropertyAttributesExt(PropertyType.SWITCH, "", ""),
                StateBackedPropertyValue(mutableStateOf(false), false),
                instance,
            )
            // and finally, this is where the magic happens
            val visible = visible
            data.dependencyPredicate = VisibleDependencyPredicate(visible, links)
            return data
        }
    }
}
