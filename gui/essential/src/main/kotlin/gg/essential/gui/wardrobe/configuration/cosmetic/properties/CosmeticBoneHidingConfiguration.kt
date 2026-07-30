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
package gg.essential.gui.wardrobe.configuration.cosmetic.properties

import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.Checkbox
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.configuration.AbstractConfiguration.AbstractConfigurationSubmenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.addAutoCompleteMenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDeleteCosmeticPropertyModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorNoticeModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledInputRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick

class CosmeticBoneHidingConfiguration(
    private val cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    private val cosmetic: Cosmetic,
) : AbstractConfigurationSubmenu<Cosmetic>("properties:${CosmeticPropertyType.COSMETIC_BONE_HIDING.name}", CosmeticPropertyType.COSMETIC_BONE_HIDING.displayName, cosmetic) {

    private val boneHidingProperties = cosmetic.properties<CosmeticProperty.CosmeticBoneHiding>()

    override fun LayoutScope.layout(modifier: Modifier) {
        column(Modifier.fillWidth().then(modifier), Arrangement.spacedBy(10f)) {
            row(Modifier.fillWidth(), Arrangement.SpaceBetween) {
                idColumn(boneHidingProperties)
                checkboxColumn("Head", { head }, { copy(head = it) })
                checkboxColumn("Body", { body }, { copy(body = it) })
                checkboxColumn("Arms", { arms }, { copy(arms = it) })
                checkboxColumn("Legs", { legs }, { copy(legs = it) })
                removeColumn(boneHidingProperties)
            }
            column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
                text("Add new cosmetic bone hiding setting")
                row(Modifier.fillWidth(), Arrangement.SpaceAround) {
                    val input = essentialStateTextInput(
                        mutableStateOf(null),
                        { it?.id ?: "" },
                        { input ->
                            if (input.isBlank())
                                null
                            else (cosmeticsDataWithChanges.getCosmetic(input) ?: throw StateTextInput.ParseException())
                        }
                    )
                    addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName })

                    val headCheckbox = checkbox(false)
                    val bodyCheckbox = checkbox(false)
                    val armsCheckbox = checkbox(false)
                    val legsCheckbox = checkbox(false)

                    addButton(input, boneHidingProperties, headCheckbox, bodyCheckbox, armsCheckbox, legsCheckbox)
                }
            }
            divider()
            labeledInputRow("Copy from:") {
                val input = essentialStateTextInput(
                    mutableStateOf(null),
                    { "" }, // Since we update when we get a valid result, we don't need this
                    { input ->
                        if (input.isBlank())
                            null
                        else (cosmeticsDataWithChanges.getCosmetic(input)?.properties<CosmeticProperty.CosmeticBoneHiding>() ?: throw StateTextInput.ParseException())
                    }
                )
                addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName })
                input
            }.state.onChange(stateScope) { propertyList ->
                if (propertyList != null) {
                    val newPropertyList = cosmetic.allProperties.toMutableList()
                    newPropertyList.removeAll(boneHidingProperties)
                    newPropertyList.addAll(propertyList)
                    cosmeticsDataWithChanges.updateCosmetic(cosmetic.id, cosmetic.copy(base = cosmetic.base.copy(allProperties = newPropertyList)))
                }
            }
        }
    }

    private fun LayoutScope.checkboxColumn(
        name: String,
        initialState: CosmeticProperty.CosmeticBoneHiding.Data.() -> Boolean,
        update: CosmeticProperty.CosmeticBoneHiding.Data.(Boolean) -> CosmeticProperty.CosmeticBoneHiding.Data
    ) {
        column(Arrangement.spacedBy(5f)) {
            text(name)
            for (property in boneHidingProperties) {
                checkbox(property.data.initialState()) {
                    cosmeticsDataWithChanges.updateCosmeticProperty(
                        cosmetic.id,
                        property,
                        property.copy(data = property.data.update(it))
                    )
                }
            }
        }
    }

    private fun LayoutScope.addButton(
        input: StateTextInput<Cosmetic?>,
        properties: List<CosmeticProperty.CosmeticBoneHiding>,
        headCheckbox: Checkbox,
        bodyCheckbox: Checkbox,
        armsCheckbox: Checkbox,
        legsCheckbox: Checkbox,
    ) {
        IconButton(EssentialPalette.PLUS_5X, tooltipText = "Add")(Modifier.width(9f).heightAspect(1f)).onLeftClick {
            USound.playButtonPress()
            val cosmeticId = input.getText()
            if (cosmeticsDataWithChanges.getCosmetic(cosmeticId.uppercase()) == null) {
                launchModalFlow(platform.createModalManager()) {
                    configuratorNoticeModal("Invalid Cosmetic ID", "The ID you entered is not a valid cosmetic ID")
                }
                return@onLeftClick
            }

            if (properties.any { it.id == cosmeticId }) {
                launchModalFlow(platform.createModalManager()) {
                    configuratorNoticeModal("Duplicate Cosmetic ID", "The target cosmetic already has a bone hiding setting. Update it instead of adding a new one.")
                }
                return@onLeftClick
            }

            cosmeticsDataWithChanges.addCosmeticProperty(
                cosmetic.id,
                CosmeticProperty.CosmeticBoneHiding(
                    cosmeticId,
                    true,
                    CosmeticProperty.CosmeticBoneHiding.Data(
                        head = headCheckbox.isChecked.getUntracked(),
                        body = bodyCheckbox.isChecked.getUntracked(),
                        arms = armsCheckbox.isChecked.getUntracked(),
                        legs = legsCheckbox.isChecked.getUntracked(),
                    )
                )
            )

            input.state.set(null)
            headCheckbox.isChecked.set(false)
            bodyCheckbox.isChecked.set(false)
            armsCheckbox.isChecked.set(false)
            legsCheckbox.isChecked.set(false)
        }

    }

    private fun LayoutScope.removeColumn(properties: List<CosmeticProperty.CosmeticBoneHiding>) {
        column(Arrangement.spacedBy(7f)) {
            text("Remove")
            for (property in properties) {
                icon(EssentialPalette.CANCEL_7X, Modifier.hoverTooltip("Remove").hoverScope()).onLeftClick {
                    launchModalFlow(platform.createModalManager()) {
                        configuratorDeleteCosmeticPropertyModal(cosmeticsDataWithChanges, cosmetic, property)
                    }
                }
            }
        }
    }

    private fun LayoutScope.idColumn(properties: List<CosmeticProperty.CosmeticBoneHiding>) {
        column(Arrangement.spacedBy(6f)) {
            text("ID")
            for (property in properties) {
                text(property.id)
            }
        }
    }
}
