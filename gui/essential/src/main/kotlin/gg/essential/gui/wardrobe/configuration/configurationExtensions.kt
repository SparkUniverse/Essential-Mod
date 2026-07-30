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
import gg.essential.cosmetics.CosmeticCategoryId
import gg.essential.cosmetics.CosmeticId
import gg.essential.cosmetics.FeaturedPageCollectionId
import gg.essential.cosmetics.FeaturedPageWidth
import gg.essential.cosmetics.ImplicitOwnership
import gg.essential.cosmetics.ImplicitOwnershipId
import gg.essential.mod.Model
import gg.essential.mod.cosmetics.CosmeticBundle
import gg.essential.mod.cosmetics.CosmeticCategory
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.CosmeticTier
import gg.essential.mod.cosmetics.database.GitRepoCosmeticsDatabase
import gg.essential.mod.cosmetics.featured.FeaturedPage
import gg.essential.mod.cosmetics.featured.FeaturedPageCollection
import gg.essential.mod.cosmetics.settings.CosmeticSetting
import gg.essential.network.connectionmanager.cosmetics.CosmeticsDataWithChanges

// Cosmetics
fun CosmeticsDataWithChanges.registerCategory(id: String) {
    if (getCategory(id) != null) {
        throw IllegalArgumentException("A category with the ID $id already exists")
    }
    updateCategory(
        id,
        CosmeticCategory(
            id,
            mapOf("en_us" to id),
            mapOf("en_us" to "Category Description"),
            mapOf("en_us" to id),
            setOf(),
            emptySet(),
            0,
            null,
            null,
        )
    )
}

fun CosmeticsDataWithChanges.unregisterCategory(categoryId: CosmeticCategoryId) {
    // Remove the category from all cosmetics that are in it
    if (cosmetics.getUntracked().any { cosmetic -> categoryId in cosmetic.categories.keys }) {
        throw IllegalArgumentException("Cannot unregister a category that is in use by a cosmetic")
    }

    updateCategory(categoryId, null)
}

fun CosmeticsDataWithChanges.resetCategory(categoryId: CosmeticCategoryId) {
    updateCategory(categoryId, inner.getCategory(categoryId))
}

// Bundles
fun CosmeticsDataWithChanges.registerBundle(
    id: CosmeticBundleId,
    name: String = id,
    tier: CosmeticTier = CosmeticTier.COMMON,
    discount: Float = 0f,
    rotateOnPreview: Boolean = false,
    // A default skin I use for my alt, just so it's not empty :,
    skin: CosmeticBundle.Skin? = CosmeticBundle.Skin("bff1570fdf623153e6b4a4d2ca97559b471f1ec776584ceec2ebb8bf0b7ba504", Model.ALEX),
    cosmetics: Map<CosmeticSlot, CosmeticId> = mapOf(),
    settings: Map<CosmeticId, List<CosmeticSetting>> = mapOf(),
) {
    if (getCosmeticBundle(id) != null) {
        throw IllegalArgumentException("A bundle with the ID $id already exists")
    }
    updateBundle(
        id,
        CosmeticBundle(
            id,
            name,
            tier,
            discount,
            rotateOnPreview,
            skin,
            cosmetics,
            settings
        )
    )
}

fun CosmeticsDataWithChanges.resetBundle(bundleId: CosmeticBundleId) {
    updateBundle(bundleId, inner.getCosmeticBundle(bundleId))
}

// Featured Page Collections
fun CosmeticsDataWithChanges.registerFeaturedPageCollection(
    id: FeaturedPageCollectionId,
    availability: FeaturedPageCollection.Availability? = null,
    pages: Map<FeaturedPageWidth, FeaturedPage> = mapOf()
) {
    if (getFeaturedPageCollection(id) != null) {
        throw IllegalArgumentException("A featured page collection with the ID $id already exists")
    }
    updateFeaturedPageCollection(
        id,
        FeaturedPageCollection(
            id,
            availability,
            pages
        )
    )
}

fun CosmeticsDataWithChanges.resetFeaturedPageCollection(id: FeaturedPageCollectionId) {
    updateFeaturedPageCollection(id, inner.getFeaturedPageCollection(id))
}

// Implicit ownerships
fun CosmeticsDataWithChanges.registerNewImplicitOwnership(id: ImplicitOwnershipId) {
    if (getImplicitOwnership(id) != null) {
        throw IllegalArgumentException("A implicit ownership with the ID $id already exists")
    }
    updateImplicitOwnership(
        id,
        ImplicitOwnership(
            id,
            listOf(),
            GitRepoCosmeticsDatabase.ImplicitOwnershipCriterion.Everyone
        )
    )
}

fun CosmeticsDataWithChanges.resetImplicitOwnership(id: ImplicitOwnershipId) {
    updateImplicitOwnership(id, inner.getImplicitOwnership(id))
}
