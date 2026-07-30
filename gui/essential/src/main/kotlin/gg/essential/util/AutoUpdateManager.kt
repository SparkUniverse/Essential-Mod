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
package gg.essential.util

import gg.essential.gui.elementa.state.v2.State
import java.util.concurrent.CompletableFuture

interface AutoUpdateManager {
    /** `true` when an update is required because infra longer supports the current (old) version. */
    val isUpdateRequired: Boolean

    val updateAvailable: State<Boolean>
    val updateIgnored: State<Boolean>

    val changelog: CompletableFuture<String?>

    fun acceptUpdate()
    fun ignoreUpdate()

    // FIXME these don't really belong in here
    fun getNotificationTitle(includeLoaderText: Boolean = true): String
    fun dismissUpdateNotification()
}
