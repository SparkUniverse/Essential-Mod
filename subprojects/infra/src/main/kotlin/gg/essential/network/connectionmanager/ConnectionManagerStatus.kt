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
package gg.essential.network.connectionmanager

sealed interface ConnectionManagerStatus {
    data object Success : ConnectionManagerStatus
    data object Outdated : ConnectionManagerStatus
    data object TOSNotAccepted : ConnectionManagerStatus
    data object EssentialDisabled : ConnectionManagerStatus
    data object UserSuspended: ConnectionManagerStatus

    sealed interface Error : ConnectionManagerStatus {
        data object HostsFileModified : Error
        data object DNSFailure : Error

        data object GeneralFailure : Error

        data class AuthenticationFailure(val throwable: Throwable) : Error
    }
}