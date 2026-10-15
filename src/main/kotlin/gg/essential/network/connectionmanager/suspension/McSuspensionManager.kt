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
package gg.essential.network.connectionmanager.suspension

import gg.essential.Essential
import gg.essential.api.gui.GuiRequiresTOS
import gg.essential.gui.InternalEssentialGUI
import gg.essential.gui.friends.SocialMenu
import gg.essential.gui.modals.SuspensionModal
import gg.essential.gui.overlay.ModalFlow
import gg.essential.network.connectionmanager.ConnectionManager
import gg.essential.network.connectionmanager.social.Suspension
import gg.essential.universal.UMinecraft
import gg.essential.universal.UScreen
import gg.essential.util.GuiUtil

class McSuspensionManager(connectionManager: ConnectionManager) : SuspensionManager(connectionManager) {

    init {
        Essential.EVENT_BUS.register(this)
    }

    override fun showSuspension(suspension: Suspension) {
        GuiUtil.launchModalFlow {
            suspensionModal(suspension)
        }
    }
}

suspend fun ModalFlow.suspensionModal(suspension: Suspension) {
    val connectionManager = Essential.getInstance().connectionManager
    val suspensionManager = connectionManager.suspensionManager

    awaitModal { continuation ->
        suspensionManager.markSeen()
        SuspensionModal(
            modalManager,
            suspension,
            connectionManager.chatManager.getReportReasons(UMinecraft.getSettings().language),
            continuation
        )
    }

    val openedScreen = GuiUtil.openedScreen()
    if (openedScreen is InternalEssentialGUI && (openedScreen is SocialMenu || (suspension.isPermanent && openedScreen is GuiRequiresTOS))) {
        UScreen.displayScreen(null)
    }
}