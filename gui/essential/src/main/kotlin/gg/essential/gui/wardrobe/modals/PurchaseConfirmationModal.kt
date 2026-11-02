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
package gg.essential.gui.wardrobe.modals

import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.common.textStyle
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.FloatPosition
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedMaxHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillRemainingWidth
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.image
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.tag
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.wardrobe.Item
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.network.connectionmanager.coins.CoinsManager
import java.awt.Color

// TODO: rewrite usages to use modalflow so passing primary action is no longer required
class PurchaseConfirmationModal(
    modalManager: ModalManager,
    private val items: List<Pair<Item.CosmeticOrEmote, State<Int>>>,
    private val totalAmount: State<Int>,
    private val primaryAction: PurchaseConfirmationModal.() -> Unit,
) : EssentialModal2(modalManager, requiresButtonPress = false) {
    private val discountAmount = memo { items.sumOf { it.second() } - totalAmount() }

    override fun LayoutScope.layoutContent(modifier: Modifier) {
        column(Modifier.width(222f), Arrangement.spacedBy(14f)) {
            box(Modifier.fillWidth(padding = 16f)) {
                layoutTitle()
            }
            box(Modifier.fillWidth(padding = 16f).childBasedMaxHeight()) {
                purchaseSummary()
            }
            box(Modifier.fillWidth(padding = 16f)) {
                spacer(height = 3f)
                layoutButtons()
            }
        }
    }

    override fun LayoutScope.layoutTitle() {
        text("Confirm your purchase!", Modifier.color(EssentialPalette.TEXT))
    }

    private fun LayoutScope.purchaseSummary() {
        box(
            Modifier
                .fillWidth(rightPadding = 1f)
                .childBasedHeight(padding = 15f)
                .color(EssentialPalette.PURCHASE_CONFIRMATION_MODAL_SECONDARY)
                .shadow()
        ) {
            column(Modifier.fillWidth(padding = 16f), Arrangement.spacedBy(7f)) {
                // If there is only one item in the cart, and there is no discount, we don't need to show a total.
                if_({ items.size == 1 && discountAmount() == 0 }) {
                    itemEntry(items[0], Modifier.color(EssentialPalette.TEXT_HIGHLIGHT))
                } `else` {
                    column(Modifier.fillWidth(), Arrangement.spacedBy(4f)) {
                        items.forEach { itemEntry(it) }
                    }

                    box(Modifier.fillWidth().height(1f).color(EssentialPalette.BUTTON))

                    if_({ discountAmount() != 0 }) {
                        listEntry(
                            "Discount",
                            discountAmount,
                            nameModifier = Modifier.color(EssentialPalette.GREEN),
                            costModifier = Modifier.color(EssentialPalette.TEXT_HIGHLIGHT),
                            negative = true
                        )
                        box(Modifier.fillWidth().height(1f).color(EssentialPalette.BUTTON))
                    }

                    listEntry("Total", totalAmount, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT))
                }
            }
        }
    }

    private fun LayoutScope.itemEntry(
        itemAndPrice: Pair<Item.CosmeticOrEmote, State<Int>>,
        nameModifier: Modifier = Modifier,
        costModifier: Modifier = nameModifier,
    ) {
        val (item, price) = itemAndPrice
        listEntry(item.name, price, nameModifier, costModifier)
    }

    private fun LayoutScope.listEntry(
        name: String,
        cost: State<Int>,
        nameModifier: Modifier = Modifier,
        costModifier: Modifier = nameModifier,
        negative: Boolean = false,
    ) {
        val defaultTextModifier = Modifier.color(EssentialPalette.TEXT_MID_GRAY).shadow(Color.BLACK)

        row(Modifier.fillWidth(), Arrangement.spacedBy(spacing = 15f)) {
            row(Modifier.fillRemainingWidth(), Arrangement.spacedBy(float = FloatPosition.START)) {
                text(
                    name,
                    Modifier.then(defaultTextModifier).then(nameModifier),
                    truncateIfTooSmall = true,
                )
            }

            bind(cost) { costAmount ->
                wrappedText(
                    "${if(negative) "-" else ""}${CoinsManager.COIN_FORMAT.format(costAmount)}{coin-icon}",
                    textModifier = defaultTextModifier.then(costModifier),
                ) {
                    "coin-icon" {
                        row {
                            spacer(width = 2f)
                            image(EssentialPalette.COIN_7X, Modifier.shadow(Color.BLACK))
                        }
                    }
                }
            }
        }
    }

    override fun LayoutScope.layoutButtons() {
        row(Arrangement.spacedBy(8f)) {
            cancelButton("Cancel", Modifier)
            outlineButton(
                Modifier.width(91f).shadow().tag(PrimaryAction),
                OutlineButtonStyle.BLUE,
                action = {
                    this@PurchaseConfirmationModal.primaryAction()
                    close()
                },
            ) { currentStyle ->
                row {
                    text("Purchase", Modifier.textStyle(currentStyle))
                }
            }
        }
    }

    companion object {
        fun forEquippedItemsPurchasable(modalManager: ModalManager, state: WardrobeState, primaryAction: () -> Unit): PurchaseConfirmationModal {
            val itemsAndPriceInfo = state.equippedCosmeticsPurchasable.getUntracked().map { it to it.getPricingInfo(state) }
            val totalCost = memo { itemsAndPriceInfo.sumOf { (_, price) -> price()?.realCost ?: 0 } }

            return PurchaseConfirmationModal(
                modalManager,
                itemsAndPriceInfo.map { (item, price) -> item to price.letState { it?.baseCost ?: 0 } },
                totalCost,
            ) { primaryAction() }
        }

        fun forItem(
            modalManager: ModalManager,
            item: Item.CosmeticOrEmote,
            state: WardrobeState,
            primaryAction: () -> Unit
        ): PurchaseConfirmationModal {
            val priceInfo = item.getPricingInfo(state)

            return PurchaseConfirmationModal(
                modalManager,
                listOf(item to priceInfo.letState { it?.baseCost ?: 0 }),
                priceInfo.letState { it?.realCost ?: 0 },
            ) { primaryAction() }
        }

        fun forBundle(modalManager: ModalManager, bundle: Item.Bundle, state: WardrobeState, primaryAction: () -> Unit): PurchaseConfirmationModal {
            val bundleInfo = bundle.getPricingInfo(state)

            val allCosmetics = state.rawCosmetics.getUntracked()
            val unlockedCosmetics = state.unlockedCosmetics.getUntracked()

            val itemsToPurchase = bundle.cosmetics.values
                .filter { it !in unlockedCosmetics }
                .mapNotNull { cosmeticId ->
                    allCosmetics.find { it.id == cosmeticId }?.let { Item.CosmeticOrEmote(it) }
                }

            val itemsAndPriceInfo = itemsToPurchase.map { item ->
                val price = item.getPricingInfoInternal(listOf())
                item to stateOf(price?.baseCost ?: 0)
            }

            return PurchaseConfirmationModal(
                modalManager,
                itemsAndPriceInfo,
                bundleInfo.letState { it?.realCost ?: 0 },
            ) { primaryAction() }
        }
    }
}
