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
package gg.essential.gui.wardrobe.categories

import gg.essential.config.EssentialConfig
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.animation.AnimatingConstraints
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.state.BasicState
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.combinators.zip
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.filterIsInstance
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.WardrobeCategory
import gg.essential.gui.wardrobe.WardrobeContainer
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.components.banner
import gg.essential.gui.wardrobe.components.noItemsFound
import gg.essential.gui.wardrobe.something.CosmeticGroup
import gg.essential.network.connectionmanager.notices.WardrobeBannerColor
import gg.essential.universal.ChatColor
import gg.essential.universal.USound
import gg.essential.gui.util.isInComponentTree
import gg.essential.gui.util.pollingStateV2
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.scrollToTopOf

class CategoryComponent(
    val category: WardrobeCategory.ParentCategory,
    val wardrobeState: WardrobeState,
    private val scroller: ScrollComponent,
) : UIContainer() {

    private val scrollerPercentState = BasicState(0f)

    init {
        constrain {
            width = 100.percent
            height = ChildBasedSizeConstraint()
        }

        scroller.addScrollAdjustEvent(false) { percent, _ -> scrollerPercentState.set(percent) }
    }

    private val subCategories = wardrobeState.allCategories.filterIsInstance<WardrobeCategory.SubCategory>()
        .filter { it.parent == category }

    private val groupsContainer: UIComponent
    private val groups: List<CosmeticGroup>
        get() = groupsContainer.childrenOfType()

    private var updatingCategoryBasedOnScroll = false
    private var updatingScrollBasedOnCategory = false
    private var updatingScrollBasedOnCategoryPending = false

    init {
        val isEmpty = subCategories.zip(wardrobeState.visibleCosmetics) { subCategories, cosmetics ->
            subCategories.all { subCategory -> cosmetics.none { it in subCategory } }
        }

        val dismissibleBannerTextState = if (category == WardrobeCategory.Emotes) {
            pollingStateV2 {
                if (EssentialConfig.showEmotePageKeybindBanner.get()) {
                    val openEmoteWheelKeybinding = platform.openEmoteWheelKeybind
                    if (openEmoteWheelKeybinding.isBound) {
                        val keyName = openEmoteWheelKeybinding.boundKeyName
                        "${ChatColor.GRAY}Open the emote wheel in-game by pressing [${ChatColor.WHITE}**${keyName}**${ChatColor.GRAY}]" to WardrobeBannerColor.GRAY
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } else {
            stateOf<Pair<String, WardrobeBannerColor>?>(null)
        }

        val nonDismissibleBannerTextState = if (category == WardrobeCategory.Emotes) {
            pollingStateV2 {
                val openEmoteWheelKeybinding = platform.openEmoteWheelKeybind
                when {
                    openEmoteWheelKeybinding.isConflicting ->
                        "${ChatColor.GRAY}Emote wheel keybind is conflicting. [View Keybinds](essential://minecraft/settings/keybinds)" to WardrobeBannerColor.YELLOW

                    !openEmoteWheelKeybinding.isBound ->
                        "${ChatColor.GRAY}Emote wheel keybind is not assigned. [View Keybinds](essential://minecraft/settings/keybinds)" to WardrobeBannerColor.YELLOW

                    else -> null
                }
            }
        } else {
            stateOf<Pair<String, WardrobeBannerColor>?>(null)
        }

        layout {
            groupsContainer = column(Modifier.fillWidth(), horizontalAlignment = Alignment.Start) {
                bind(dismissibleBannerTextState.zip(nonDismissibleBannerTextState)) { (dismissible, nonDismissible) ->
                    if (dismissible != null || nonDismissible != null) {
                        spacer(height = 10f)
                    }

                    if (nonDismissible != null) {
                        banner(nonDismissible.first, bannerColor = nonDismissible.second)
                    } else if (dismissible != null) {
                        banner(dismissible.first, bannerColor = dismissible.second) {
                            USound.playButtonPress()
                            EssentialConfig.showEmotePageKeybindBanner.set(false)
                        }
                    }
                }
                if_(!isEmpty) {
                    forEach(subCategories, cache = true) { subCategory ->
                        val cosmetics = wardrobeState.visibleCosmeticItems.filter { it.cosmetic in subCategory }
                        if_(cosmetics.map { it.isNotEmpty() }) {
                            val group = CosmeticGroup(subCategory, subCategory.fullName, cosmetics, wardrobeState, scroller, scrollerPercentState)
                            group(Modifier.fillWidth())
                        }
                    }
                    spacer(height = 10f)
                } `else` {
                    noItemsFound()
                }
            }
        }

        var previousCategory: WardrobeCategory? = null
        wardrobeState.currentCategory.zip(
            wardrobeState.highlightItem
        ).onSetValueAndNow(this) { (category, highlightedItem) ->
            if (category.superCategory != this.category) return@onSetValueAndNow
            if (updatingCategoryBasedOnScroll) return@onSetValueAndNow
            if (previousCategory == category && highlightedItem == null) return@onSetValueAndNow

            previousCategory = category

            updatingScrollBasedOnCategoryPending = true
            fun doScroll() {
                updatingScrollBasedOnCategoryPending = false
                updatingScrollBasedOnCategory = true

                scroller.animationFrame() // Force recalculate of position to avoid scrolling an incorrect amount

                if (category == this.category) return scroller.scrollToTop()

                val group = groups.find { it.category == category } ?: return scroller.scrollToTopOf(this)
                val cosmetics = group.sortedCosmetics.get()

                val equipped = wardrobeState.equippedCosmeticsState.get().values.toSet() + wardrobeState.emoteWheelManager.selectedEmoteWheelSlots.getUntracked()

                val target = group.cosmeticsContainer.children.getOrNull(cosmetics.indexOfFirst { cosmetic ->
                    if (highlightedItem != null) {
                        cosmetic.itemId == highlightedItem
                    } else {
                        cosmetic.cosmetic.id in equipped
                    }
                }) ?: return scroller.scrollToTopOf(group)

                scroller.scrollToTopOf(target, offset = -CosmeticGroup.headerHeight)
            }

            // Delay is needed because the effect which swaps out the current category component has undefined ordering
            // compared to this effect, so we'll wait until before the next frame, by which point it will definitely
            // have ran.
            Window.enqueueRenderOperation(::doScroll)
        }

        scroller.addScrollAdjustEvent(false) { _, _ ->
            if (!isInComponentTree()) return@addScrollAdjustEvent
            if (WardrobeContainer.scrollingToTop) return@addScrollAdjustEvent
            if (updatingScrollBasedOnCategory || updatingScrollBasedOnCategoryPending) return@addScrollAdjustEvent

            updatedCurrentCategoryBasedOnScrollPosition()
        }
    }

    private fun checkScrolling() {
        // Check if the scroller is still smooth-scrolling, needs to be in `draw` because that's where ScrollComponent
        // initiates the scroll animation (if we checked in animationFrame, the animation might not yet have started).
        if (scroller.children.first().constraints !is AnimatingConstraints) {
            // Done scrolling, end of update. Any further scrolling is due to the user and should update the category
            // again.
            updatingScrollBasedOnCategory = false
        }
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        checkScrolling()
    }

    private fun updatedCurrentCategoryBasedOnScrollPosition() {
        val top = scroller.getTop()
        val bottom = scroller.getBottom()
        val targetGroup = if (groups.none { it.getBottom() > bottom }) { // At the bottom, last group
                groups.lastOrNull()
            } else { // Currently visible group
                groups.find { it.getTop() <= top && it.getBottom() > top }
            }
        val targetCategory = targetGroup?.category ?: category

        updatingCategoryBasedOnScroll = true
        wardrobeState.currentCategory.set(targetCategory)
        updatingCategoryBasedOnScroll = false
    }
}
