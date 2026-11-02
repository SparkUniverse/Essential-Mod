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
@file:JvmName("Gifting")
package gg.essential.gui.wardrobe.components

import gg.essential.api.gui.NotificationType
import gg.essential.api.gui.Slot
import gg.essential.connectionmanager.common.packet.cosmetic.ClientCosmeticBulkRequestUnlockStatePacket
import gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticBulkRequestUnlockStateResponsePacket
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.modal.EssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.addAll
import gg.essential.gui.elementa.state.v2.collections.MutableTrackedList
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.isNotEmpty
import gg.essential.gui.elementa.state.v2.mapList
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.modals.ensurePrerequisites
import gg.essential.gui.modals.select.selectModal
import gg.essential.gui.modals.select.users
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.content.CosmeticPreviewToastComponent
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.wardrobe.Item
import gg.essential.gui.wardrobe.ItemId
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.modals.FeatureDisabledModal
import gg.essential.gui.wardrobe.giftCosmeticOrEmote
import gg.essential.gui.wardrobe.modals.CoinsPurchaseModal
import gg.essential.gui.wardrobe.modals.PurchaseConfirmationModal
import gg.essential.network.connectionmanager.coins.CoinsManager
import gg.essential.network.connectionmanager.features.Feature
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.ChatColor
import gg.essential.universal.USound
import gg.essential.util.CachedAvatarImage
import gg.essential.util.EssentialSounds
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.UuidNameLookup
import gg.essential.vigilance.utils.onLeftClick
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val LOGGER = LoggerFactory.getLogger("Essential Logger")

fun openGiftModal(item: Item.CosmeticOrEmote, state: WardrobeState) {
    if (platform.disabledFeaturesManager.isFeatureDisabled(Feature.COSMETIC_PURCHASE)) {
        platform.pushModal { FeatureDisabledModal.store(it) }
        return
    }

    val requiredCoinsSpent = state.settings.giftingCoinSpendRequirement.get()
    val coinsSpent = state.coinsSpent.getUntracked()
    if (coinsSpent == null) {
        showErrorToast("Please try again later")
        return
    }
    if (coinsSpent < requiredCoinsSpent) {
        launchModalFlow(platform.createModalManager()) {
            cannotGiftYetModal(requiredCoinsSpent)
        }
        return
    }

    // Get all friends except those who already own the item to gift
    val socialStates = platform.createSocialStates()
    val allFriends = socialStates.relationships.friends.mapList { list -> list.filter { !socialStates.isSuspended(it)() } }
    val validFriends = mutableListStateOf<UUID>()
    val loadingFriends = mutableStateOf(allFriends.getUntracked().isNotEmpty())

    platform.cmConnection.send(ClientCosmeticBulkRequestUnlockStatePacket(allFriends.getUntracked().toSet(), item.cosmetic.id)) { maybePacket ->
        ServerCosmeticBulkRequestUnlockStateResponsePacket::class.java // FIXME workaround for feature-flag-processor eating the packet
        when (val packet = maybePacket.orElse(null)) {
            is ServerCosmeticBulkRequestUnlockStateResponsePacket -> {
                validFriends.addAll(packet.unlockStates.filter { !it.value }.keys.toList())
            }
            else -> {
                showErrorToast("Something went wrong, please try again.")
                val prefix = (packet as? ResponseActionPacket)?.let { "$it - " } ?: ""
                LOGGER.error(prefix + "Failed to validate unlock status for ${item.cosmetic.displayName} in friends list.")
            }
        }
        loadingFriends.set(false)
    }

    launchModalFlow(platform.createModalManager()) {
        ensurePrerequisites(Feature.SOCIAL)

        val selectedUsers = selectFriendsToGiftModal(item, state, allFriends, validFriends, loadingFriends)
            ?: return@launchModalFlow
        val multiplier = selectedUsers.size
        val priceInfo = item.getPricingInfo(state)
        awaitModal { continuation ->
            PurchaseConfirmationModal(
                modalManager,
                List(multiplier) { item to priceInfo.letState { it?.baseCost ?: 0 } },
                priceInfo.letState { (it?.realCost ?: 0) * multiplier },
            ) { this.replaceWith(continuation.resumeImmediately(Unit)) }
        }
        giftItemToFriends(item, selectedUsers, state)
    }
}

fun openWardrobeWithHighlight(itemId: ItemId) {
    platform.openWardrobe(itemId)
}

private fun showErrorToast(message: String) {
    Notifications.push("Gifting failed", message) { type = NotificationType.ERROR }
}

