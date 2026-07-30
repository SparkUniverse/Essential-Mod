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

import gg.essential.cosmetics.CosmeticId
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.common.FullEssentialToggle
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.compactFullEssentialToggle
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialIntInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledEnumInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledIntInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledListInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledManagedNullableISODateInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledStringInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorButton
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorCollapsibleSection
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDangerModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDeleteCosmeticPropertyModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorWrappedLabeledRow
import gg.essential.gui.wardrobe.configuration.cosmetic.properties.*
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.CosmeticCategory
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick

class CosmeticConfiguration(
    state: WardrobeState,
) : AbstractConfiguration<CosmeticId, Cosmetic>(
    ConfigurationType.COSMETICS,
    state
) {

    override fun LayoutScope.columnLayout(cosmetic: Cosmetic) {
        submenuSelection(cosmetic) {
            it.id == "properties" || it.id == "general" || it.id == "category" || it.id.startsWith("bone-hiding")
        }
    }

    override fun getSubmenus(cosmetic: Cosmetic): List<AbstractConfigurationSubmenu<Cosmetic>> {
        return listOf(
            // First level menu
            CosmeticGeneralConfigurationSubmenu(cosmetic),
            CosmeticBoneHidingConfigurationSubmenu(cosmetic, true),
            CosmeticBoneHidingConfigurationSubmenu(cosmetic, false),
            CosmeticPropertiesConfigurationSubmenu(cosmetic),
            CosmeticCategoryConfigurationSubmenu(cosmetic),
            // Properties
            CosmeticBoneHidingConfiguration(cosmeticsDataWithChanges, cosmetic),
            ExternalHiddenBoneConfiguration(state, cosmeticsDataWithChanges, cosmetic),
            ArmorHandlingConfiguration(cosmeticsDataWithChanges, cosmetic),
            ArmorHandlingV2Configuration(cosmeticsDataWithChanges, cosmetic),
            PlayerPositionAdjustmentPropertyConfiguration(cosmeticsDataWithChanges, cosmetic),
            InterruptsEmoteConfiguration(cosmeticsDataWithChanges, cosmetic),
            PreviewResetTimeConfiguration(cosmeticsDataWithChanges, cosmetic),
            LocalizationConfiguration(cosmeticsDataWithChanges, cosmetic),
            TransitionDelayConfiguration(cosmeticsDataWithChanges, cosmetic),
            RequiresUnlockActionConfiguration(cosmeticsDataWithChanges, cosmetic),
            VariantsPropertyConfiguration(cosmeticsDataWithChanges, cosmetic),
            DefaultSidePropertyConfiguration(cosmeticsDataWithChanges, cosmetic),
            MutuallyExclusivePropertyConfiguration(cosmeticsDataWithChanges, cosmetic),
            HidesAllOtherCosmeticsOrItemsConfiguration(cosmeticsDataWithChanges, cosmetic),
            LocksPlayerRotationConfiguration(cosmeticsDataWithChanges, cosmetic),
        )
    }

    private fun LayoutScope.labeledNullableIntInputRow(
        label: String,
        state: MutableState<Int?>
    ) = labeledInputRow(label) {
        essentialStateTextInput(
            state,
            { it?.toString() ?: "" },
            {
                try {
                    if (it.isEmpty()) null
                    else it.toInt()
                } catch (e: NumberFormatException) {
                    throw StateTextInput.ParseException()
                }
            },
            Modifier.width(40f),
        )
    }

    private inner class CosmeticPropertiesConfigurationSubmenu(val cosmetic: Cosmetic) : AbstractConfigurationSubmenu<Cosmetic>("properties", "Properties", cosmetic) {

        override fun LayoutScope.layout(modifier: Modifier) {
            CosmeticPropertyType.values().sortedBy { it.singleton }.forEach { type ->
                if (type.singleton) {
                    val enabledState = mutableStateOf(cosmetic.properties.any { it.type == type })
                    enabledState.onChange(stateScope) { cosmeticsDataWithChanges.setCosmeticSingletonPropertyEnabled(cosmetic.id, type, it) }
                    row(Modifier.fillWidth(padding = 10f).height(20f)) {
                        configuratorButton(type.displayName, disabled = !enabledState, modifier = Modifier.fillRemainingWidth()) {
                            // Create the setting if it does not already exist
                            if (cosmetic.allProperties.none { it.type == type }) {
                                cosmeticsDataWithChanges.setCosmeticSingletonPropertyEnabled(cosmetic.id, type, true)
                            }
                            selectSubmenu("properties:${type.name}")
                        }
                        spacer(width = 10f)
                        box(Modifier.width(35f).fillHeight()) {
                            FullEssentialToggle(enabledState)()
                        }
                    }
                } else {
                    configuratorButton(type.displayName) {
                        selectSubmenu("properties:${type.name}")
                    }
                }
            }
        }

    }

    private inner class CosmeticGeneralConfigurationSubmenu(val cosmetic: Cosmetic) : AbstractConfigurationSubmenu<Cosmetic>("general", "General", cosmetic) {
        override fun LayoutScope.layout(modifier: Modifier) {
            labeledListInputRow(
                "Slot:",
                cosmetic.slot,
                listStateOf(*CosmeticSlot.values().toTypedArray()).mapEach { EssentialDropDown.Option(it.id, it) }) {
                cosmeticsDataWithChanges.setCosmeticSlot(cosmetic.id, it)
            }
            labeledEnumInputRow("Tier:", cosmetic.tier) { cosmeticsDataWithChanges.setCosmeticTier(cosmetic.id, it) }
            labeledStringInputRow("Display Name:", mutableStateOf(cosmetic.displayName)).state.onChange(stateScope) { cosmeticsDataWithChanges.setCosmeticDisplayName(cosmetic.id, it) }
            labeledNullableIntInputRow("Price:", mutableStateOf(cosmetic.price)).state.onChange(stateScope) { cosmeticsDataWithChanges.setCosmeticPriceCoins(cosmetic.id, it) }
            text("Tags:", Modifier.alignHorizontal(Alignment.Start))
            if (cosmetic.tags.isEmpty()) {
                text("No tags...", Modifier.alignHorizontal(Alignment.Start))
            } else {
                for (tag in cosmetic.tags) {
                    labeledRow("- $tag") {
                        box(Modifier.width(10f).height(10f)) {
                            icon(EssentialPalette.CANCEL_5X)
                        }.onLeftClick {
                            cosmeticsDataWithChanges.setCosmeticTags(cosmetic.id, cosmetic.tags - tag)
                        }
                    }
                }
            }

            labeledStringInputRow("Add tag:", mutableStateOf("")).state.onChange(stateScope) { cosmeticsDataWithChanges.setCosmeticTags(cosmetic.id, cosmetic.tags + it) }
            val isLegacyState = mutableStateOf(cosmetic.isLegacy)
            isLegacyState.onChange(stateScope) {
                cosmeticsDataWithChanges.setCosmeticTags(cosmetic.id, if (it) cosmetic.tags + "LEGACY" else cosmetic.tags - "LEGACY")
            }
            labeledRow("Is legacy: ") {
                box(Modifier.childBasedWidth(3f).childBasedHeight(3f).hoverScope()) {
                    compactFullEssentialToggle(isLegacyState)
                    spacer(1f, 1f)
                }
            }
            labeledManagedNullableISODateInputRow("Available After:", mutableStateOf(cosmetic.availableAfter)).state.onChange(stateScope) {
                cosmeticsDataWithChanges.setCosmeticAvailable(
                    cosmetic.id,
                    it,
                    cosmetic.availableUntil,
                    cosmetic.showTimerAfter
                )
            }
            labeledManagedNullableISODateInputRow("Available Until:", mutableStateOf(cosmetic.availableUntil)).state.onChange(stateScope) {
                cosmeticsDataWithChanges.setCosmeticAvailable(
                    cosmetic.id,
                    cosmetic.availableAfter,
                    it,
                    cosmetic.showTimerAfter
                )
            }
            labeledManagedNullableISODateInputRow("Show Timer After:", mutableStateOf(cosmetic.showTimerAfter)).state.onChange(stateScope) {
                cosmeticsDataWithChanges.setCosmeticAvailable(
                    cosmetic.id,
                    cosmetic.availableAfter,
                    cosmetic.availableUntil,
                    it
                )
            }
            labeledIntInputRow("Default Sort Weight", mutableStateOf(cosmetic.defaultSortWeight)).state.onChange(stateScope) { cosmeticsDataWithChanges.setCosmeticDefaultSortWeight(cosmetic.id, it) }
        }

    }

    private inner class CosmeticCategoryConfigurationSubmenu(val cosmetic: Cosmetic) : AbstractConfigurationSubmenu<Cosmetic>("category", "Categories", cosmetic) {
        override fun LayoutScope.layout(modifier: Modifier) {
            column(Modifier.fillWidth().childBasedHeight(), Arrangement.spacedBy(5f)) {
                val categories = cosmeticsDataWithChanges.categories.filter { cosmetic.categories.containsKey(it.id) }.mapEach { Pair(it, cosmetic.categories[it.id]!!) }

                fun LayoutScope.categoryLine(category: CosmeticCategory, sortWeight: Int) {
                    row(Modifier.fillWidth()) {
                        row(Modifier.fillRemainingWidth(), Arrangement.SpaceAround) {
                            text(category.displayNames["en_us"]!!)
                            essentialIntInput(mutableStateOf(sortWeight)).state.onChange(stateScope) {
                                cosmeticsDataWithChanges.addToCategory(cosmetic.id, category.id, it)
                            }
                        }
                        IconButton(EssentialPalette.CANCEL_7X, "Remove From Category").onLeftClick {
                            cosmeticsDataWithChanges.removeCosmeticFromCategory(cosmetic.id, category.id)
                        }()
                    }

                }

                forEach(categories) {
                    categoryLine(it.first, it.second)
                }
                divider()

                val excludedCategories = State {
                    cosmeticsDataWithChanges.categories() - categories().map { it.first }.toSet()
                }.toListState()

                if_({ excludedCategories().isNotEmpty() }) {
                    val dropDown = EssentialDropDown(excludedCategories.get()[0], excludedCategories.mapEach { EssentialDropDown.Option(it.displayNames["en_us"]!!, it) })
                    box(Modifier.fillWidth()) {
                        row(Modifier.fillWidth(), Arrangement.SpaceAround) {
                            dropDown()

                            IconButton(EssentialPalette.PLUS_7X, "Add to category").onLeftClick {
                                val category = dropDown.selectedOption.getUntracked().value
                                cosmeticsDataWithChanges.addToCategory(
                                    cosmetic.id,
                                    category.id,
                                    0
                                )
                            }()
                        }
                    }
                }
            }
        }

    }

    private inner class CosmeticBoneHidingConfigurationSubmenu(val cosmetic: Cosmetic, val outgoing: Boolean) : AbstractConfigurationSubmenu<Cosmetic>(
        if (outgoing) "bone-hiding-out" else "bone-hiding-in",
        if (outgoing) "Hiding other cosmetics" else "Hiding this cosmetic",
        if (outgoing) "Allows you to hide OTHER cosmetic's parts, when THIS cosmetic is equipped."
        else "Allows you to hide THIS cosmetic's parts, when OTHER cosmetics are equipped.",
        cosmetic
    ) {

        val targetBoneHidingProperties = if (outgoing) {
            mutableStateOf(mapOf(cosmetic to cosmetic.properties<CosmeticProperty.CosmeticBoneHiding>()).entries.toList())
        } else {
            cosmeticsDataWithChanges.cosmetics.letState {
                it.associateWith { it.properties<CosmeticProperty.CosmeticBoneHiding>().filter { it.id == cosmetic.id } }.entries.toList()
            }
        }.toListState()

        val targetExternalHiddenBoneProperties = if (outgoing) {
            mutableStateOf(mapOf(cosmetic to cosmetic.properties<CosmeticProperty.ExternalHiddenBone>()).entries.toList())
        } else {
            cosmeticsDataWithChanges.cosmetics.letState {
                it.associateWith { it.properties<CosmeticProperty.ExternalHiddenBone>().filter { it.id == cosmetic.id } }.entries.toList()
            }
        }.toListState()

        override fun LayoutScope.layout(modifier: Modifier) {
            if (outgoing) {
                configuratorWrappedLabeledRow("You can hide OTHER cosmetics either by body part or by hiding their specific bones,")
            } else {
                configuratorWrappedLabeledRow("You can hide THIS cosmetic's body parts or specific bones, for each other equipped cosmetic.")
            }
            configuratorCollapsibleSection(
                "By body part",
                if (outgoing) "All cosmetics configured will have the selected parts hidden when this cosmetic is equipped"
                else "When any of the configured cosmetics are equipped, the selected parts of this cosmetic will be hidden",
                startExpanded = true,
            ) {
                configuratorWrappedLabeledRow("Cosmetic ID:", Arrangement.spacedBy(3f)) {
                    box(Modifier.width(9f).hoverScope().hoverTooltip("Head", position = EssentialTooltip.Position.ABOVE)) { text("H") }
                    box(Modifier.width(9f).hoverScope().hoverTooltip("Body", position = EssentialTooltip.Position.ABOVE)) { text("B") }
                    box(Modifier.width(9f).hoverScope().hoverTooltip("Arms", position = EssentialTooltip.Position.ABOVE)) { text("A") }
                    box(Modifier.width(9f).hoverScope().hoverTooltip("Legs", position = EssentialTooltip.Position.ABOVE)) { text("L") }
                    spacer(width = 7f)
                }
                spacer(height = 3f)
                forEach(targetBoneHidingProperties) { (cosm, props) ->
                    for (prop in props.sortedBy { it.id }) {
                        val id = if (outgoing) prop.id else cosm.id
                        configuratorWrappedLabeledRow("- $id", Arrangement.spacedBy(3f), {
                            launchModalFlow(platform.createModalManager()) {
                                configuratorDeleteCosmeticPropertyModal(cosmeticsDataWithChanges, cosm, prop)
                            }
                        }) {
                            checkboxAlt(mutableStateOf(prop.data.head).apply {
                                onChange(referenceHolder) {
                                    cosmeticsDataWithChanges.updateCosmeticProperty(cosm.id, prop, prop.copy(data = prop.data.copy(head = it)))
                                }
                            })
                            checkboxAlt(mutableStateOf(prop.data.body).apply {
                                onChange(referenceHolder) {
                                    cosmeticsDataWithChanges.updateCosmeticProperty(cosm.id, prop, prop.copy(data = prop.data.copy(body = it)))
                                }
                            })
                            checkboxAlt(mutableStateOf(prop.data.arms).apply {
                                onChange(referenceHolder) {
                                    cosmeticsDataWithChanges.updateCosmeticProperty(cosm.id, prop, prop.copy(data = prop.data.copy(arms = it)))
                                }
                            })
                            checkboxAlt(mutableStateOf(prop.data.legs).apply {
                                onChange(referenceHolder) {
                                    cosmeticsDataWithChanges.updateCosmeticProperty(cosm.id, prop, prop.copy(data = prop.data.copy(legs = it)))
                                }
                            })
                        }
                    }
                }
            }
            configuratorCollapsibleSection(
                "By specific bone",
                if (outgoing) "All cosmetics configured will have the selected bones hidden when this cosmetic is equipped"
                else "When any of the configured cosmetics are equipped, the selected bones of this cosmetic will be hidden",
                startExpanded = true,
            ) {
                forEach(targetExternalHiddenBoneProperties) { (cosm, props) ->
                    for (prop in props.sortedBy { it.id }) {
                        val id = if (outgoing) prop.id else cosm.id
                        configuratorCollapsibleSection("- $id", hasDividerLine = false, startExpanded = true, removeAction = {
                            launchModalFlow(platform.createModalManager()) {
                                configuratorDeleteCosmeticPropertyModal(cosmeticsDataWithChanges, cosm, prop)
                            }
                        }) {
                            for (hiddenBone in prop.data.hiddenBones.sorted()) {
                                configuratorWrappedLabeledRow("  - $hiddenBone", removeAction = {
                                    launchModalFlow(platform.createModalManager()) {
                                        configuratorDangerModal("Delete", "Are you sure you want to remove the bone with id ${prop.id}?")
                                        cosmeticsDataWithChanges.updateCosmeticProperty(
                                            cosm.id,
                                            prop,
                                            prop.copy(data = prop.data.copy(hiddenBones = prop.data.hiddenBones - hiddenBone))
                                        )
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

}
