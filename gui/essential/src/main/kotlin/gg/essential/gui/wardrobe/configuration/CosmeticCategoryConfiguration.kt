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
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.compactFullEssentialToggle
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.configuratorDangerModal
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledIntInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledManagedNullableISODateInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledStringInputRow
import gg.essential.mod.cosmetics.CosmeticCategory
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick

class CosmeticCategoryConfiguration(
    state: WardrobeState,
) : AbstractConfiguration<CosmeticCategoryId, CosmeticCategory>(
    ConfigurationType.CATEGORIES,
    state
) {

    override fun LayoutScope.columnLayout(category: CosmeticCategory) {
        labeledStringInputRow("ID:", mutableStateOf(category.id)).state.onChange(stateScope) { newID ->
            // Create the new category
            cosmeticsDataWithChanges.updateCategory(newID, category.copy(id = newID))

            // Update cosmetics to use the new category
            cosmeticsDataWithChanges.cosmetics.getUntracked().forEach { cosmetic ->
                val order = cosmetic.categories[category.id] ?: return@forEach
                cosmeticsDataWithChanges.updateCosmetic(cosmetic.id, cosmetic.copy(storeInfo = cosmetic.storeInfo.copy(categories = cosmetic.categories - category.id + (newID to order))))
            }

            // Delete the old category
            cosmeticsDataWithChanges.updateCategory(category.id, null)

            state.currentlyEditingCosmeticCategoryId.set(newID)
        }
        labeledStringInputRow(
            "Full name:",
            mutableStateOf(category.displayNames["en_us"] ?: "")
        ).state.onChange(stateScope) { category.update(category.copy(displayNames = category.displayNames + ("en_us" to it))) }
        labeledStringInputRow(
            "Compact Name:",
            mutableStateOf(category.compactNames["en_us"] ?: "")
        ).state.onChange(stateScope) { category.update(category.copy(compactNames = category.compactNames + ("en_us" to it))) }
        labeledStringInputRow(
            "Description:",
            mutableStateOf(category.descriptions["en_us"] ?: ""),
            Modifier.fillRemainingWidth(),
            Arrangement.spacedBy(10f)
        ).state.onChange(stateScope) { category.update(category.copy(descriptions = category.descriptions + ("en_us" to it))) }
        labeledIntInputRow("Order:", mutableStateOf(category.order)).state.onChange(stateScope) { category.update(category.copy(order = it)) }
        labeledManagedNullableISODateInputRow("Available After:", mutableStateOf(category.availableAfter)).state.onChange(stateScope) { category.update(category.copy(availableAfter = it)) }
        labeledManagedNullableISODateInputRow("Available Until:", mutableStateOf(category.availableUntil)).state.onChange(stateScope) { category.update(category.copy(availableUntil = it)) }

        val isEmoteCategoryState = mutableStateOf(category.isEmoteCategory())
        isEmoteCategoryState.onChange(stateScope) { category.update(category.copy(tags = if (it) category.tags + CosmeticCategory.EMOTE_CATEGORY_TAG else category.tags - CosmeticCategory.EMOTE_CATEGORY_TAG)) }
        labeledRow("Is Emote Category: ") {
            box(Modifier.childBasedWidth(3f).childBasedHeight(3f).hoverScope()) {
                compactFullEssentialToggle(isEmoteCategoryState)
                spacer(1f, 1f)
            }
        }

        val isHiddenState = mutableStateOf(category.isHidden())
        isHiddenState.onChange(stateScope) { category.update(category.copy(tags = if (it) category.tags + CosmeticCategory.HIDDEN_CATEGORY_TAG else category.tags - CosmeticCategory.HIDDEN_CATEGORY_TAG)) }
        labeledRow("Is Hidden (sidebar): ") {
            box(Modifier.childBasedWidth(3f).childBasedHeight(3f).hoverScope()) {
                compactFullEssentialToggle(isHiddenState)
                spacer(1f, 1f)
            }
        }

        text("Tags:", Modifier.alignHorizontal(Alignment.Start))
        if (category.tags.isEmpty()) {
            text("No tags...", Modifier.alignHorizontal(Alignment.Start))
        } else {
            for (tag in category.tags) {
                labeledRow("- $tag") {
                    box(Modifier.width(10f).height(10f)) {
                        icon(EssentialPalette.CANCEL_5X)
                    }.onLeftClick {
                        category.update(category.copy(tags = category.tags - tag))
                    }
                }
            }
        }
        labeledStringInputRow("Add tag:", mutableStateOf("")).state.onChange(stateScope) { category.update(category.copy(tags = category.tags + it)) }
    }

    override fun attemptDelete(categoryId: CosmeticCategoryId) {
        val cosmeticNames = cosmeticsDataWithChanges.cosmetics.getUntracked().filter { categoryId in it.categories }.joinToString { it.displayName }
        launchModalFlow(platform.createModalManager()) {
            configuratorDangerModal(
                "Delete",
                "Are you sure you want to delete ${categoryId}?",
                "This will remove the category from all cosmetics that use it. The following cosmetics will be affected: $cosmeticNames",
            )
            cosmeticsDataWithChanges.cosmetics.getUntracked().forEach { cosmetic ->
                if (!cosmetic.categories.containsKey(categoryId)) return@forEach
                cosmeticsDataWithChanges.updateCosmetic(cosmetic.id, cosmetic.copy(storeInfo = cosmetic.storeInfo.copy(categories = cosmetic.categories - categoryId)))
            }
            cosmeticsDataWithChanges.unregisterCategory(categoryId)
            state.currentlyEditingCosmeticCategoryId.set(null)
        }
    }

}
