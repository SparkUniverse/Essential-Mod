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

import gg.essential.cosmetics.FeaturedPageCollectionId
import gg.essential.cosmetics.FeaturedPageWidth
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.common.compactFullEssentialToggle
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.UITextInput
import gg.essential.gui.common.input.essentialInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.common.input.essentialStringInput
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationType.Companion.createWithIDModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.addAutoCompleteMenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledISODateInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledListInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledNullableISODateInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorButton
import gg.essential.gui.wardrobe.configuration.cosmetic.settings.*
import gg.essential.mod.cosmetics.CosmeticBundle
import gg.essential.mod.cosmetics.featured.BaseDivider
import gg.essential.mod.cosmetics.featured.BlankDivider
import gg.essential.mod.cosmetics.featured.DividerType
import gg.essential.mod.cosmetics.featured.FeaturedPageComponent
import gg.essential.mod.cosmetics.featured.FeaturedItem
import gg.essential.mod.cosmetics.featured.FeaturedItemRow
import gg.essential.mod.cosmetics.featured.FeaturedPage
import gg.essential.mod.cosmetics.featured.FeaturedPageCollection
import gg.essential.mod.cosmetics.featured.TextDivider
import gg.essential.mod.cosmetics.settings.CosmeticSettingType
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick
import java.time.Duration
import java.time.Instant

