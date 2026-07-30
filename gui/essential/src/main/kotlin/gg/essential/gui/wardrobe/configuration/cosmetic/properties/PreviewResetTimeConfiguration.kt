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

import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.checkbox
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledDoubleInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.CosmeticsDataWithChanges
import gg.essential.network.cosmetics.Cosmetic

class PreviewResetTimeConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.PreviewResetTime>(
    CosmeticPropertyType.PREVIEW_RESET_TIME,
    CosmeticProperty.PreviewResetTime::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.PreviewResetTime) {
        val largeTime = 1E10
        val time = property.data.time
        labeledRow("Infinite loop:") {
            checkbox(time >= largeTime) { enabled ->
                property.update(property.copy(data = property.data.copy(time = if (enabled) largeTime else 3.0)))
            }
        }
        if (time < largeTime) {
            labeledDoubleInputRow("Time", mutableStateOf(time)).state.onSetValue(stateScope) { property.update(property.copy(data = property.data.copy(time = it))) }
        }
    }

}
