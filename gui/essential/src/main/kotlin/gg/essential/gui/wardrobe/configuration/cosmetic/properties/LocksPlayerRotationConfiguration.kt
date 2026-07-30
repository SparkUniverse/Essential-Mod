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

import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

class LocksPlayerRotationConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.LocksPlayerRotation>(
    CosmeticPropertyType.LOCKS_PLAYER_ROTATION,
    CosmeticProperty.LocksPlayerRotation::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.LocksPlayerRotation) {
        labeledRow("Player Rotation Lock") {
            checkbox(property.data.rotationLock) { property.update(property.copy(data = property.data.copy(rotationLock = it))) }
        }
    }
}
