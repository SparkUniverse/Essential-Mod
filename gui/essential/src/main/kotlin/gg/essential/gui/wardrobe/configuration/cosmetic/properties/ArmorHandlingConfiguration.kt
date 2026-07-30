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

class ArmorHandlingConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.ArmorHandling>(
    CosmeticPropertyType.ARMOR_HANDLING,
    CosmeticProperty.ArmorHandling::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.ArmorHandling) {
        labeledRow("Head") {
            checkbox(property.data.head) { newState -> property.update(property.copy(data = property.data.copy(head = newState))) }
        }
        labeledRow("Arms") {
            checkbox(property.data.arms) { newState -> property.update(property.copy(data = property.data.copy(arms = newState))) }
        }
        labeledRow("Body") {
            checkbox(property.data.body) { newState -> property.update(property.copy(data = property.data.copy(body = newState))) }
        }
        labeledRow("Legs") {
            checkbox(property.data.legs) { newState -> property.update(property.copy(data = property.data.copy(legs = newState))) }
        }
    }

}
