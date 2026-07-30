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

import gg.essential.cosmetics.CosmeticBundleId
import gg.essential.gui.common.compactFullEssentialToggle
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledBooleanInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledEnumInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledFloatInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledNullableStringInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledStringInputRow
import gg.essential.gui.wardrobe.configuration.cosmetic.settings.*
import gg.essential.mod.Model
import gg.essential.mod.cosmetics.CosmeticBundle
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.settings.CosmeticSettingType

class CosmeticBundleConfiguration(
    state: WardrobeState,
) : AbstractConfiguration<CosmeticBundleId, CosmeticBundle>(
    ConfigurationType.BUNDLES,
    state
) {

    override fun LayoutScope.columnLayout(bundle: CosmeticBundle) {
        val skin = bundle.skin

        labeledStringInputRow("Name:", mutableStateOf(bundle.name)).state.onChange(stateScope) { bundle.update(bundle.copy(name = it)) }
        labeledEnumInputRow("Tier:", bundle.tier) { bundle.update(bundle.copy(tier = it)) }
        labeledFloatInputRow("Discount %:", mutableStateOf(bundle.discountPercent)).state.onChange(stateScope) { bundle.update(bundle.copy(discountPercent = it)) }
        labeledBooleanInputRow("Rotate on Preview:", bundle.rotateOnPreview) { bundle.update(bundle.copy(rotateOnPreview = it)) }
        val hasSkinState = mutableStateOf(skin != null)
        hasSkinState.onChange(stateScope) { bundle.update(bundle.copy(skin = if (it) CosmeticBundle.Skin("bff1570fdf623153e6b4a4d2ca97559b471f1ec776584ceec2ebb8bf0b7ba504", Model.ALEX) else null)) }
        labeledRow("Has skin: ") {
            box(Modifier.childBasedWidth(3f).childBasedHeight(3f).hoverScope()) {
                compactFullEssentialToggle(hasSkinState)
                spacer(1f, 1f)
            }
        }
        if (skin != null) {
            labeledEnumInputRow("Skin model:", skin.model) { bundle.update(bundle.copy(skin = bundle.skin?.copy(model = it))) }
            labeledStringInputRow("Skin hash:", mutableStateOf(skin.hash), Modifier.fillRemainingWidth(), Arrangement.spacedBy(10f)).state.onChange(stateScope) { bundle.update(bundle.copy(skin = bundle.skin?.copy(hash = it))) }
            labeledNullableStringInputRow("Skin name:", mutableStateOf(skin.name)).state.onChange(stateScope) { bundle.update(bundle.copy(skin = bundle.skin?.copy(name = it))) }
        }

        for (slot in CosmeticSlot.values()) {
            labeledRow(slot.id + ":") {
                val initialId = bundle.cosmetics[slot]
                val cosmeticState = mutableStateOf(initialId)
                essentialStateTextInput(
                    cosmeticState,
                    { it ?: "" },
                    { if (it.isBlank()) null else (cosmeticsDataWithChanges.getCosmetic(it)?.id ?: throw StateTextInput.ParseException()) }
                )
                cosmeticState.onChange(stateScope) { cosmeticId ->
                    bundle.update(
                        if (cosmeticId == null) {
                            bundle.copy(cosmetics = bundle.cosmetics - slot, settings = if (initialId == null) bundle.settings else bundle.settings - initialId)
                        } else {
                            val newCosmetics = bundle.cosmetics.toMutableMap()
                            // All slots that the equipped cosmetic is mutually exclusive with
                            for (mutuallyExclusiveSlot in cosmeticsDataWithChanges.getCosmetic(cosmeticId)?.mutuallyExclusiveWith ?: emptySet()) {
                                newCosmetics.remove(mutuallyExclusiveSlot)
                            }
                            // All other slots with an equipped cosmetic that is mutually exclusive with the newly equipped slot
                            for ((equippedSlot, equippedCosmeticId) in newCosmetics.entries) {
                                if (cosmeticsDataWithChanges.getCosmetic(equippedCosmeticId)?.mutuallyExclusiveWith?.contains(slot) == true) {
                                    newCosmetics.remove(equippedSlot)
                                }
                            }

                            newCosmetics[slot] = cosmeticId
                            bundle.copy(cosmetics = newCosmetics, settings = bundle.settings - (bundle.settings.keys - newCosmetics.values.toSet()))
                        }
                    )
                }
            }

            val cosmeticId = bundle.cosmetics[slot]

            if (cosmeticId != null) {
                val settings = mutableListStateOf(*(bundle.settings[cosmeticId] ?: listOf()).toTypedArray())
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
                    bundle.update(bundle.copy(settings = if (it.isEmpty()) bundle.settings - cosmeticId else bundle.settings + (cosmeticId to it)))
                }
            }
        }
    }

}
