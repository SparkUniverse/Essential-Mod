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
package gg.essential.gui.wardrobe.configuration.cosmetic.settings

import gg.essential.cosmetics.CosmeticId
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledFloatInputRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticSetting
import gg.essential.mod.cosmetics.settings.CosmeticSettingType
import gg.essential.model.Side
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

class PlayerPositionAdjustmentSettingConfiguration(
    cosmeticsData: CosmeticsData,
    modelLoader: ModelLoader,
    cosmeticId: CosmeticId,
    settingsList: MutableListState<CosmeticSetting>,
) : SingletonSettingConfiguration<CosmeticSetting.PlayerPositionAdjustment>(
    CosmeticSettingType.PLAYER_POSITION_ADJUSTMENT,
    cosmeticsData,
    modelLoader,
    cosmeticId,
    settingsList
) {

    override fun getDefault(cosmetic: Cosmetic, availableSides: Set<Side>): CosmeticSetting.PlayerPositionAdjustment? {
        val adjustments = cosmetic.property<CosmeticProperty.PositionRange>()?.data
        return if (adjustments == null) null else CosmeticSetting.PlayerPositionAdjustment(null, true, CosmeticSetting.PlayerPositionAdjustment.Data())
    }

    override fun LayoutScope.layout(cosmetic: Cosmetic, setting: CosmeticSetting.PlayerPositionAdjustment, availableSides: Set<Side>) {
        column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
            val adjustments = cosmetic.property<CosmeticProperty.PositionRange>()?.data
            if (adjustments == null) {
                text("No adjustments found...")
            } else {
                val xMin = adjustments.xMin
                val xMax = adjustments.xMax
                val yMin = adjustments.yMin
                val yMax = adjustments.yMax
                val zMin = adjustments.zMin
                val zMax = adjustments.zMax

                if (xMax != null || xMin != null) {
                    labeledFloatInputRow("X: $xMin to $xMax", mutableStateOf(setting.data.x), min = xMin ?: Float.NEGATIVE_INFINITY, max = xMax ?: Float.POSITIVE_INFINITY).state.onChange(stateScope) {
                        setting.update(setting.copy(data = setting.data.copy(x = it)))
                    }
                }
                if (yMax != null || yMin != null) {
                    labeledFloatInputRow("Y: $yMin to $yMax", mutableStateOf(setting.data.y), min = yMin ?: Float.NEGATIVE_INFINITY, max = yMax ?: Float.POSITIVE_INFINITY).state.onChange(stateScope) {
                        setting.update(setting.copy(data = setting.data.copy(y = it)))
                    }
                }
                if (zMax != null || zMin != null) {
                    labeledFloatInputRow("Z: $zMin to $zMax", mutableStateOf(setting.data.z), min = zMin ?: Float.NEGATIVE_INFINITY, max = zMax ?: Float.POSITIVE_INFINITY).state.onChange(stateScope) {
                        setting.update(setting.copy(data = setting.data.copy(z = it)))
                    }
                }
            }
        }
    }

}
