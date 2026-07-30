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
import gg.essential.gui.about.components.ColoredDivider
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.AbstractConfiguration.AbstractConfigurationSubmenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.addAutoCompleteMenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDangerModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDeleteCosmeticPropertyModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledInputRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick

class ExternalHiddenBoneConfiguration(
    private val state: WardrobeState,
    private val cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    private val cosmetic: Cosmetic,
) : AbstractConfigurationSubmenu<Cosmetic>("properties:${CosmeticPropertyType.EXTERNAL_HIDDEN_BONE.name}", CosmeticPropertyType.EXTERNAL_HIDDEN_BONE.displayName, cosmetic) {

    override fun LayoutScope.layout(modifier: Modifier) {
        val boneHidingProperties = cosmetic.properties<CosmeticProperty.ExternalHiddenBone>()
        column(Modifier.fillWidth().then(modifier), Arrangement.spacedBy(10f)) {
            column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
                for (property in boneHidingProperties) {
                    layoutBone(property)
                }
            }
            labeledInputRow("Add cosmetic:") {
                val input = essentialStateTextInput(
                    mutableStateOf(null),
                    { "" }, // Since we update when we get a valid result, we don't need this
                    { input ->
                        if (input.isBlank())
                            null
                        else (cosmeticsDataWithChanges.getCosmetic(input) ?: throw StateTextInput.ParseException())
                    }
                )
                addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName })
                input
            }.state.onChange(stateScope) { targetCosmetic ->
                if (targetCosmetic != null) {
                    cosmeticsDataWithChanges.addCosmeticProperty(
                        this@ExternalHiddenBoneConfiguration.cosmetic.id,
                        CosmeticProperty.ExternalHiddenBone(targetCosmetic.id.uppercase(), true, CosmeticProperty.ExternalHiddenBone.Data(emptySet()))
                    )
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
                        else (cosmeticsDataWithChanges.getCosmetic(input)?.properties<CosmeticProperty.ExternalHiddenBone>() ?: throw StateTextInput.ParseException())
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

    private fun LayoutScope.layoutBone(setting: CosmeticProperty.ExternalHiddenBone) {
        val expanded = mutableStateOf(false)
        row(Modifier.fillWidth()) {
            row(Modifier.fillRemainingWidth()) {
                ColoredDivider(setting.id)(Modifier.fillRemainingWidth())
                box(Modifier.width(14f).heightAspect(1f)) {
                    icon(expanded.letState { if (it) EssentialPalette.ARROW_UP_7X5 else EssentialPalette.ARROW_DOWN_7X5 })
                }
            }.onLeftClick { expanded.set { !it } }
            box(Modifier.width(10f).heightAspect(1f).hoverTooltip("Remove").hoverScope()) {
                icon(EssentialPalette.CANCEL_5X)
            }.onLeftClick {
                launchModalFlow(platform.createModalManager()) {
                    configuratorDeleteCosmeticPropertyModal(cosmeticsDataWithChanges, cosmetic, setting)
                }
            }
        }
        if_(expanded) {
            for (bone in setting.data.hiddenBones) {
                row(Modifier.fillWidth(), Arrangement.SpaceBetween) {
                    text("- $bone")
                    box(Modifier.width(10f).heightAspect(1f).hoverTooltip("Remove").hoverScope()) {
                        icon(EssentialPalette.CANCEL_5X)
                    }.onLeftClick {
                        launchModalFlow(platform.createModalManager()) {
                            configuratorDangerModal("Delete", "Are you sure you want to remove the bone with id ${setting.id}?")
                            cosmeticsDataWithChanges.updateCosmeticProperty(
                                cosmetic.id,
                                setting,
                                setting.copy(data = setting.data.copy(hiddenBones = setting.data.hiddenBones - bone))
                            )
                        }
                    }
                }
            }
            val targetCosmetic = cosmeticsDataWithChanges.getCosmetic(setting.id)
            if (targetCosmetic != null) {
                labeledInputRow("Add Bone:") {
                    val model = platform.modelLoader.getModel(targetCosmetic, cosmetic.defaultVariantName, AssetLoader.Priority.Blocking).get()
                    val remainingBones = model.bones.byName.keys - setting.data.hiddenBones
                    val boneInput = essentialStateTextInput(
                        mutableStateOf(""),
                        { it },
                        { if (it.isEmpty()) "" else if (remainingBones.contains(it)) it else throw StateTextInput.ParseException() },
                        Modifier.width(100f),
                    )
                    addAutoCompleteMenu(boneInput, listStateOf(*remainingBones.map { it to it }.toTypedArray()))
                    boneInput
                }.state.onChange(stateScope) { boneID ->
                    cosmeticsDataWithChanges.updateCosmeticProperty(cosmetic.id, setting, setting.copy(data = setting.data.copy(hiddenBones = setting.data.hiddenBones + boneID)))
                }
            }
        }
    }

}
