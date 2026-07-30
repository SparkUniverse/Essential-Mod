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
package gg.essential.network.connectionmanager.cosmetics

import gg.essential.cosmetics.CosmeticCategoryId
import gg.essential.cosmetics.CosmeticId
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.CosmeticTier
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.model.Side
import gg.essential.model.util.Instant
import gg.essential.network.cosmetics.Cosmetic

/**
 * Utility function to easily update a cosmetic
 */
private fun CosmeticsDataWithChanges.updateCosmetic(cosmeticId: CosmeticId, func: Cosmetic.() -> Cosmetic) {
    getCosmetic(cosmeticId)!!.let { oldCosmetic ->
        val newCosmetic = oldCosmetic.func()
        updateCosmetic(cosmeticId, newCosmetic)
    }
}

/**
 * Removes a [CosmeticProperty] from the cosmetic with id [cosmeticId]
 */
fun CosmeticsDataWithChanges.removeCosmeticProperty(cosmeticId: CosmeticId, property: CosmeticProperty) {
    updateCosmetic(cosmeticId) {
        copy(base = base.copy(allProperties = allProperties - property))
    }
}

/**
 * Adds a [CosmeticProperty] to the cosmetic with id [cosmeticId]. If the property is a singleton
 * and already exists, it will be replaced.
 */
fun CosmeticsDataWithChanges.addCosmeticProperty(cosmeticId: CosmeticId, property: CosmeticProperty) {
    updateCosmetic(cosmeticId) {
        copy(base = base.copy(allProperties = allProperties.removeSingletonPropertyType(property.type) + property))
    }
}

/**
 * Updates [cosmeticId] by removing [oldProperty] and adding [newProperty]
 */
fun CosmeticsDataWithChanges.updateCosmeticProperty(cosmeticId: CosmeticId, oldProperty: CosmeticProperty, newProperty: CosmeticProperty) {
    updateCosmetic(cosmeticId) {
        copy(base = base.copy(allProperties = allProperties.removeSingletonPropertyType(oldProperty.type) - oldProperty + newProperty))
    }
}

/**
 * Enables or disables the [CosmeticProperty] of type [type] for the cosmetic with id [cosmeticId].
 * If the property does not exist, it will be added with default/placeholder values.
 * Throws [IllegalArgumentException] if [type] is not singleton.
 */
fun CosmeticsDataWithChanges.setCosmeticSingletonPropertyEnabled(
    cosmeticId: CosmeticId,
    type: CosmeticPropertyType,
    enabled: Boolean,
) {
    updateCosmetic(cosmeticId) {
        val existingProperty = allProperties.find { it.type == type }
        if (existingProperty != null) {
            val updatedProperty = when (existingProperty) {
                is CosmeticProperty.ArmorHandling -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.ArmorHandlingV2 -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.PositionRange -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.InterruptsEmote -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.Localization -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.PreviewResetTime -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.RequiresUnlockAction -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.TransitionDelay -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.Variants -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.DefaultSide -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.MutuallyExclusive -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.HidesAllOtherCosmeticsOrItems -> existingProperty.copy(enabled = enabled)
                is CosmeticProperty.LocksPlayerRotation -> existingProperty.copy(enabled = enabled)

                is CosmeticProperty.CosmeticBoneHiding,
                is CosmeticProperty.ExternalHiddenBone,
                is CosmeticProperty.Unknown -> throw IllegalArgumentException("$type is not a singleton property")
            }
            copy(base = base.copy(allProperties = allProperties - existingProperty + updatedProperty))
        } else {
            val newProperty = when (type) {
                CosmeticPropertyType.ARMOR_HANDLING -> CosmeticProperty.ArmorHandling(
                    cosmeticId,
                    enabled,
                    CosmeticProperty.ArmorHandling.Data()
                )

                CosmeticPropertyType.ARMOR_HANDLING_V2 -> CosmeticProperty.ArmorHandlingV2(
                    cosmeticId,
                    enabled,
                    CosmeticProperty.ArmorHandlingV2.Data()
                )

                CosmeticPropertyType.POSITION_RANGE -> CosmeticProperty.PositionRange(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.PositionRange.Data()
                )

                CosmeticPropertyType.INTERRUPTS_EMOTE -> CosmeticProperty.InterruptsEmote(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.InterruptsEmote.Data()
                )
                CosmeticPropertyType.ALL_OTHER_COSMETIC_OR_ITEM_HIDING -> CosmeticProperty.HidesAllOtherCosmeticsOrItems(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.HidesAllOtherCosmeticsOrItems.Data()
                )

                CosmeticPropertyType.LOCKS_PLAYER_ROTATION -> CosmeticProperty.LocksPlayerRotation(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.LocksPlayerRotation.Data()
                )

                CosmeticPropertyType.LOCALIZATION -> CosmeticProperty.Localization(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.Localization.Data("Partner Name")
                )

                CosmeticPropertyType.PREVIEW_RESET_TIME -> CosmeticProperty.PreviewResetTime(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.PreviewResetTime.Data(3.0)
                )

                CosmeticPropertyType.REQUIRES_UNLOCK_ACTION -> CosmeticProperty.RequiresUnlockAction(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.RequiresUnlockAction.Data.OpenLink(
                        "Descriptive Action",
                        "https://example.com",
                        "example.com"
                    )
                )

                CosmeticPropertyType.TRANSITION_DELAY -> CosmeticProperty.TransitionDelay(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.TransitionDelay.Data(0)
                )

                CosmeticPropertyType.VARIANTS -> CosmeticProperty.Variants(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.Variants.Data(listOf())
                )

                CosmeticPropertyType.DEFAULT_SIDE -> CosmeticProperty.DefaultSide(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.DefaultSide.Data(Side.getDefaultSideOrNull(Side.values().toSet()) ?: Side.LEFT)
                )

                CosmeticPropertyType.MUTUALLY_EXCLUSIVE -> CosmeticProperty.MutuallyExclusive(
                    "UNUSED",
                    enabled,
                    CosmeticProperty.MutuallyExclusive.Data(emptySet())
                )

                CosmeticPropertyType.COSMETIC_BONE_HIDING,
                CosmeticPropertyType.EXTERNAL_HIDDEN_BONE -> throw IllegalArgumentException("$type is not a singleton property")

            }
            copy(base = base.copy(allProperties = allProperties + newProperty))
        }
    }
}

