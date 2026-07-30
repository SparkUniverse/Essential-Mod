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
package gg.essential.gui.modals

import gg.essential.config.EssentialConfig
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.compactFullEssentialToggle
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedWidth
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.hoverTooltip
import gg.essential.gui.layoutdsl.inheritHoverScope
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.whenTrue
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.notification.Notifications
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.toState
import java.awt.Color
import kotlin.coroutines.cancellation.CancellationException

class UpdateAvailableModal(modalManager: ModalManager, private val continuation: ModalFlow.ModalContinuation<Boolean>) : EssentialModal2(modalManager, false) {
    private val autoUpdateManager = platform.autoUpdateManager

    override fun onOpen() {
        super.onOpen()

        autoUpdateManager.dismissUpdateNotification()
    }

    override fun LayoutScope.layoutTitle() {
        text(autoUpdateManager.getNotificationTitle(), Modifier.whenTrue({ autoUpdateManager.isUpdateRequired }, Modifier.color(EssentialPalette.MODAL_WARNING)))
    }

    override fun LayoutScope.layoutBody() {
        val autoUpdate = EssentialConfig.autoUpdateState
        column(Modifier.fillWidth(), Arrangement.spacedBy(17f)) {
            ifNotNull(autoUpdateManager.changelog.toState()) { changelog ->
                wrappedText(changelog, centered = true)
            }
            row(
                Modifier.childBasedWidth(3f).hoverScope().onLeftClick {
                    USound.playButtonPress()
                    autoUpdate.set { !it }
                },
                Arrangement.spacedBy(9f),
            ) {
                text("Auto-updates", Modifier.color(EssentialPalette.TEXT_DISABLED).shadow(Color.BLACK))
                box(Modifier.childBasedHeight()) {
                    compactFullEssentialToggle(
                        autoUpdate,
                        Modifier.inheritHoverScope(),
                        offColor = EssentialPalette.TEXT_DISABLED,
                    )
                }
            }
        }
    }

    override fun LayoutScope.layoutButtons() {
        primaryAndCancelButtons(
            "Update",
            "Cancel",
            primaryAction = {
                replaceWith(continuation.resume(true))
            },
            cancelAction = {
                replaceWith(continuation.resumeImmediately(false))
            },
            primaryStyle = OutlineButtonStyle.GREEN
        )
    }
}

suspend fun ModalFlow.updateAvailableModal() {
    val acceptedUpdate = awaitModal { continuation -> UpdateAvailableModal(modalManager, continuation) }
    if (acceptedUpdate) {
        platform.autoUpdateManager.acceptUpdate()
        awaitModal { EssentialRebootUpdateModal(modalManager) }
    } else {
        platform.autoUpdateManager.ignoreUpdate()
        throw CancellationException("Update Denied")
    }
}

class EssentialRebootUpdateModal(modalManager: ModalManager) : EssentialModal2(modalManager, true) {
    override fun LayoutScope.layoutBody() {
        wrappedText("Essential will update the next time\nyou launch the game.", centered = true)
    }

    override fun LayoutScope.layoutButtons() {
        row(Arrangement.spacedBy(8f)) {
            cancelButton("Quit & Update", Modifier.hoverScope().hoverTooltip("This will close your game!", position = EssentialTooltip.Position.ABOVE, padding = 4f)) {
                platform.shutdown()
            }
            primaryButton("Okay") {
                Notifications.push("Update Confirmed", "Essential will update next time you launch the game!")
                close()
            }
        }
    }
}

class UpdateRequiredModal(modalManager: ModalManager) : EssentialModal2(modalManager, false) {

    override fun LayoutScope.layoutBody() {
        wrappedText("Sorry, you are on an outdated version of Essential. Restart your game to update.", centered = true)
    }

    override fun LayoutScope.layoutButtons() {
        row(Arrangement.spacedBy(8f)) {
            cancelButton("Cancel")
            primaryButton(
                "Quit & Update",
                Modifier.hoverTooltip("This will close your game!", position = EssentialTooltip.Position.ABOVE, padding = 4f),
                action = { platform.shutdown() },
                style = OutlineButtonStyle.GRAY
            )
        }
    }
}

suspend fun ModalFlow.updateRequiredModal() {
    awaitModal<Unit> { UpdateRequiredModal(modalManager) }
}
