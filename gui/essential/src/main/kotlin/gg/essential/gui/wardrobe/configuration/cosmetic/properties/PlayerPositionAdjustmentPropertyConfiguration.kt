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

import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledInputRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

class PlayerPositionAdjustmentPropertyConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.PositionRange>(
    CosmeticPropertyType.POSITION_RANGE,
    CosmeticProperty.PositionRange::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.PositionRange) {
        labeledNullableFloatInputRow("Min x:", mutableStateOf(property.data.xMin)).state.onChange(stateScope) { property.update(property.copy(data = property.data.copy(xMin = it))) }
        labeledNullableFloatInputRow("Max x:", mutableStateOf(property.data.xMax)).state.onChange(stateScope) { property.update(property.copy(data = property.data.copy(xMax = it))) }
        labeledNullableFloatInputRow("Min y:", mutableStateOf(property.data.yMin)).state.onChange(stateScope) { property.update(property.copy(data = property.data.copy(yMin = it))) }
        labeledNullableFloatInputRow("Max y:", mutableStateOf(property.data.yMax)).state.onChange(stateScope) { property.update(property.copy(data = property.data.copy(yMax = it))) }
        labeledNullableFloatInputRow("Min z:", mutableStateOf(property.data.zMin)).state.onChange(stateScope) { property.update(property.copy(data = property.data.copy(zMin = it))) }
        labeledNullableFloatInputRow("Max z:", mutableStateOf(property.data.zMax)).state.onChange(stateScope) { property.update(property.copy(data = property.data.copy(zMax = it))) }
    }
}

// Adapted from essentialFloatInput
private fun LayoutScope.labeledNullableFloatInputRow(label: String, state: MutableState<Float?>) = labeledInputRow(label) {
    essentialStateTextInput(
        state,
        { it?.toString() ?: "" },
        {
            return@essentialStateTextInput try {
                if (it.isEmpty()) null
                else it.toFloat()
            } catch (e: NumberFormatException) {
                throw StateTextInput.ParseException()
            }
        },
        Modifier.width(40f),
    )
}