fun CosmeticsDataWithChanges.setCosmeticPriceCoins(cosmeticId: CosmeticId, price: Int?) {
    updateCosmetic(cosmeticId) {
        copy(storeInfo = storeInfo.copy(price = price))
    }
}

fun CosmeticsDataWithChanges.setCosmeticTier(cosmeticId: CosmeticId, tier: CosmeticTier) {
    updateCosmetic(cosmeticId) {
        copy(base = base.copy(tier = tier))
    }
}

/**
 * Sets the slot of the cosmetic with id [cosmeticId] to [slot]
 */
fun CosmeticsDataWithChanges.setCosmeticSlot(cosmeticId: CosmeticId, slot: CosmeticSlot) {
    updateCosmetic(cosmeticId) {
        copy(base = base.copy(slot = slot))
    }
}

/**
 * Sets the English ("en_us") display name of the cosmetic with id [cosmeticId] to [displayName]
 */
fun CosmeticsDataWithChanges.setCosmeticDisplayName(cosmeticId: CosmeticId, displayName: String) {
    updateCosmetic(cosmeticId) {
        copy(base = base.copy(displayNames = displayNames + ("en_us" to displayName)))
    }
}

/**
 * Sets the tags of the cosmetic with id [cosmeticId] to [tags]
 */
fun CosmeticsDataWithChanges.setCosmeticTags(cosmeticId: CosmeticId, tags: Set<String>) {
    updateCosmetic(cosmeticId) {
        copy(storeInfo = storeInfo.copy(tags = tags))
    }
}

/**
 * Sets the availability window of the cosmetic with id [cosmeticId] to [availableAfter] and [availableUntil]
 */
fun CosmeticsDataWithChanges.setCosmeticAvailable(cosmeticId: CosmeticId, availableAfter: Instant?, availableUntil: Instant?, showTimerAfter: Instant?) {
    updateCosmetic(cosmeticId) {
        copy(storeInfo = storeInfo.copy(availableAfter = availableAfter, availableUntil = availableUntil, showTimerAfter = showTimerAfter))
    }
}

/**
 * Sets the default sort weight of the cosmetic with id [cosmeticId] to [defaultSortWeight]
 */
fun CosmeticsDataWithChanges.setCosmeticDefaultSortWeight(cosmeticId: CosmeticId, defaultSortWeight: Int) {
    updateCosmetic(cosmeticId) {
        copy(storeInfo = storeInfo.copy(defaultSortWeight = defaultSortWeight))
    }
}

/**
 * Adds the cosmetic with id [cosmeticId] to the category with id [categoryId] at the specified [sortOrder]
 */
fun CosmeticsDataWithChanges.addToCategory(cosmeticId: CosmeticId, categoryId: CosmeticCategoryId, sortOrder: Int) {
    updateCosmetic(cosmeticId) {
        copy(storeInfo = storeInfo.copy(categories = categories.toMutableMap().apply {
            this[categoryId] = sortOrder
        }))
    }
}

/**
 * Removes the cosmetic with id [cosmeticId] from the category with id [categoryId]
 */
fun CosmeticsDataWithChanges.removeCosmeticFromCategory(cosmeticId: CosmeticId, categoryId: CosmeticCategoryId) {
    updateCosmetic(cosmeticId) {
        copy(storeInfo = storeInfo.copy(categories = categories.toMutableMap().apply {
            remove(categoryId)
        }))
    }
}

/**
 * Resets a cosmetic to its default values
 */
fun CosmeticsDataWithChanges.resetCosmetic(cosmeticId: CosmeticId) {
    updateCosmetic(cosmeticId, inner.getCosmetic(cosmeticId))
}
