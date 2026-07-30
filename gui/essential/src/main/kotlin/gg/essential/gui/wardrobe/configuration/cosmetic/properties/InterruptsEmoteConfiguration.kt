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

import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledFloatInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

class InterruptsEmoteConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.InterruptsEmote>(
    CosmeticPropertyType.INTERRUPTS_EMOTE,
    CosmeticProperty.InterruptsEmote::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.InterruptsEmote) {
        labeledRow("Movement") {
            checkbox(property.data.movement) { property.update(property.copy(data = property.data.copy(movement = it))) }
        }
        labeledFloatInputRow("Movement grace period", mutableStateOf(property.data.movementGraceTime.toFloat())).state.onChange(stateScope) {
            property.update(property.copy(data = property.data.copy(movementGraceTime = it.toDouble())))
        }
        labeledRow("Attack") {
            checkbox(property.data.attack) { property.update(property.copy(data = property.data.copy(attack = it))) }
        }
        labeledRow("Damaged") {
            checkbox(property.data.damaged) { property.update(property.copy(data = property.data.copy(damaged = it))) }
        }
        labeledRow("Arm Swing") {
            checkbox(property.data.armSwing) { property.update(property.copy(data = property.data.copy(armSwing = it))) }
        }
    }
}
