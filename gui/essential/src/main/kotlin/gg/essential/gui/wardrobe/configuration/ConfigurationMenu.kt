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

import gg.essential.elementa.dsl.*
import gg.essential.gui.EssentialPalette
import gg.essential.gui.about.components.ColoredDivider
import gg.essential.gui.common.EssentialCollapsibleSearchbar
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDangerModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorButton
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick

class ConfigurationMenu(
    private val state: WardrobeState,
) : LayoutDslComponent {

    private val cosmeticsDataWithChanges = state.cosmeticsManager.cosmeticsDataWithChanges!!
    private val currentConfigurationType = mutableStateOf<ConfigurationType<*, *>?>(null)
    private val currentTabName = currentConfigurationType.letState { tab -> tab?.displayPlural?.let { "Configuring $it" } ?: "Select something to configure" }
    private val backButtonState = currentConfigurationType.letState { if (it == null) "Close Editing Menu" else "Back to selection menu" }

    override fun LayoutScope.layout(modifier: Modifier) {
        column(Modifier.fillParent().alignBoth(Alignment.Center), Arrangement.spacedBy(3f, FloatPosition.START)) {
            box(Modifier.fillWidth().height(20f)) {
                text(currentTabName)
            }
            divider()
            row(Modifier.fillWidth().fillRemainingHeight()) {
                val scroller = scrollable(Modifier.fillWidth(padding = 10f).fillHeight(), vertical = true) {
                    column(Modifier.fillWidth(), Arrangement.spacedBy(3f)) {
                        bind(currentConfigurationType) {
                            if (it == null) {
                                homeView()
                            } else {
                                tabView(it)
                            }
                        }
                    }
                }
                val scrollbar = box(Modifier.width(2f).fillHeight().color(EssentialPalette.LIGHTEST_BACKGROUND).hoverColor(EssentialPalette.SCROLLBAR).hoverScope())
                scroller.setVerticalScrollBarComponent(scrollbar, true)
            }
            divider()
            row(Modifier.fillWidth().childBasedMaxHeight(3f), Arrangement.spacedBy(5f, FloatPosition.CENTER)) {
                configuratorButton(backButtonState, Modifier.fillWidth(0.45f)) {
                    if (currentConfigurationType.getUntracked() == null) state.editingMenuOpen.set(false)
                    else currentConfigurationType.set(null)
                }
            }
        }
    }

    private fun LayoutScope.homeView() {
        for (tab in ConfigurationType.values()) {
            configuratorButton(tab.displayPlural) { currentConfigurationType.set(tab) }
        }
        divider()
        configuratorButton("Clear all cosm. unlock info") {
            state.cosmeticsManager.clearUnlockedCosmetics()
        }
        configuratorButton("Unlock all cosmetics") {
            state.cosmeticsManager.unlockAllCosmetics()
        }
        configuratorButton("Reset All Changes") {
            launchModalFlow(platform.createModalManager()) {
                configuratorDangerModal("Reset ALL", "Are you sure you want to reset ALL changes to their initial loaded state?")
                cosmeticsDataWithChanges.resetLocalChanges()
            }
        }
    }

    private fun <I, T> LayoutScope.tabView(type: ConfigurationType<I, T>) {
        val (editingIdState, _, items) = type.stateSupplier(state)

        spacer(height = 10f)

        val createHandler = type.createHandler
        if (createHandler != null) {
            configuratorButton("Create New ${type.displaySingular}", style = OutlineButtonStyle.BLUE) {
                createHandler(cosmeticsDataWithChanges)
            }
        }

        val searchBar by EssentialCollapsibleSearchbar()()
        val filteredGroups = memo {
            val search = searchBar.textContentV2()
            val list = items()
                .filter { item -> type.idAndNameMapper(item).let { (id, name) -> id.toString().contains(search, ignoreCase = true) || name.contains(search, ignoreCase = true) } }
                .sortedWith(type.comparator)
            type.groupingSupplier(list)
        }.toListState()

        spacer(height = 10f)

        fun LayoutScope.itemButton(item: T) {
            val (id, name) = type.idAndNameMapper(item)
            configuratorButton(name) {
                editingIdState.set(id)
            }
        }

        forEach(filteredGroups) { grouping ->
            when (grouping) {
                is Single<T> -> itemButton(grouping.item)
                is Multi<T> -> {
                    val expanded = mutableStateOf(false)
                    row(Modifier.fillWidth()) {
                        ColoredDivider(grouping.name)(Modifier.fillRemainingWidth())
                        box(Modifier.width(14f).heightAspect(1f)) {
                            icon(expanded.letState { if (it) EssentialPalette.ARROW_UP_7X5 else EssentialPalette.ARROW_DOWN_7X5 })
                        }
                    }.onLeftClick { expanded.set { !it } }
                    if_(expanded) {
                        if (grouping.description != null) {
                            wrappedText(grouping.description, Modifier.fillWidth())
                        }
                        for (item in grouping.items) {
                            itemButton(item)
                        }
                    }
                }
            }

        }
        spacer(height = 10f)
    }

    sealed interface Grouping<T>

    data class Single<T>(val item: T) : Grouping<T>

    data class Multi<T>(val name: String, val description: String? = null, val items: List<T>) : Grouping<T>

}
