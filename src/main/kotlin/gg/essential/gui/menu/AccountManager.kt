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
package gg.essential.gui.menu

import gg.essential.Essential
import gg.essential.gui.EssentialPalette
import gg.essential.gui.account.factory.InitialSessionFactory
import gg.essential.gui.account.factory.ManagedSessionFactory
import gg.essential.gui.common.modal.DangerConfirmationEssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.await
import gg.essential.gui.elementa.state.v2.awaitValue
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.notification.iconAndMarkdownBody
import gg.essential.gui.overlay.ModalManager
import gg.essential.handlers.account.WebAccountManager
import gg.essential.network.connectionmanager.ConnectionManagerStatus
import gg.essential.universal.UMinecraft
import gg.essential.util.GuiUtil
import gg.essential.util.USession
import gg.essential.util.colored
import gg.essential.util.executor
import gg.essential.util.raceOf
import gg.essential.util.setSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class AccountManager {

    private val referenceHolder = ReferenceHolderImpl()
    private val allAccountsMutable = mutableStateOf<List<AccountInfo>>(listOf())
    private val originalAccountsMutable = mutableStateOf<List<AccountInfo>>(listOf())
    val allAccounts: ListState<AccountInfo> = allAccountsMutable.toListState()
    val originalAccounts: ListState<AccountInfo> = originalAccountsMutable.toListState()

    init {
        USession.active.onSetValueAndNow(referenceHolder) {
            refreshAccounts()
        }
        WebAccountManager.mostRecentAccountManager = WeakReference(this)
    }

    private fun refreshAccounts() {
        val sessionFactories = Essential.getInstance().sessionFactories
        val accounts = sessionFactories
            .flatMap { it.sessions.values }
            .distinctBy { it.uuid }
            .map { AccountInfo(it.uuid, it.username) }
        allAccountsMutable.set(accounts.toList())

        // Find original account(s) that cannot be removed
        val managedSessions = sessionFactories.filterIsInstance<ManagedSessionFactory>().flatMap { it.sessions.keys }
        originalAccountsMutable.set(accounts.filterNot { it.uuid in managedSessions })
    }

    /**
     * Logs in with the specified account's [uuid].
     * This will refresh the session and re-establish the connection.
     */
    fun login(uuid: UUID) {
        val isSwitching = mutableStateOf(true)
        val modalManager = GuiUtil.pushModal { AccountSwitchingModal(it, isSwitching) }
        refreshSession(uuid) { session, error ->
            if (error == null) {
                monitorSwitching(modalManager.coroutineScope, isSwitching, session.username)
            } else {
                isSwitching.set(false)
                Essential.logger.error("Account Error: $error")
                Notifications.error("Account Error", "Something went wrong\nduring login.")
            }
            refreshAccounts()
        }
    }

    private fun monitorSwitching(coroutineScope: CoroutineScope, isSwitching: MutableState<Boolean>, username: String) {
        coroutineScope.launch {
            val connectionStatus = Essential.getInstance().connectionManager.connectionStatus
            // Stop switching if there is a connection error, cosmetics have finished loading or after a 10-second delay
            raceOf(
                { connectionStatus.await { it != null && it != ConnectionManagerStatus.Success } },
                {
                    connectionStatus.awaitValue(ConnectionManagerStatus.Success)
                    Essential.getInstance().connectionManager.cosmeticsManager.cosmeticsLoaded.awaitValue(true)
                },
                { delay(10.seconds) }
            )
            isSwitching.set(false)
            Notifications.push("", "", 1f) {
                iconAndMarkdownBody(
                    EssentialPalette.SMILEY_8X.create(),
                    "Logged in as ${username.colored(EssentialPalette.TEXT_HIGHLIGHT)}"
                )
            }
        }
    }

    /** Display a modal prompting the user to confirm if they want to remove the account which [uuid] belongs to */
    fun promptRemove(uuid: UUID, name: String) {
        GuiUtil.pushModal { manager ->
            RemoveAccountConfirmationModal(manager, name).onPrimaryAction {
                removeAccount(uuid)
            }
        }
    }

    /** Removes the account which [uuid] belongs to from the [AccountManager] */
    fun removeAccount(uuid: UUID) {
        Essential.getInstance().sessionFactories.filterIsInstance<ManagedSessionFactory>().forEach { it.remove(uuid) }
        refreshAccounts()
    }

    companion object {
        /** Refresh the current session with the option to [force] refresh and specify an optional [callback]. */
        @JvmStatic
        @JvmOverloads
        fun refreshCurrentSession(
            force: Boolean = false,
            callback: ((session: USession, throwable: Throwable?) -> Unit)? = null
        ) {
            refreshSession(USession.activeNow().uuid, force, callback)
        }

        /**
         * Refresh a specific session belonging to [uuid] with the option to [force] refresh and specify a [callback].
         *
         * [callback] receives the refreshed session if the session was refreshed or the current session if the session
         * was unable to be refreshed, as well as the error if a valid session could not be found or an error occurred
         * while refreshing the session.
         */
        @JvmStatic
        @JvmOverloads
        fun refreshSession(
            uuid: UUID,
            force: Boolean = false,
            callback: ((session: USession, throwable: Throwable?) -> Unit)? = null
        ) {
            val mc = UMinecraft.getMinecraft()

            fun error(throwable: Throwable) {
                Essential.logger.error("Failed to refresh session: ${throwable.message}", throwable)
                callback?.invoke(USession.activeNow(), throwable)
            }

            // Check if UUID is in the managed session factory first
            val factory =
                Essential.getInstance().sessionFactories.find { uuid in it.sessions } as? ManagedSessionFactory
            if (factory != null) {
                // If so, then refresh the session
                CompletableFuture.supplyAsync { factory.refresh(factory.sessions[uuid]!!, force) }
                    .whenCompleteAsync({ session, error ->
                        if (session != null) {
                            Essential.logger.info("Successfully refreshed session token.")
                            UMinecraft.getMinecraft().setSession(session)
                            callback?.invoke(session, null)
                        } else {
                            error?.let { error(it) }
                        }
                    }, mc.executor)
            } else {
                // Otherwise, check if it's in the initial session, which we can simply activate
                val initialSession =
                    Essential.getInstance().sessionFactories.find { uuid in it.sessions } as? InitialSessionFactory
                        ?: return error(UnknownAccountException())
                UMinecraft.getMinecraft().setSession(initialSession.sessions[uuid]!!)
                callback?.invoke(initialSession.sessions[uuid]!!, null)
            }
        }
    }

    class RemoveAccountConfirmationModal(manager: ModalManager, name: String) : DangerConfirmationEssentialModal(manager, "Remove", requiresButtonPress = false) {
        init {
            configure {
                contentText = "Are you sure you want to remove the account $name?"
            }
        }
    }

    class UnknownAccountException : Exception("Unknown account")

    data class AccountInfo(val uuid: UUID, val name: String)
}
