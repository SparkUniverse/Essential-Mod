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
package gg.essential.gui.overlay

import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.collections.effectForEach
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import kotlinx.coroutines.cancel

/**
 * Launches a modal flow for each entry in the list and cancels the flow again when the entry is removed.
 */
fun <T> launchModalFlowForEach(
    referenceHolder: ReferenceHolder,
    listState: ListState<T>,
    block: suspend ModalFlow.(T) -> Unit,
) {
    listState.effectForEach(referenceHolder) { value ->
        val modalManager = platform.createModalManager()
        launchModalFlow(modalManager) {
            block(value)
        }
        AutoCloseable {
            modalManager.coroutineScope.cancel()
        }
    }
}
