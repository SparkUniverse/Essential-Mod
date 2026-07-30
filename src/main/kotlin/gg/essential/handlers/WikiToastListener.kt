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
package gg.essential.handlers

import gg.essential.api.gui.NotificationType
import gg.essential.api.gui.Slot
import gg.essential.data.OnboardingData
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.toastButton
import gg.essential.universal.UDesktop
import java.net.URI

object WikiToastListener {
    private val refHolder = ReferenceHolderImpl()

    fun register() {
        effect(refHolder) {
            if (OnboardingData.acceptedTos() && !OnboardingData.hasShownWikiToast()) {
                showWikiToast()
            }
        }
    }

    private fun showWikiToast() {
        Notifications.pushPersistentToast(
            title = "Need Help?",
            message = "Visit our wiki for more information.",
            action = {},
            // The toast should show on each restart unless explicitly dismissed by the user.
            close = OnboardingData::setHasShownWikiToast
        ) {
            type = NotificationType.INFO
            uniqueId = object {}.javaClass

            withCustomComponent(Slot.ICON, EssentialPalette.ROUND_WARNING_7X.create())
            withCustomComponent(Slot.ACTION, toastButton("Visit Wiki") {
                UDesktop.browse(URI.create("https://essential.gg/wiki"))
            })
        }
    }
}