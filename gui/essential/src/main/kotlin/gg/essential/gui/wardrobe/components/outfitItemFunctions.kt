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
package gg.essential.gui.wardrobe.components

import gg.essential.api.gui.Slot
import gg.essential.elementa.components.Window
import gg.essential.elementa.events.UIClickEvent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.ContextOptionMenu
import gg.essential.gui.common.modal.CancelableInputModal
import gg.essential.gui.common.modal.DangerConfirmationEssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.notification.Notifications
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.wardrobe.Item
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.registerBundle
import gg.essential.mod.cosmetics.CosmeticBundle
import gg.essential.mod.cosmetics.CosmeticTier
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform

fun displayOutfitOptions(item: Item.OutfitItem, wardrobeState: WardrobeState, event: UIClickEvent, onClose: () -> Unit = {}) {

    val options = mutableListOf<ContextOptionMenu.Item>()
    options.add(
        ContextOptionMenu.Option("Rename", image = EssentialPalette.PENCIL_7x7) {
            platform.pushModal { manager ->
                RenameOutfitModal(manager, item.name).onPrimaryActionWithValue {
                    wardrobeState.outfitManager.renameOutfit(item.id, it)
                }
            }
        }
    )

    options.add(
        ContextOptionMenu.Option(
            if (item.isFavorite) "Remove Favorite" else "Favorite",
            image = EssentialPalette.HEART_FILLED_9X
        ) {
            wardrobeState.setFavorite(item, !item.isFavorite)
        }
    )

    options.add(
        ContextOptionMenu.Divider
    )

    options.add(
        ContextOptionMenu.Option(
            "Duplicate",
            image = EssentialPalette.COPY_9X
        ) {
            wardrobeState.outfitManager.addOutfit(item.name, item.skinId, item.cosmetics, item.settings) {
                if (it == null) {
                    Notifications.push("Unable to add new outfit.", "Please try again later.")
                } else {
                    Notifications.push("Outfit created", "") {
                        withCustomComponent(Slot.ICON, EssentialPalette.COSMETICS_10X7.create())
                    }

                    wardrobeState.outfitManager.setSelectedOutfit(it.id)
                }
            }
        }
    )

    if (wardrobeState.outfitItems.get().size > 1) {
        options.add(
            ContextOptionMenu.Divider
        )

        options.add(
            ContextOptionMenu.Option(
                "Delete",
                image = EssentialPalette.TRASH_9X,
                hoveredColor = EssentialPalette.TEXT_WARNING
            ) {
                platform.pushModal { manager ->
                    DeleteOutfitConfirmationModal(manager, item.name).onPrimaryAction {
                        wardrobeState.outfitManager.deleteOutfit(item.id)
                    }
                }
            }
        )
    }

    val cosmeticsDataWithChanges = wardrobeState.cosmeticsManager.cosmeticsDataWithChanges
    if (cosmeticsDataWithChanges != null) {
        options.add(
            ContextOptionMenu.Option("Create bundle", image = EssentialPalette.PLUS_7X) {
                platform.pushModal { manager ->
                    CancelableInputModal(manager, "Bundle id").configure {
                        titleText = "Create New Bundle"
                        contentText = "Enter the id for the new bundle."
                    }.apply {
                        onPrimaryActionWithValue { id ->
                            if (cosmeticsDataWithChanges.getCosmeticBundle(id) != null) {
                                setError("That id already exists!")
                                return@onPrimaryActionWithValue
                            }
                            cosmeticsDataWithChanges.registerBundle(
                                id,
                                item.name,
                                CosmeticTier.COMMON,
                                0f,
                                false,
                                CosmeticBundle.Skin(item.skin, item.name),
                                item.cosmetics.filterValues { it != "BASE_ICON" },
                                item.settings,
                            )
                        }
                    }
                }
            }
        )
    }

    ContextOptionMenu.create(
        ContextOptionMenu.Position(event.absoluteX, event.absoluteY),
        Window.of(event.currentTarget),
        *options.toTypedArray(),
        onClose = onClose
    )

}

fun handleOutfitLeftClick(item: Item.OutfitItem, wardrobeState: WardrobeState, event: UIClickEvent) {

    event.stopPropagation()
    USound.playButtonPress()

    wardrobeState.selectedItem.set(item)
    wardrobeState.outfitManager.setSelectedOutfit(item.id)

}

class RenameOutfitModal(manager: ModalManager, name: String) : CancelableInputModal(manager, "Outfit Name", maxLength = 22, initialText = name) {
    init {
        configure {
            titleText = "Rename Outfit"
            contentText = "Enter a new name for your outfit."
            primaryButtonText = "Rename"
            titleTextColor = EssentialPalette.TEXT_HIGHLIGHT

            cancelButtonText = "Back"
        }
    }
}

class DeleteOutfitConfirmationModal(manager: ModalManager, name: String) : DangerConfirmationEssentialModal(manager, "Delete", true) {
    init {
        configure {
            titleText = "Are you sure you want to delete $name?"
        }
    }
}
