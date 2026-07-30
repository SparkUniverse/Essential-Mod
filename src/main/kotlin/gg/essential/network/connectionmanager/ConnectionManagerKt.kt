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

import gg.essential.minecraftauth.exception.AuthenticationException
import gg.essential.minecraftauth.exception.MinecraftAuthenticationException
import gg.essential.minecraftauth.minecraft.session.MinecraftSessionService
import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.packet.Packet
import gg.essential.connectionmanager.common.util.LoginUtil
import gg.essential.data.OnboardingData
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.await
import gg.essential.gui.elementa.state.v2.awaitValue
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.menu.AccountManager
import gg.essential.gui.menu.AccountManager.Companion.refreshCurrentSession
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.Connection.KnownCloseReason
import gg.essential.util.Client
import gg.essential.util.ExponentialBackoff
import gg.essential.util.HostsFileUtil
import gg.essential.util.USession
import gg.essential.util.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.Closeable
import java.time.Instant
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

abstract class ConnectionManagerKt : CMConnection {
    @Suppress("LeakingThis")
    private val java: ConnectionManager = this as ConnectionManager

    override val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Client)

    private val mutableConnectionStatus = mutableStateOf<ConnectionManagerStatus?>(null)
    val connectionStatus: State<ConnectionManagerStatus?> = mutableConnectionStatus

    val outdated: Boolean
        get() = connectionStatus.get() == ConnectionManagerStatus.Outdated

    protected val mutableConnectionUriState = mutableStateOf<String?>(null)
    val connectionUriState: State<String?> = mutableConnectionUriState
    @JvmField
    protected var connection: Connection? = null

    protected abstract val previouslyConnectedProtocol: Int

    override val usingProtocol: Int
        get() = connection?.usingProtocol ?: previouslyConnectedProtocol

    private val disconnectRequests = Channel<CloseReason>(1, BufferOverflow.DROP_LATEST)

    protected abstract fun completeConnection(connection: Connection)
    protected abstract fun onClose()

    fun close(closeReason: CloseReason) {
        disconnectRequests.trySendBlocking(closeReason)
    }

    private val scope = CoroutineScope(SupervisorJob())
    private var connectLoopJob = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.LAZY) { connectLoop() }

    fun start() {
        connectLoopJob.start()
    }

    private suspend fun connectLoop() {
        val mojangBackoff = ExponentialBackoff(4.seconds, 1.minutes, 2.0)
        val connectBackoff = ExponentialBackoff(2.seconds, 1.minutes, 2.0)
        val unexpectedCloseBackoff = ExponentialBackoff(10.seconds, 2.minutes, 2.0)

        var awaitSessionChange = false
        while (true) {
            updateStatus(null)

            if (!OnboardingData.hasAcceptedTos()) {
                updateStatus(ConnectionManagerStatus.TOSNotAccepted)
                LOGGER.info("Waiting for Terms Of Service to be accepted before attempting connection")
                withContext(Dispatchers.Client) {
                    OnboardingData.acceptedTos.awaitValue(true)
                }
                continue
            }

            if (!EssentialConfig.essentialEnabledState.get()) {
                updateStatus(ConnectionManagerStatus.EssentialDisabled)
                LOGGER.info("Waiting for Essential to be re-enabled in its settings before attempting connection")
                withContext(Dispatchers.Client) {
                    EssentialConfig.essentialEnabledState.awaitValue(true)
                }
                continue
            }

            if (HostsFileUtil.containsRulesForMojangServers) {
                updateStatus(ConnectionManagerStatus.Error.HostsFileModified)
                LOGGER.info("Found redirection rules for Mojang servers in hosts file, not attempting to connect")
                return
            }

            if (java.minecraftHook.session == "undefined") {
                // Session token not yet set, refresh session before connecting
                LOGGER.info("Fetching/refreshing initial MC session token")
                val error = suspendCoroutine { continuation ->
                    refreshCurrentSession(false) { _: USession?, throwable ->
                        continuation.resume(throwable)
                    }
                }

                if (error != null) {
                    LOGGER.info("Failed to fetch/refresh initial MC session token, waiting for new user-supplied token.")

                    updateStatus(when (error) {
                        is AccountManager.UnknownAccountException ->
                            // This is not the exception that occurred, but to the UI it's the same problem.
                            ConnectionManagerStatus.Error.AuthenticationFailure(AuthenticationException.InvalidCredentials())

                        else -> ConnectionManagerStatus.Error.AuthenticationFailure(error)
                    })
                    USession.active.await { it.token != "undefined" }
                    continue
                }
            }

            // Ignore any disconnect requests we received while we weren't even connected/connecting
            disconnectRequests.tryReceive()

            val session = USession.activeNow()
            val (uuid, userName, token) = session
            LOGGER.info("Authenticating to Mojang as {} ({})", userName, uuid)
            val sharedSecret = LoginUtil.generateSharedSecret()
            val sessionHash = LoginUtil.computeHash(sharedSecret)

            try {

                withContext(Dispatchers.IO) {
                    MinecraftSessionService.joinServer(token, uuid, sessionHash)
                }

            } catch (joinServerException: AuthenticationException.Ratelimited) {

                val delay = 5.seconds
                LOGGER.warn("Got rate-limit by Mojang, waiting {} before re-trying", delay)
                delay(delay.inWholeMilliseconds)
                continue

            } catch (joinServerException: AuthenticationException.InvalidCredentials) {

                LOGGER.info("Session token appears to be invalid, trying to automatically refresh it")
                val error = withContext(Dispatchers.Client) {
                    suspendCoroutine { continuation ->
                        refreshCurrentSession(true) { _: USession?, throwable ->
                            continuation.resume(throwable)
                        }
                    }
                }
                if (error != null) {
                    LOGGER.warn("User's Minecraft access token has expired, waiting for new user-supplied token.")
                    updateStatus(ConnectionManagerStatus.Error.AuthenticationFailure(error))
                    USession.active.await { it != session }
                }
                continue

            } catch (joinServerException: MinecraftAuthenticationException.InsufficientPrivileges) {

                LOGGER.warn("User is not allowed to join multiplayer sessions, aborting connection attempts.")
                updateStatus(ConnectionManagerStatus.Error.AuthenticationFailure(joinServerException))
                USession.active.await { it != session }
                continue

            } catch (joinServerException: Exception) {

                LOGGER.warn("Got unexpected reply from Mojang:", joinServerException)
                updateStatus(ConnectionManagerStatus.Error.AuthenticationFailure(joinServerException))
                val delay = mojangBackoff.increment()
                if (delay.isPositive()) {
                    LOGGER.info("Waiting {} before re-trying", delay)
                    delay(delay.inWholeMilliseconds)
                }
                continue

            }

            mojangBackoff.reset()

            LOGGER.info("Connecting to Essential Connection Manager...")

            var fastUnexpectedClose = false
            val wrapper = ConnectionWrapper()
            try {
                when (val result = wrapper.connect(uuid, userName, sharedSecret)) {
                    ConnectResult.Outdated -> {
                        LOGGER.error("Client version is no longer supported. Will no longer try to connect.")
                        updateStatus(ConnectionManagerStatus.Outdated)
                        return
                    }
                    is ConnectResult.Failed -> {
                        val delay = connectBackoff.increment()

                        LOGGER.warn("Failed to connect ({}), re-trying in {}", result.info, delay)
                        if (result.info.knownReason == KnownCloseReason.DNS_FAILED) {
                            updateStatus(ConnectionManagerStatus.Error.DNSFailure)
                        } else {
                            updateStatus(ConnectionManagerStatus.Error.GeneralFailure)
                        }

                        delay(delay.inWholeMilliseconds)

                        continue
                    }
                    is ConnectResult.Suspended -> {
                        handleSuspension(result.info)
                        // If the account in use changes, we can try to connect again
                        USession.active.await { it != session }
                        continue
                    }
                    ConnectResult.Connected -> {}
                }
                connectBackoff.reset()

                LOGGER.info("Connected to Essential Connection Manager.")
                val connectedAt = Instant.now()

                try {
                    withContext(Dispatchers.Client) {
                        completeConnection(wrapper.connection)
                    }
                    updateStatus(ConnectionManagerStatus.Success)

                    coroutineScope {
                        launch {
                            for (packet in wrapper.packetChannel) {
                                java.packetHandlers.handle(java, packet)
                            }
                        }

                        select {
                            wrapper.onClose { info ->
                                if (info.knownReason == KnownCloseReason.SUSPENDED) {
                                    handleSuspension(info)
                                    awaitSessionChange = true
                                } else {
                                    val duration = JavaDuration.between(connectedAt, Instant.now()).toKotlinDuration()
                                    fastUnexpectedClose = duration < 2.minutes
                                    LOGGER.warn("Connection closed unexpectedly ({}) after {}", info, duration)
                                }
                            }
                            async { USession.active.await { it.uuid != session.uuid } }.onAwait { newSession ->
                                val duration = JavaDuration.between(connectedAt, Instant.now()).toKotlinDuration()
                                LOGGER.info("Closing connection after {} to change account from {} to {}",
                                    duration, session.username, newSession.username)
                                wrapper.connection.close(CloseReason.REAUTHENTICATION)
                            }
                            disconnectRequests.onReceive { reason ->
                                val duration = JavaDuration.between(connectedAt, Instant.now()).toKotlinDuration()
                                LOGGER.info("Closing connection after {} due to {}", duration, reason)
                                wrapper.connection.close(reason)
                            }
                        }
                        coroutineContext.cancelChildren()
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.Client) {
                        onClose()
                    }
                }
            } finally {
                // TODO replace with `use` once continue-from-inline-function is stable
                try {
                    wrapper.close()
                } catch (_: Throwable) {}
            }

            if (fastUnexpectedClose) {
                val delay = unexpectedCloseBackoff.increment()
                if (delay.isPositive()) {
                    LOGGER.info("Waiting {} before re-connecting", delay)
                    updateStatus(ConnectionManagerStatus.Error.GeneralFailure)
                    delay(delay.inWholeMilliseconds)
                }
            } else {
                unexpectedCloseBackoff.reset()
            }

            if (awaitSessionChange) {
                USession.active.await { it != session }
                awaitSessionChange = false
            }
        }
    }

    private suspend fun updateStatus(status: ConnectionManagerStatus?) {
        withContext(Dispatchers.Client) {
            mutableConnectionStatus.set(status)
        }
    }

    private suspend fun handleSuspension(result: CloseInfo) {
        LOGGER.error("User is permanently suspended. Will no longer try to connect for this session.")
        updateStatus(ConnectionManagerStatus.UserSuspended)
        withContext(Dispatchers.Client) {
            java.suspensionManager.setPermanentlySuspended(result.reason)
        }
    }

    private sealed interface ConnectResult {
        object Connected : ConnectResult
        object Outdated : ConnectResult
        data class Failed(val info: CloseInfo) : ConnectResult
        data class Suspended(val info: CloseInfo) : ConnectResult
    }

    private class ConnectionWrapper : Connection.Callbacks, Closeable {
        val connection = Connection(this)

        private val openChannel = Channel<Unit>(Channel.CONFLATED)

        val packetChannel = Channel<Packet>()

        private val closeChannel = Channel<CloseInfo>(1, BufferOverflow.DROP_LATEST)
        val onClose: SelectClause1<CloseInfo>
            get() = closeChannel.onReceive

        override fun onOpen() {
            openChannel.trySendBlocking(Unit)
        }

        override fun onPacketAsync(packet: Packet) {
            packetChannel.trySendBlocking(packet)
        }

        override fun onClose(info: CloseInfo) {
            closeChannel.trySendBlocking(info)
        }

        suspend fun connect(uuid: UUID, userName: String, sharedSecret: ByteArray): ConnectResult {
            withContext(Dispatchers.IO) {
                connection.setupAndConnect(uuid, userName, sharedSecret)
            }
            return select {
                openChannel.onReceive { ConnectResult.Connected }
                closeChannel.onReceive { info ->
                    when (info.knownReason) {
                        KnownCloseReason.OUTDATED -> ConnectResult.Outdated
                        KnownCloseReason.SUSPENDED -> ConnectResult.Suspended(info)
                        KnownCloseReason.DNS_FAILED,
                        null -> ConnectResult.Failed(info)
                    }
                }
            }
        }

        override fun close() {
            connection.close()
        }
    }

    data class CloseInfo(
        val code: Int,
        val reason: String,
        val remote: Boolean,
        val knownReason: KnownCloseReason?,
    )

    companion object {
        val LOGGER: Logger = LogManager.getLogger("Essential - Connection")
    }
}
