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
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

class HidesAllOtherCosmeticsOrItemsConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.HidesAllOtherCosmeticsOrItems>(
    CosmeticPropertyType.ALL_OTHER_COSMETIC_OR_ITEM_HIDING,
    CosmeticProperty.HidesAllOtherCosmeticsOrItems::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.HidesAllOtherCosmeticsOrItems) {
        divider()
        text("If enabled,")
        text("this Cosmetic / Emote will be unaffected by these settings,")
        text("from this or any other Cosmetic / Emote.")
        divider()
        labeledRow("Hide Other Cosmetics: All") {
            checkbox(property.data.hideAllCosmetics) { property.update(property.copy(data = property.data.copy(hideAllCosmetics = it))) }
        }

        labeledRow("Hide Other Cosmetics: Parts") {
            column(Arrangement.spacedBy(5f)) {
                text("Head")
                checkbox(property.data.hideHeadCosmetics) { property.update(property.copy(data = property.data.copy(hideHeadCosmetics = it))) }
            }
            column(Arrangement.spacedBy(5f)) {
                text("Body")
                checkbox(property.data.hideBodyCosmetics) { property.update(property.copy(data = property.data.copy(hideBodyCosmetics = it))) }
            }
            column(Arrangement.spacedBy(5f)) {
                text("Arms")
                checkbox(property.data.hideArmCosmetics) { property.update(property.copy(data = property.data.copy(hideArmCosmetics = it))) }
            }
            column(Arrangement.spacedBy(5f)) {
                text("Legs")
                checkbox(property.data.hideLegCosmetics) { property.update(property.copy(data = property.data.copy(hideLegCosmetics = it))) }
            }
        }

        labeledRow("Hide Held Items") {
            checkbox(property.data.hideItems) { property.update(property.copy(data = property.data.copy(hideItems = it))) }
        }
    }
}
