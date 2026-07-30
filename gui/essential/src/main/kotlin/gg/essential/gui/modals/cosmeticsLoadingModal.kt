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

import gg.essential.gui.common.modal.ConnectingModal
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.awaitValue
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.overlay.ModalFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

suspend fun ModalFlow.cosmeticsLoadingModal(cosmeticsLoaded: State<Boolean>, timeout: Duration): Boolean {
    return awaitModal { continuation ->
        val hasCompleted = mutableStateOf<Boolean?>(null)
        modalManager.coroutineScope.launch {
            val completed = withTimeoutOrNull(timeout) {
                cosmeticsLoaded.awaitValue(true)
            }
            hasCompleted.set(completed == true)
        }
        ConnectingModal(
            modalManager,
            "Loading Wardrobe...",
            isConnecting = { hasCompleted() == null },
            continuation = continuation
        )
    }
}