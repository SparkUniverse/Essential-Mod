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

import gg.essential.gui.common.modal.ConfirmDenyModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.overlay.ModalManager
import gg.essential.util.GuiEssentialPlatform.Companion.platform

class AddAccountModal(modalManager: ModalManager) : ConfirmDenyModal(modalManager, false) {
    init {
        configure {
            contentText = "Log into your account on our website to securely add it in-game."
            primaryButtonText = "Open Browser"
        }

        onPrimaryAction {
            platform.openWebAccountManager()
        }
    }
}