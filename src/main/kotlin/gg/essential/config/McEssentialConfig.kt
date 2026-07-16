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
package gg.essential.config

import gg.essential.Essential
import gg.essential.api.gui.Slot
import gg.essential.commands.EssentialCommandRegistry
import gg.essential.config.EssentialConfig.autoUpdate
import gg.essential.config.EssentialConfig.autoUpdateState
import gg.essential.config.EssentialConfig.collectOptionalTelemetryWithSource
import gg.essential.config.EssentialConfig.discordRichPresenceState
import gg.essential.config.EssentialConfig.essentialEnabledState
import gg.essential.config.EssentialConfig.friendRequestPrivacyState
import gg.essential.config.EssentialConfig.ownCosmeticsVisibleStateWithSource
import gg.essential.config.EssentialConfig.shareProfileLastOnline
import gg.essential.config.EssentialConfig.chatFilterWithSource
import gg.essential.config.EssentialConfig.appearOffline
import gg.essential.connectionmanager.common.packet.Packet
import gg.essential.connectionmanager.common.packet.chat.ChatUnfilteredContentSettingPacket
import gg.essential.connectionmanager.common.packet.cosmetic.ClientCosmeticsUserEquippedVisibilityTogglePacket
import gg.essential.connectionmanager.common.packet.profile.ClientProfileAppearOfflineStateTogglePacket
import gg.essential.connectionmanager.common.packet.profile.ClientProfileLastDisconnectVisibilityTogglePacket
import gg.essential.connectionmanager.common.packet.relationships.privacy.FriendRequestPrivacySettingPacket
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryCollectionCategoriesUpdatePacket
import gg.essential.data.OnboardingData
import gg.essential.data.OnboardingData.hasAcceptedTos
import gg.essential.elementa.components.Window
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.modal.discord.DiscordActivityStatusModal
import gg.essential.gui.modals.ensurePrerequisites
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.notification.sendTosNotification
import gg.essential.gui.vigilancev2.VigilanceV2SettingsGui
import gg.essential.util.AutoUpdate
import gg.essential.util.GuiUtil
import kotlinx.coroutines.launch

object McEssentialConfig {
    private val referenceHolder = ReferenceHolderImpl()

    @JvmOverloads
    fun gui(initialCategory: String? = null): VigilanceV2SettingsGui = VigilanceV2SettingsGui(EssentialConfig.gui, initialCategory)

