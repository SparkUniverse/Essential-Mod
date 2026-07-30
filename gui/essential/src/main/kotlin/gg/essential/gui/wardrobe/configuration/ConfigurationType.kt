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

import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.input.UITextInput
import gg.essential.gui.common.input.essentialInput
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.model.util.Instant
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import java.time.ZoneId

class ConfigurationType<I, T> private constructor(
    val displayPlural: String,
    val displaySingular: String = displayPlural.dropLast(1),
    val stateSupplier: (WardrobeState) -> Triple<MutableState<I?>, State<T?>, ListState<T>>,
    val groupingSupplier: (List<T>) -> List<ConfigurationMenu.Grouping<T>> = { list -> list.map { ConfigurationMenu.Single(it) } },
    val idAndNameMapper: (T) -> Pair<I, String>,
    val comparator: Comparator<T> = Comparator.comparing { idAndNameMapper(it).second },
    val updateHandler: ((CosmeticsDataWithChanges, I, T?) -> Unit)? = null,
    val resetHandler: ((CosmeticsDataWithChanges, I) -> Unit)? = null,
    val createHandler: ((CosmeticsDataWithChanges) -> Unit)? = null,
) {

    val canReset = resetHandler != null
    val canUpdate = updateHandler != null

    init {
        VALUES.add(this)
    }

    companion object {
        private val VALUES = mutableListOf<ConfigurationType<*, *>>()

        fun values(): List<ConfigurationType<*, *>> = VALUES

        val CATEGORIES = ConfigurationType(
            displayPlural = "Categories",
            displaySingular = "Category",
            stateSupplier = { Triple(it.currentlyEditingCosmeticCategoryId, it.currentlyEditingCosmeticCategory, it.rawCategories) },
            idAndNameMapper = { it.id to (it.displayNames["en_us"] ?: it.id) },
            updateHandler = { data, id, new -> data.updateCategory(id, new) },
            resetHandler = { data, id -> data.resetCategory(id) },
            createHandler = { cosmeticsDataWithChanges ->
                launchModalFlow(platform.createModalManager()) {
                    val id = createWithIDModal("Category", cosmeticsDataWithChanges.categories.letState { it.map { it.id } }.toListState())
                    cosmeticsDataWithChanges.registerCategory(id)
                }
            }
        )

        val COSMETICS = ConfigurationType(
            displayPlural = "Cosmetics",
            stateSupplier = { Triple(it.currentlyEditingCosmeticId, it.currentlyEditingCosmetic, it.rawCosmetics) },
            idAndNameMapper = { it.id to (it.displayNames["en_us"] ?: it.id) },
            updateHandler = { data, id, new -> data.updateCosmetic(id, new) },
            resetHandler = { data, id -> data.resetCosmetic(id) }
        )

        val BUNDLES = ConfigurationType(
            displayPlural = "Bundles",
            stateSupplier = { Triple(it.currentlyEditingCosmeticBundleId, it.currentlyEditingCosmeticBundle, it.rawBundles) },
            idAndNameMapper = { it.id to it.name },
            updateHandler = { data, id, new -> data.updateBundle(id, new) },
            resetHandler = { data, id -> data.resetBundle(id) },
            createHandler = { cosmeticsDataWithChanges ->
                launchModalFlow(platform.createModalManager()) {
                    val id = createWithIDModal("Bundle", cosmeticsDataWithChanges.bundles.letState { it.map { it.id } }.toListState())
                    cosmeticsDataWithChanges.registerBundle(id)
                }
            }
        )

        val FEATURED_PAGE_LAYOUT_COLLECTIONS = ConfigurationType(
            displayPlural = "Featured page collections",
            stateSupplier = { Triple(it.currentlyEditingFeaturedPageCollectionId, it.currentlyEditingFeaturedPageCollection, it.rawFeaturedPageCollections) },
            groupingSupplier = { list ->
                // Sorts collections as:
                // - DEFAULT (exact id match) is separate and at the top as standalone thing (no group)
                // - Templates (group, anything with "template" in name)
                // - Other (group, everything without availability, and not a template or 'default')
                // - everything else grouped by month+year
                val defaultCollection = "default"
                val otherGroupName = "Other/Testing"
                val templateGroupName = "Templates"
                list.groupBy { collection ->
                    when {
                        collection.id.equals(defaultCollection, ignoreCase = true) -> defaultCollection
                        collection.id.contains("template", ignoreCase = true) -> templateGroupName
                        else -> collection.availability?.after?.atZone(ZoneId.of("UTC"))?.let { "${it.month.name} ${it.year}" } ?: otherGroupName
                    }
                }.mapNotNull { (name, list) ->
                    when (name) {
                        defaultCollection -> list.firstOrNull()?.let { ConfigurationMenu.Single(it) }
                        else -> ConfigurationMenu.Multi(name, null, list)
                    }
                }.sortedBy {
                    when {
                        it is ConfigurationMenu.Single -> 0
                        it is ConfigurationMenu.Multi && it.name == templateGroupName -> 1
                        it is ConfigurationMenu.Multi && it.name == otherGroupName -> 2
                        else -> 3
                    }
                }
            },
            idAndNameMapper = { it.id to it.id },
            comparator = compareByDescending { it.availability?.after ?: Instant.MAX },
            updateHandler = { data, id, new -> data.updateFeaturedPageCollection(id, new) },
            resetHandler = { data, id -> data.resetFeaturedPageCollection(id) },
            createHandler = { cosmeticsDataWithChanges ->
                launchModalFlow(platform.createModalManager()) {
                    val id = createWithIDModal("Featured page collection", cosmeticsDataWithChanges.featuredPageCollections.letState { it.map { it.id } }.toListState())
                    cosmeticsDataWithChanges.registerFeaturedPageCollection(id)
                }
            }
        )

        val IMPLICIT_OWNERSHIPS = ConfigurationType(
            displayPlural = "Implicit ownerships",
            stateSupplier = { Triple(it.currentlyEditingImplicitOwnershipId, it.currentlyEditingImplicitOwnership, it.rawImplicitOwnerships) },
            idAndNameMapper = { it.id to it.id },
            updateHandler = { data, id, new -> data.updateImplicitOwnership(id, new) },
            resetHandler = { data, id -> data.resetImplicitOwnership(id) },
            createHandler = { cosmeticsDataWithChanges ->
                launchModalFlow(platform.createModalManager()) {
                    val id = createWithIDModal("Implicit ownership", cosmeticsDataWithChanges.implicitOwnerships.letState { it.map { it.id } }.toListState())
                    cosmeticsDataWithChanges.registerNewImplicitOwnership(id)
                }
            }
        )

        // Allows a much easier way of editing the sorting values for items
        val SORT_WEIGHT = ConfigurationType(
            displayPlural = "Sort Weight",
            stateSupplier = { Triple(it.currentlyEditingSortWeightCategoryId, it.currentlyEditingSortWeightCategory, it.rawCategories) },
            idAndNameMapper = { it.id to it.id },
        )

        suspend fun ModalFlow.createWithIDModal(name: String, idCollection: ListState<String>) = awaitModal { continuation ->
            object : EssentialModal2(modalManager, false) {
                private val input = UITextInput("Enter New ID", shadowColor = EssentialPalette.BLACK)

                private val errorMessageState = memo {
                    if (idCollection().contains(input.textState())) "That ID already exists!" else null
                }

                override fun LayoutScope.layoutTitle() {
                    title("Create a new $name")
                }

                override fun LayoutScope.layoutBody() {
                    box(Modifier.width(106f).height(17f)) {
                        essentialInput(input, errorMessageState = errorMessageState, inputModifier = Modifier.height(10f).color(EssentialPalette.TEXT))
                    }
                }

                override fun LayoutScope.layoutButtons() {
                    cancelButton("Cancel")
                    primaryButton("Create", disabled = errorMessageState.letState { it != null }) {
                        val id = input.textState.getUntracked()
                        if (!idCollection.getUntracked().contains(id)) {
                            replaceWith(continuation.resume(id))
                        }
                    }
                }

            }
        }
    }
}
