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
package gg.essential.gui.sps

import gg.essential.Essential
import gg.essential.elementa.UIComponent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.modals.select.SelectModal
import gg.essential.gui.modals.select.offlinePlayers
import gg.essential.gui.modals.select.onlinePlayers
import gg.essential.gui.modals.select.selectModal
import gg.essential.gui.notification.sendOutgoingSpsInviteNotification
import gg.essential.gui.overlay.ModalManager
import gg.essential.universal.UMinecraft.getMinecraft
import gg.essential.universal.USound
import gg.essential.util.*
import gg.essential.vigilance.utils.onLeftClick
import java.util.*

object InviteFriendsModal {

    fun showInviteModal(
        modalManager: ModalManager,
        onComplete: () -> Unit,
    ): Modal {
        val connectionManager = Essential.getInstance().connectionManager
        val currentServerData = getMinecraft().currentServerData

        val invites = if (currentServerData != null) {
            connectionManager.socialManager.getInvitesOnServer(currentServerData.serverIP)
        } else {
            emptySet()
        }

        val reInviteEnabledStateList = mutableMapOf<UUID, MutableState<Boolean>>()

        val updateInvites: (newInvites: Set<UUID>) -> Unit = { newInvites ->
            if (currentServerData != null) {
                connectionManager.socialManager.setInvitedFriendsOnServer(currentServerData.serverIP, newInvites)
            }
        }

        fun getReInviteEnabledState(uuid: UUID): MutableState<Boolean> {
            return reInviteEnabledStateList.getOrPut(uuid) { mutableStateOf(true) }
        }

        fun startReInviteTimer(uIComponent: UIComponent, uuid: UUID) {
            val reInviteEnabledState = getReInviteEnabledState(uuid)

            reInviteEnabledState.set(false)

            uIComponent.delay(5000) {
                reInviteEnabledState.set(true)
            }
        }

        /**
         * Re-invites a player returns true if successful
         */
        fun reInvite(selectModal: SelectModal<UUID>, uuid: UUID): Boolean {
            val reInviteEnabledState = getReInviteEnabledState(uuid)

            if (!reInviteEnabledState.get()) {
                return false
            }

            updateInvites(selectModal.selectedIdentifiers - uuid)
            updateInvites(selectModal.selectedIdentifiers + uuid)

            sendInviteNotification(uuid)
            return true
        }

        val title = "Invite friends to server"
        val modalSimpleName = "InviteFriendsToServer"
        return selectModal(modalManager, title, modalSimpleName) {
            fun LayoutScope.customPlayerEntry(selected: MutableState<Boolean>, uuid: UUID) {
                val reInviteEnabled = getReInviteEnabledState(uuid)
                val reInviteVisible = memo {
                    selected()
                }

                fun LayoutScope.reinviteButton(modifier: Modifier = Modifier) {
                    iconButton(
                        modifier = modifier,
                        icon = stateOf(EssentialPalette.REINVITE_5X),
                        color = { _ ->
                            if (reInviteEnabled()) EssentialPalette.TEXT else EssentialPalette.TEXT_DISABLED
                        },
                        backgroundColor = { hovered ->
                            when {
                                !reInviteEnabled() -> EssentialPalette.COMPONENT_BACKGROUND
                                hovered() -> EssentialPalette.TEXT_DISABLED
                                else -> EssentialPalette.BUTTON_HIGHLIGHT
                            }
                        },
                        tooltip = stateOf("Re-invite"),
                    ).apply {
                        rebindEnabled(reInviteEnabled.toV1(this))
                    }.onLeftClick { event ->
                        if (reInvite(findParentOfType(), uuid)) {
                            USound.playButtonPress()
                            event.stopPropagation()
                            startReInviteTimer(this, uuid)
                        }
                    }
                }

                box(Modifier.fillParent()) {
                    row(Modifier.fillParent(padding = 3f)) {
                        playerEntry(selected, uuid)

                        row(Arrangement.spacedBy(3f)) {
                            if_(reInviteVisible) {
                                reinviteButton(Modifier.width(9f))
                            }

                            defaultAddRemoveButton(selected)
                        }
                    }
                }.onLeftClick { event ->
                    if (reInviteVisible.get()) {
                        if (reInvite(findParentOfType(), uuid)) {
                            USound.playButtonPress()
                            event.stopPropagation()
                            startReInviteTimer(this, uuid)
                        }
                    } else {
                        USound.playButtonPress()
                        event.stopPropagation()
                        selected.set { !it }
                    }
                }
            }

            onlinePlayers(LayoutScope::customPlayerEntry)
            offlinePlayers(LayoutScope::customPlayerEntry)

            // Set the initially selected user to the initial invites / invited users
            setInitiallySelected(*invites.toTypedArray())

            modalSettings {
                primaryButtonText = "Done"

                hideCancelButton()
            }

            selectTooltip = "Invite"
            deselectTooltip = "Cancel"

            requiresSelection = false
            requiresButtonPress = false
        }.onPrimaryAction { newInvites ->
            onComplete()
        }.onSelection { identifier, selected ->
            updateInvites(selectedIdentifiers)

            if (selected) {
                sendInviteNotification(identifier)
                startReInviteTimer(this, identifier)
            }
        }
    }

    fun sendInviteNotification(uuid: UUID) {
        UUIDUtil.getName(uuid).thenAcceptOnMainThread { sendOutgoingSpsInviteNotification(it) }
    }
}
