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

import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDangerModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorButton
import gg.essential.util.GuiEssentialPlatform.Companion.platform

sealed class AbstractConfiguration<I, T>(
    private val configurationType: ConfigurationType<I, T>,
    protected val state: WardrobeState,
) : LayoutDslComponent {

    protected val cosmeticsDataWithChanges = state.cosmeticsManager.cosmeticsDataWithChanges!!
    protected val referenceHolder = ReferenceHolderImpl()
    private val stateTriple = configurationType.stateSupplier(state)
    private val editingIdState = stateTriple.first
    private val editingState = stateTriple.second
    private val submenuMapState = editingState.letState { editing -> if (editing != null) getSubmenus(editing).associateBy { it.id } else mapOf() }
    private val currentSubmenuStack = mutableListStateOf<String?>(null)
    private val currentSubmenuId = currentSubmenuStack.letState { it.lastOrNull() }

    override fun LayoutScope.layout(modifier: Modifier) {
        column(Modifier.fillParent().alignBoth(Alignment.Center), Arrangement.spacedBy(0f, FloatPosition.CENTER)) {
            bind({ editingState()?.id() to currentSubmenuId() }) { (id, submenuId) ->
                val submenuState = memo { submenuMapState()[submenuId] }
                if (id != null) {
                    column(Modifier.fillWidth(padding = 5f).childBasedHeight(3f), Arrangement.spacedBy(3f, FloatPosition.CENTER)) {
                        text("Editing ${configurationType.displaySingular}")
                        text({ (editingState()?.name() ?: "") + " ($id)" }, truncateIfTooSmall = true)
                        ifNotNull(submenuState) { submenu ->
                            text("Submenu: ${submenu.name}")
                            if (submenu.description != null) {
                                wrappedText(submenu.description, centered = true, modifier = Modifier.color(EssentialPalette.TEXT_DISABLED))
                            }
                        }
                    }
                    divider()
                    row(Modifier.fillWidth().fillRemainingHeight()) {
                        val scrollComponent = scrollable(Modifier.fillRemainingWidth().fillHeight(), vertical = true) {
                            column(Modifier.fillWidth(padding = 10f).alignVertical(Alignment.Start), Arrangement.spacedBy(3f)) {
                                spacer(height = 5f)
                                ifNotNull(submenuState) { submenu ->
                                    submenu()
                                } `else` {
                                    ifNotNull(editingState) { currentlyEditing ->
                                        columnLayout(currentlyEditing)
                                    }
                                }
                                spacer(height = 5f)
                            }
                        }
                        val scrollbar = box(Modifier.width(2f).fillHeight().color(EssentialPalette.LIGHTEST_BACKGROUND).hoverColor(EssentialPalette.SCROLLBAR).hoverScope())
                        scrollComponent.setVerticalScrollBarComponent(scrollbar, true)
                    }
                    divider()
                    row(Modifier.fillWidth().childBasedMaxHeight(3f), Arrangement.spacedBy(5f, FloatPosition.CENTER)) {
                        configuratorButton("Reset", disabled = stateOf(!configurationType.canReset), modifier = Modifier.fillWidth(0.3f)) {
                            attemptReset(id)
                        }
                        configuratorButton("Delete", disabled = stateOf(!configurationType.canUpdate), modifier = Modifier.fillWidth(0.3f)) {
                            attemptDelete(id)
                        }
                        if_({ submenuState() != null }) {
                            configuratorButton("Back", Modifier.fillWidth(0.3f)) {
                                currentSubmenuStack.set { if (it.isNotEmpty()) it.removeAt(it.size - 1) else it }
                            }
                        } `else` {
                            configuratorButton("Close", Modifier.fillWidth(0.3f)) {
                                editingIdState.set(null)
                            }
                        }
                    }
                } else {
                    column(Modifier.fillRemainingHeight(), Arrangement.spacedBy(5f, FloatPosition.CENTER)) {
                        text("${configurationType.displaySingular} with id")
                        text("${editingIdState.getUntracked().toString()} not found")
                        configuratorButton("Close") {
                            editingIdState.set(null)
                        }
                    }
                }
            }
        }
    }

    protected open fun LayoutScope.columnLayout(editing: T) {
        submenuSelection(editing)
    }

    protected fun LayoutScope.submenuSelection(editing: T, predicate: (AbstractConfigurationSubmenu<T>) -> Boolean = { true }) {
        val submenus = getSubmenus(editing).filter(predicate)
        text(if (submenus.isEmpty()) "No submenus..." else "Select a submenu:")
        spacer(height = 10f)
        for (submenu in submenus) {
            val modifier = if (submenu.description == null) Modifier else Modifier.hoverTooltip(submenu.description, wrapAtWidth = 300f, position = EssentialTooltip.Position.LEFT)
            configuratorButton("Edit ${submenu.name}", modifier) {
                currentSubmenuStack.add(submenu.id)
            }
        }
    }

    protected fun selectSubmenu(id: String) {
        currentSubmenuStack.add(id)
    }

    protected open fun attemptDelete(toDelete: I) {
        launchModalFlow(platform.createModalManager()) {
            configuratorDangerModal("Delete", "Are you sure you want to delete ${configurationType.displaySingular} with id $toDelete?")
            toDelete.update(null)
            editingIdState.set(null)
        }
    }

    protected open fun attemptReset(toReset: I) {
        launchModalFlow(platform.createModalManager()) {
            configuratorDangerModal("Reset", "Are you sure you want to reset ${configurationType.displaySingular} with id $toReset back to initial loaded state?")
            toReset.reset()
        }
    }

    protected open fun getSubmenus(editing: T): List<AbstractConfigurationSubmenu<T>> = listOf()

    protected fun T.update(newItem: T?) = id().update(newItem)

    @JvmName("updateById")
    protected fun I.update(newItem: T?) = configurationType.updateHandler?.invoke(cosmeticsDataWithChanges, this, newItem)

    protected fun T.reset() = id().reset()

    @JvmName("resetById")
    protected fun I.reset() = configurationType.resetHandler?.invoke(cosmeticsDataWithChanges, this)

    protected fun T.idAndName() = configurationType.idAndNameMapper(this)

    protected fun T.id() = idAndName().first

    protected fun T.name() = idAndName().second

    abstract class AbstractConfigurationSubmenu<T>(val id: String, val name: String, val description: String? = null, val currentlyEditing: T) : LayoutDslComponent {

        constructor(id: String, name: String, currentlyEditing: T) : this(id, name, null, currentlyEditing)

        abstract override fun LayoutScope.layout(modifier: Modifier)

    }

}