    fun hookUp() {
        EssentialConfig.doRevokeTos = ::revokeTos

        friendRequestPrivacyState.onSetValue(referenceHolder) { privacy ->
            if (hasAcceptedTos()) {
                val connectionManager = Essential.getInstance().connectionManager

                connectionManager.send(FriendRequestPrivacySettingPacket(privacy)) {
                    val get = it.orElse(null)
                    if (get == null || !(get is ResponseActionPacket && get.isSuccessful)) {
                        Notifications.error("Error", "An unexpected error occurred. Please try again.")
                    }
                }
            }
        }

        fun displayNotConnectedInformation() {
            if (hasAcceptedTos()) {
                Notifications.error(
                    "Essential Network Error",
                    "Unable to establish connection with the Essential Network."
                )
            } else {
                fun showTOS() = GuiUtil.launchModalFlow { ensurePrerequisites() }
                if (GuiUtil.openedScreen() == null) {
                    // Show a notification when we're not in any menu, so it's less intrusive
                    sendTosNotification { showTOS() }
                } else {
                    showTOS()
                }
            }
        }

        var lastVisibilityFromSystemSource = true

        fun restoreVisibilityFromSystemSource() {
            ownCosmeticsVisibleStateWithSource.set(lastVisibilityFromSystemSource to EssentialConfig.CosmeticsVisibilitySource.System)
        }

        ownCosmeticsVisibleStateWithSource.onChange(referenceHolder) { (visible, dataSource) ->
            val notification = when (dataSource) {
                EssentialConfig.CosmeticsVisibilitySource.UserWithNotification -> true
                EssentialConfig.CosmeticsVisibilitySource.UserWithoutNotification -> false
                // Skip system changes (e.g. infra or mod undoing change)
                EssentialConfig.CosmeticsVisibilitySource.System -> {
                    lastVisibilityFromSystemSource = visible
                    return@onChange
                }
            }
            val connectionManager = Essential.getInstance().connectionManager
            if (!connectionManager.isAuthenticated) {
                displayNotConnectedInformation()
                restoreVisibilityFromSystemSource()
                return@onChange
            }
            connectionManager.send(ClientCosmeticsUserEquippedVisibilityTogglePacket(visible)) { optionalPacket ->
                val packet = optionalPacket.orElse(null) ?: return@send run {
                    restoreVisibilityFromSystemSource()
                    Notifications.error("Error", "Failed to toggle cosmetic visibility. Please try again.")
                }

                if (packet is ResponseActionPacket && packet.isSuccessful) {
                    ownCosmeticsVisibleStateWithSource.set(visible to EssentialConfig.CosmeticsVisibilitySource.System)
                    if (notification) {
                        Notifications.push("Your cosmetics are ${if (visible) "shown" else "hidden"}", "") {
                            withCustomComponent(Slot.ICON, if (visible) EssentialPalette.COSMETICS_10X7.create() else EssentialPalette.COSMETICS_OFF_10X7.create())
                        }
                    }
                }
                // Unsuccessful packet means the correct value is already set, so do nothing
            }
        }

        fun linkToggleSettingToInfra(
            name: String,
            setting: MutableState<Pair<Boolean, Boolean>>,
            packet: (Boolean) -> Packet,
        ) {
            setting.onChange(referenceHolder) { (state, updateInfra) ->
                if (!updateInfra) return@onChange
                if (Essential.getInstance().connectionManager.isAuthenticated) {
                    Essential.getInstance().connectionManager.connectionScope.launch {
                        val successful = Essential.getInstance().connectionManager.call(
                            packet(state)
                        ).awaitResponseActionPacket()
                        if (!successful) {
                            Notifications.error(
                                "Error",
                                "Failed to update '$name' setting. Try again."
                            )
                            setting.set(!state to false)
                        }
                    }
                } else {
                    displayNotConnectedInformation()
                    setting.set(!state to false)
                }
            }
        }

        linkToggleSettingToInfra(
            "Share last online time with friends",
            shareProfileLastOnline,
            ::ClientProfileLastDisconnectVisibilityTogglePacket,
        )

        linkToggleSettingToInfra(
            "Appear offline",
            appearOffline,
            ::ClientProfileAppearOfflineStateTogglePacket,
        )

        var lastCollectOptionalTelemetryFromSystemSource = true

        fun restoreCollectOptionalTelemetryFromSystemSource() {
            collectOptionalTelemetryWithSource.set(lastCollectOptionalTelemetryFromSystemSource to false)
        }

        collectOptionalTelemetryWithSource.onChange(referenceHolder) { (accepted, updateInfra) ->
            if (!updateInfra) {
                lastCollectOptionalTelemetryFromSystemSource = accepted
                return@onChange
            }
            val connectionManager = Essential.getInstance().connectionManager
            if (!connectionManager.isAuthenticated) {
                displayNotConnectedInformation()
                restoreCollectOptionalTelemetryFromSystemSource()
                return@onChange
            }
            val acceptedCategories = if (accepted) setOf("REQUIRED", "OPTIONAL") else setOf("REQUIRED")
            val packet = ClientTelemetryCollectionCategoriesUpdatePacket(acceptedCategories)
            connectionManager.connectionScope.launch {
                val successful = connectionManager.call(packet).awaitResponseActionPacket()
                if (!successful) {
                    restoreCollectOptionalTelemetryFromSystemSource()
                    Notifications.error("Error", "Failed to toggle Collect Optional Telemetry. Please try again.")
                }
            }
        }

        var lastToggleableChatFilterFromSystemSource = true

        fun restoreToggleableChatFilterFromSystemSource() {
            chatFilterWithSource.set(lastToggleableChatFilterFromSystemSource to false)
        }

        chatFilterWithSource.onChange(referenceHolder) { (chatFiltered, updateInfra) ->
            if (!updateInfra) {
                lastToggleableChatFilterFromSystemSource = chatFiltered
                return@onChange
            }
            val connectionManager = Essential.getInstance().connectionManager
            if (!connectionManager.isAuthenticated) {
                displayNotConnectedInformation()
                restoreToggleableChatFilterFromSystemSource()
                return@onChange
            }
            val packet = ChatUnfilteredContentSettingPacket(!chatFiltered)
            connectionManager.connectionScope.launch {
                val successful = connectionManager.call(packet).awaitResponseActionPacket()
                if (!successful) {
                    restoreToggleableChatFilterFromSystemSource()
                    Notifications.error("Error", "Failed to toggle Chat Filter. Please try again.")
                }
            }
        }

        discordRichPresenceState.onSetValue(referenceHolder) { enabled ->
            if (!enabled) return@onSetValue

            GuiUtil.pushModal { DiscordActivityStatusModal(it) }
        }

        essentialEnabledState.onSetValue(referenceHolder) { enabling ->
            Window.enqueueRenderOperation { toggleEssential(enabling) }
        }

        autoUpdate = AutoUpdate.autoUpdate.get()
        autoUpdateState.onSetValue(referenceHolder) { shouldAutoUpdate ->
            if (shouldAutoUpdate != AutoUpdate.autoUpdate.get()) {
                // User explicitly changed the value
                // Delayed to allow setAutoUpdate to confirm the value of the autoUpdate setting
                Window.enqueueRenderOperation {
                    AutoUpdate.setAutoUpdates(shouldAutoUpdate)
                }
            }
        }
    }

    private fun checkSPS(): Boolean {
        val currentlyHosting = Essential.getInstance().connectionManager.spsManager.localSession != null
        return if (currentlyHosting) {
            Notifications.error("Error", "You cannot disable Essential while hosting a world.")
            false
        } else true
    }

    private fun toggleEssential(enabling: Boolean) {
        // Trying to disable Essential while in an SPS world
        if (!enabling && !checkSPS()) {
            EssentialConfig.essentialEnabled = true
            return
        }

        EssentialConfig.essentialEnabled = enabling

        Essential.getInstance().keybindingRegistry.refreshBinds()
        (Essential.getInstance().commandRegistry() as EssentialCommandRegistry).checkMiniCommands()
        Essential.getInstance().checkListeners()

        if (!enabling) {
            Essential.getInstance().connectionManager.onTosRevokedOrEssentialDisabled()
        }
    }

    private fun revokeTos() {
        if (checkSPS()) {
            OnboardingData.setDeniedTos()
            Essential.getInstance().connectionManager.onTosRevokedOrEssentialDisabled()
        }
    }
}