class FeaturedPageCollectionConfiguration(
    state: WardrobeState,
) : AbstractConfiguration<FeaturedPageCollectionId, FeaturedPageCollection>(
    ConfigurationType.FEATURED_PAGE_LAYOUT_COLLECTIONS,
    state
) {

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun LayoutScope.columnLayout(pageCollection: FeaturedPageCollection) {
        configuratorButton("Add new page") {
            USound.playButtonPress()
            showNewPageModal(pageCollection)
        }
        spacer(height = 5f)
        val availability = pageCollection.availability
        val availabilityState = mutableStateOf(availability != null)
        availabilityState.onChange(stateScope) {
            if (it) {
                pageCollection.update(pageCollection.copy(availability = FeaturedPageCollection.Availability(Instant.now(), Instant.now().plus(Duration.ofDays(1)), null)))
            } else {
                pageCollection.update(pageCollection.copy(availability = null))
            }
        }
        labeledRow("Availability: ") {
            box(Modifier.childBasedWidth(3f).childBasedHeight(3f).hoverScope()) {
                compactFullEssentialToggle(availabilityState)
                spacer(1f, 1f)
            }
        }
        if (availability != null) {
            labeledISODateInputRow("After:", mutableStateOf(availability.after)).state.onChange(stateScope) {
                pageCollection.update(pageCollection.copy(availability = availability.copy(after = it)))
            }
            labeledISODateInputRow("Until:", mutableStateOf(availability.until)).state.onChange(stateScope) {
                pageCollection.update(pageCollection.copy(availability = availability.copy(until = it)))
            }
            labeledNullableISODateInputRow("Timer After:", mutableStateOf(availability.showTimerAfter)).state.onChange(stateScope) {
                pageCollection.update(pageCollection.copy(availability = availability.copy(showTimerAfter = it)))
            }
        }
        spacer(height = 5f)
        submenuSelection(pageCollection)
        configuratorButton("Clone") {
            USound.playButtonPress()
            launchModalFlow(platform.createModalManager()) {
                val id = createWithIDModal("Featured page collection", cosmeticsDataWithChanges.featuredPageCollections.letState { it.map { it.id } }.toListState())
                cosmeticsDataWithChanges.registerFeaturedPageCollection(id, pageCollection.availability, pageCollection.pages)
                state.currentlyEditingFeaturedPageCollectionId.set(id)
            }
        }
    }

    override fun getSubmenus(editing: FeaturedPageCollection): List<AbstractConfigurationSubmenu<FeaturedPageCollection>> {
        return editing.pages.entries.map { FeaturedPageConfigurationSubmenu(it.key.toString(), "${it.key}-wide page", editing, it.key, it.value) }
    }

    private inner class FeaturedPageConfigurationSubmenu(
        id: String,
        name: String,
        val pageCollection: FeaturedPageCollection,
        val width: FeaturedPageWidth,
        val layout: FeaturedPage
    ) : AbstractConfigurationSubmenu<FeaturedPageCollection>(id, name, pageCollection) {

        private val referenceHolder = ReferenceHolderImpl()

        override fun LayoutScope.layout(modifier: Modifier) {
            val rows = layout.rows

            for ((componentIndex, component) in rows.withIndex()) {
                when (component) {
                    is FeaturedItemRow -> rowConfiguration(componentIndex, component)
                    is BaseDivider -> dividerConfiguration(componentIndex, component)
                }
            }
            configuratorButton("Add Row") {
                USound.playButtonPress()
                update {
                    add(FeaturedItemRow(emptyList()))
                }
            }
            configuratorButton("Add Divider") {
                USound.playButtonPress()
                update {
                    add(BlankDivider)
                }
            }
            val optionList = mutableListOf<EssentialDropDown.Option<FeaturedPageWidth?>>(EssentialDropDown.Option("", null))
            optionList += pageCollection.pages.keys.filter { it != width }.map { EssentialDropDown.Option("$it-wide", it) }

            if (optionList.size > 1) {
                divider()
                labeledListInputRow("Copy from page:", null, stateOf(optionList).toListState()) {
                    val featuredPage = pageCollection.pages[it] ?: return@labeledListInputRow // Should never happen
                    pageCollection.update(pageCollection.copy(pages = pageCollection.pages + (width to featuredPage)))
                }
            }
        }

        fun update(builder: MutableList<FeaturedPageComponent>.() -> Unit) {
            val mutableRows = layout.rows.toMutableList()
            builder(mutableRows)
            currentlyEditing.update(currentlyEditing.copy(pages = currentlyEditing.pages + (width to layout.copy(rows = mutableRows))))
        }

        fun updateRow(componentIndex: Int, builder: MutableList<FeaturedItem>.() -> Unit) {
            val mutableRows = layout.rows.toMutableList()
            val mutableRow = (mutableRows[componentIndex] as FeaturedItemRow).items.toMutableList()
            builder(mutableRow)
            mutableRows[componentIndex] = FeaturedItemRow(mutableRow.toList())
            currentlyEditing.update(currentlyEditing.copy(pages = currentlyEditing.pages + (width to layout.copy(rows = mutableRows))))
        }

        fun updateComponent(componentIndex: Int, builder: FeaturedPageComponent.() -> FeaturedPageComponent) {
            val mutableRows = layout.rows.toMutableList()
            val mutableRow = mutableRows[componentIndex]
            mutableRows[componentIndex] = builder(mutableRow)
            currentlyEditing.update(currentlyEditing.copy(pages = currentlyEditing.pages + (width to layout.copy(rows = mutableRows))))
        }

        private fun LayoutScope.configurationTitle(title: String, componentIndex: Int, component: FeaturedPageComponent) {
            val rows = layout.rows
            row(Modifier.fillWidth().height(10f).color(EssentialPalette.LIGHT_SCROLLBAR), Arrangement.SpaceBetween) {
                text("$title " + (componentIndex + 1))
                row(Modifier.fillHeight()) {
                    box(Modifier.widthAspect(1f).fillHeight()) {
                        icon(EssentialPalette.ARROW_UP_7X5)
                    }.onLeftClick {
                        if (componentIndex > 0) {
                            USound.playButtonPress()
                            update {
                                removeAt(componentIndex)
                                add(componentIndex - 1, component)
                            }
                        }
                    }
                    box(Modifier.widthAspect(1f).fillHeight()) {
                        icon(EssentialPalette.ARROW_DOWN_7X5)
                    }.onLeftClick {
                        if (componentIndex + 1 < rows.size) {
                            USound.playButtonPress()
                            update {
                                removeAt(componentIndex)
                                add(componentIndex + 1, component)
                            }
                        }
                    }
                    box(Modifier.widthAspect(1f).fillHeight()) {
                        icon(EssentialPalette.CANCEL_5X)
                    }.onLeftClick {
                        USound.playButtonPress()
                        update {
                            removeAt(componentIndex)
                        }
                    }
                }
            }
        }

        private fun LayoutScope.dividerConfiguration(componentIndex: Int, baseDivider: BaseDivider) {
            configurationTitle("Divider", componentIndex, baseDivider)

            val optionList = listOf(
                EssentialDropDown.Option("Blank", DividerType.BLANK),
                EssentialDropDown.Option("Text", DividerType.TEXT)
            )
            val currentDivider = when (baseDivider) {
                is TextDivider -> DividerType.TEXT
                is BlankDivider -> DividerType.BLANK
            }

            labeledListInputRow("Type:", currentDivider, stateOf(optionList).toListState()) { option ->
                updateComponent(componentIndex) {
                    when (option) {
                        DividerType.TEXT -> TextDivider("")
                        DividerType.BLANK -> BlankDivider
                    }
                }
            }

            if (baseDivider is TextDivider) {
                val textState = mutableStateOf(baseDivider.text).apply {
                    onChange(referenceHolder) { text ->
                        updateComponent(componentIndex) { TextDivider(text) }
                    }
                }
                row(Modifier.fillWidth(), Arrangement.spacedBy(10f, FloatPosition.END)) {
                    text("Text:")
                    essentialStringInput(textState)
                }
            }
        }

        private fun LayoutScope.rowConfiguration(componentIndex: Int, row: FeaturedItemRow) {
            configurationTitle("Row", componentIndex, row)
            for ((itemIndex, item) in row.items.withIndex()) {
                row(Modifier.fillWidth().height(10f), Arrangement.SpaceBetween) {
                    val text = "- " + when (item) {
                        is FeaturedItem.Bundle -> "Bundle: " + item.bundle
                        is FeaturedItem.Cosmetic -> "Cosmetic: " + item.cosmetic
                        is FeaturedItem.Empty -> "Empty"
                    }
                    text(text)
                    row(Modifier.fillHeight()) {
                        box(Modifier.widthAspect(1f).fillHeight()) {
                            icon(EssentialPalette.ARROW_UP_7X5)
                        }.onLeftClick {
                            if (itemIndex > 0) {
                                USound.playButtonPress()
                                updateRow(componentIndex) {
                                    removeAt(itemIndex)
                                    add(itemIndex - 1, item)
                                }
                            }
                        }
                        box(Modifier.widthAspect(1f).fillHeight()) {
                            icon(EssentialPalette.ARROW_DOWN_7X5)
                        }.onLeftClick {
                            if (itemIndex + 1 < row.items.size) {
                                USound.playButtonPress()
                                updateRow(componentIndex) {
                                    removeAt(itemIndex)
                                    add(itemIndex + 1, item)
                                }
                            }
                        }
                        box(Modifier.widthAspect(1f).fillHeight()) {
                            icon(EssentialPalette.CANCEL_5X)
                        }.onLeftClick {
                            USound.playButtonPress()
                            updateRow(componentIndex) {
                                removeAt(itemIndex)
                            }
                        }
                    }
                }
                if (item is FeaturedItem.Cosmetic) {
                    val cosmeticId = item.cosmetic
                    val settings = mutableListStateOf(*item.settings.toTypedArray())
                    for (settingType in CosmeticSettingType.values()) {
                        val component = when (settingType) {
                            CosmeticSettingType.PLAYER_POSITION_ADJUSTMENT -> PlayerPositionAdjustmentSettingConfiguration(cosmeticsDataWithChanges, state.modelLoader, cosmeticId, settings)
                            CosmeticSettingType.SIDE -> SideSettingConfiguration(cosmeticsDataWithChanges, state.modelLoader, cosmeticId, settings)
                            CosmeticSettingType.VARIANT -> VariantSettingConfiguration(cosmeticsDataWithChanges, state.modelLoader, cosmeticId, settings)
                            CosmeticSettingType.ANIMATION_VARIANT -> continue
                        }
                        component()
                    }
                    settings.onChange(stateScope) {
                        updateRow(componentIndex) {
                            this[itemIndex] = item.copy(settings = it)
                        }
                    }
                }
            }
            row(Modifier.fillWidth(), Arrangement.spacedBy(10f, FloatPosition.END)) {
                val bundleState = mutableStateOf<CosmeticBundle?>(null)
                text("Add Bundle:")
                val input = essentialStateTextInput(
                    bundleState,
                    { it?.id ?: "" },
                    { if (it.isBlank()) null else (cosmeticsDataWithChanges.getCosmeticBundle(it) ?: throw StateTextInput.ParseException()) }
                )
                addAutoCompleteMenu(input, cosmeticsDataWithChanges.bundles.mapEach { it.id to it.name })
                bundleState.onChange(stateScope) {
                    val bundleId = (it ?: return@onChange).id
                    updateRow(componentIndex) {
                        add(FeaturedItem.Bundle(bundleId))
                    }
                }
            }
            row(Modifier.fillWidth(), Arrangement.spacedBy(10f, FloatPosition.END)) {
                val cosmeticState = mutableStateOf<Cosmetic?>(null)
                text("Add Cosmetic:")
                val input = essentialStateTextInput(
                    cosmeticState,
                    { it?.id ?: "" },
                    { if (it.isBlank()) null else (cosmeticsDataWithChanges.getCosmetic(it) ?: throw StateTextInput.ParseException()) }
                )
                addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName })
                cosmeticState.onChange(stateScope) {
                    val cosmeticId = (it ?: return@onChange).id
                    updateRow(componentIndex) {
                        add(FeaturedItem.Cosmetic(cosmeticId, listOf()))
                    }
                }
            }
        }

    }

    private fun showNewPageModal(pageCollection: FeaturedPageCollection) {
        launchModalFlow(platform.createModalManager()) {
            val width = awaitModal { continuation ->
                object : EssentialModal2(modalManager, false) {

                    private val input = UITextInput("Enter New Width", shadowColor = EssentialPalette.BLACK)
                    private val widthState = input.textState.letState { it.toIntOrNull() }
                    private val errorMessageState = widthState.letState {
                        return@letState when {
                            it == null -> "Not a number!"
                            pageCollection.pages.containsKey(it) -> "Width already exists!"
                            else -> null
                        }
                    }

                    override fun LayoutScope.layoutTitle() {
                        title("Select a width for the new page")
                    }

                    override fun LayoutScope.layoutBody() {
                        box(Modifier.width(106f).height(17f)) {
                            essentialInput(input, errorMessageState = errorMessageState, inputModifier = Modifier.height(10f).color(EssentialPalette.TEXT))
                        }
                    }

                    override fun LayoutScope.layoutButtons() {
                        cancelButton("Cancel")
                        primaryButton("Create", disabled = errorMessageState.letState { it != null }) { replaceWith(continuation.resume(widthState.getUntracked() ?: return@primaryButton)) }
                    }

                }
            }
            pageCollection.update(pageCollection.copy(pages = pageCollection.pages + (width to FeaturedPage(listOf()))))
        }
    }

}