private suspend fun giftItemToFriends(item: Item.CosmeticOrEmote, uuids: Set<UUID>, state: WardrobeState) {
    val cost = (item.getCost(state).getUntracked() ?: 0) * uuids.size
    val coins = state.coins.getUntracked()
    if (coins == null) {
        showErrorToast("Error getting your coins balance!")
        return
    }
    if (cost > coins) {
        CoinsPurchaseModal.open(state, cost)
        return
    }

    coroutineScope {
        uuids.forEach { uuid ->
            launch {
                val deferredUsername = UuidNameLookup.getName(uuid).asDeferred()
                val username = try {
                    deferredUsername.await()
                } catch (e: Throwable) {
                    showErrorToast("Something went wrong, please try again.")
                    LOGGER.error("Failed to lookup username for $uuid", e)
                    return@launch
                }
                val (success, errorCode) = suspendCoroutine { continuation ->
                    state.giftCosmeticOrEmote(item, uuid) { success, errorCode ->
                        continuation.resume(success to errorCode)
                    }
                }
                if (!success) {
                    val errorMessage = when (errorCode) {
                        "TARGET_MUST_BE_FRIEND" -> "$username is not your friend!"
                        "ESSENTIAL_USER_NOT_FOUND" -> "$username is not an Essential user!"
                        "Cosmetic already unlocked." -> "$username already owns this item!"
                        "PRODUCTS_NOT_FOUND" -> "${item.name} is no longer available to purchase."
                        else -> "Something went wrong gifting to $username, please try again."
                    }
                    showErrorToast(errorMessage)
                    return@launch
                }
                EssentialSounds.playPurchaseConfirmationSound()
                showGiftSentToast(item.cosmetic, username)
            }
        }
    }
}

fun showGiftSentToast(cosmetic: Cosmetic, username: String) {
    Notifications.push("", "${ChatColor.WHITE + cosmetic.displayName + ChatColor.RESET} has been gifted to $username.") {
        withCustomComponent(Slot.ACTION, CosmeticPreviewToastComponent(cosmetic))
    }
}

fun showGiftReceivedToast(cosmetic: Cosmetic, uuid: UUID, username: String) {
    Notifications.push(username, "", 4f, {
        openWardrobeWithHighlight(ItemId.CosmeticOrEmote(cosmetic.id))
    }) {
        withCustomComponent(Slot.ICON, CachedAvatarImage.create(uuid))
        withCustomComponent(Slot.SMALL_PREVIEW, CosmeticPreviewToastComponent(cosmetic))
        withCustomComponent(Slot.LARGE_PREVIEW, UIContainer().apply {
            val colorModifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.TEXT_SHADOW_LIGHT)
            layout(Modifier.fillWidth().childBasedHeight()) {
                wrappedText("{gift} Sent you a gift", Modifier.alignHorizontal(Alignment.Start), colorModifier) {
                    "gift" { icon(EssentialPalette.WARDROBE_GIFT_7X, colorModifier) }
                }
            }
        })
    }
}

suspend fun ModalFlow.selectFriendsToGiftModal(
    item: Item.CosmeticOrEmote,
    state: WardrobeState,
    allFriends: ListState<UUID>,
    validFriends: State<MutableTrackedList<UUID>>,
    loadingFriends: State<Boolean>,
): Set<UUID>? {

    return selectModal(
        "Select friends to gift\nthem ${ChatColor.WHITE + item.name + ChatColor.RESET}.", "SelectFriendsToGift"
    ) {
        modalSettings {
            primaryButtonText = "Purchase"
            titleTextColor = EssentialPalette.TEXT
        }

        emptyText(memo {
            when {
                allFriends().isEmpty() -> "You haven't added any friends yet. You can add them in the social menu."
                loadingFriends() -> "Loading..."
                else -> "Your friends already own this item."
            }
        })

        requiresButtonPress = false
        requiresSelection = true

        users("Friends", validFriends) {selected, uuid ->
            box(Modifier.fillParent()) {
                row(Modifier.fillParent(padding = 3f)) {
                    playerEntry(selected, uuid)
                    checkboxAlt(selected)
                }
            }.onLeftClick { event ->
                USound.playButtonPress()
                event.stopPropagation()
                selected.set { !it }
            }
        }

        val selectedCount = mutableStateOf(0)
        modalSettings {
            selectedCount.set(selectedIdentifiers.size)
            onSelection { _, _ ->
                selectedCount.set(selectedIdentifiers.size)
            }
        }

        extraContent = {
            val quantityText = selectedCount.map { "${it}x ${item.name}" }
            val cost = memo { (item.getCost(state)() ?: 0) * selectedCount() }
            val costText = cost.letState { CoinsManager.COIN_FORMAT.format(it) }
            val shadowModifier = Modifier.shadow(EssentialPalette.BLACK)

            if_(validFriends.isNotEmpty()) {
                column(Modifier.fillWidth(rightPadding = 1f)) {
                    spacer(height = 10f)
                    row(Modifier.fillWidth(), Arrangement.SpaceBetween) {
                        text(quantityText, shadowModifier.color(EssentialPalette.TEXT_MID_GRAY))
                        row(Arrangement.spacedBy(2f)) {
                            text(costText, shadowModifier.color(EssentialPalette.TEXT))
                            image(EssentialPalette.COIN_7X, shadowModifier)
                        }
                    }
                    spacer(height = 1f)
                }
            }
        }
    }
}

suspend fun ModalFlow.cannotGiftYetModal(requiredCoinsSpent: Int) {
    awaitModal<Unit> {
        val modal = EssentialModal(modalManager, false).configure {
            titleText = "You can't gift yet..."
            titleTextColor = EssentialPalette.MODAL_WARNING
            primaryButtonText = "Okay!"
        }

        val textModifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.BLACK)

        modal.configureLayout { customContent ->
            customContent.layoutAsColumn {
                spacer(height = 13f)
                text("You can only send gifts", textModifier)
                spacer(height = 4f)
                row {
                    text("after spending $requiredCoinsSpent ", textModifier)
                    spacer(width = 1f)
                    image(EssentialPalette.COIN_7X)
                }
                spacer(height = 17f)
            }
        }
        modal
    }
}

