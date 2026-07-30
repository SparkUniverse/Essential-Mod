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

import gg.essential.cosmetics.CosmeticCategoryId
import gg.essential.gui.common.input.essentialIntInput
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.CosmeticCategory
import gg.essential.network.connectionmanager.cosmetics.addToCategory
import gg.essential.network.connectionmanager.cosmetics.setCosmeticDefaultSortWeight

class CosmeticSortWeightConfiguration(
    state: WardrobeState,
) : AbstractConfiguration<CosmeticCategoryId, CosmeticCategory>(
    ConfigurationType.SORT_WEIGHT,
    state
) {

    override fun LayoutScope.columnLayout(category: CosmeticCategory) {
        val text1 = """
            Cosmetics sort weight configuration.
            Cosmetics are first sorted descending by the sort weight for their specific category and if tied then sorted by their default sort weight.
            Below are all the cosmetics in the selected category.
            The first number is the default sort weight.
            The second number is the category-specific sort-weight.
        """.trimIndent()
        wrappedText(text1, Modifier.fillWidth())
        val cosmetics = memo {
            state.cosmetics()
                .filter { category.id in it.categories }
                .map { WardrobeState.CosmeticWithSortInfo(it, false, null, category) }
                .sortedWith(WardrobeState.FilterSort.Default)
        }.toListState()

        if_({ cosmetics().isEmpty() }) {
            text("No cosmetics in category...", Modifier.alignHorizontal(Alignment.Start))
        }
        forEach(cosmetics) { cosm ->
            val cosmetic = cosm.cosmetic
            labeledRow("- ${cosmetic.displayName}:") {
                row {
                    essentialIntInput(mutableStateOf(cosmetic.defaultSortWeight), Modifier.width(30f)).state.onChange(stateScope) { cosmeticsDataWithChanges.setCosmeticDefaultSortWeight(cosmetic.id, it) }
                    essentialIntInput(mutableStateOf(cosmetic.categories[category.id] ?: 0), Modifier.width(30f)).state.onChange(stateScope) { cosmeticsDataWithChanges.addToCategory(cosmetic.id, category.id, it) }
                }
            }
        }
    }

}
