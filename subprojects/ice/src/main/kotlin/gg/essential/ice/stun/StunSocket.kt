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
package gg.essential.ice.stun

import gg.essential.ice.DatagramPacket
import gg.essential.ice.toBase64String
import gg.essential.ice.toHexString
import gg.essential.slf4j.withKeyValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.yield
import org.slf4j.Logger
import java.io.IOException
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketException
import java.security.MessageDigest
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * This class extends a local DatagramSocket with [STUN](https://www.rfc-editor.org/rfc/rfc8489) and
 * [TURN](https://www.rfc-editor.org/rfc/rfc8656) functionality.
 */
class StunSocket(
    parentLogger: Logger,
    parentScope: CoroutineScope, // must have Job and a concurrency-limited "main" dispatcher
    private val manager: StunManager,
    private val hostSocket: DatagramSocket,
    val hostAddress: InetSocketAddress,
) {
    val scope = parentScope + Job(parentScope.coroutineContext.job)
    val logger = parentLogger.withKeyValue("hostAddress", hostAddress)

    // Intentionally not connecting this Job to its parent so we have some extra time with our socket to properly
    // release any TURN allocations.
    private val hostSocketScope = CoroutineScope(scope.coroutineContext + Job())
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED /* want ATOMIC but that's still experimental */) {
            try {
                delay(Duration.INFINITE)
            } finally {
                hostSocketScope.launch {
                    // TODO could be improved by quitting as soon as all allocations were successfully released, instead
                    //      of always waiting the full minute; but not like that's necessary while we only have a sane
                    //      number of these.
                    delay(1.minutes)
                    hostSocketScope.cancel()
                }
            }
        }
    }

    private val hostSendChannel: Channel<Pair<DatagramPacket, CompletableDeferred<Boolean>?>> =
        Channel(1000, BufferOverflow.DROP_OLDEST) { (packet, deferred) ->
            logger.warn("Failed to send packet of {} bytes to {}: hostSendChannel overflow", packet.length, packet.address)
            // On  overflow, we resolve the deferred as successful because overflow is not unrecoverable (just re-try)
            deferred?.complete(true)
        }

    private val endpoints = mutableMapOf<InetSocketAddress, Endpoint>()
    private val stunBindings = mutableMapOf<InetSocketAddress, StunBinding>()
    private val relayAllocations = mutableMapOf<InetSocketAddress, RelayAllocation>()

    init {
        hostSocketScope.launch(Dispatchers.IO, CoroutineStart.UNDISPATCHED) {
            // We want CoroutineStart.ATOMIC so our finally is guaranteed, but that's still experimental, so we'll
            // instead use UNDISPATCHED and yield as soon as we're inside our try-finally.
            hostSocket.use { socket ->
                yield()
                val sha256 = MessageDigest.getInstance("SHA-256")
                val knownUnreachable = mutableSetOf<InetAddress>()
                for ((packet, deferred) in hostSendChannel) {
                    if (packet.address in knownUnreachable) {
                        deferred?.complete(false)
                        continue // don't even bother trying
                    }
                    try {
                        if (LOG_UDP_PACKET_CONTENT) {
                            val bytes = packet.data.maybeSliceArray(packet.offset, packet.length)
                            val checksum = sha256.digest(bytes).toBase64String()
                            val content = bytes.toBase64String()
                            logger.atTrace()
                                .addKeyValue("hostAddress", hostAddress)
                                .addKeyValue("remoteAddress", packet.socketAddress)
                                .log("Sending packet of {} bytes with checksum {}: {}", bytes.size, checksum, content)
                        }
                        socket.send(packet)
                    } catch (e: Exception) {
                        if (e is SocketException && e.message?.startsWith("Network is unreachable:") == true
                            || e is BindException && e.message == "Cannot assign requested address: no further information"
                            || e is IOException && e.message == "Network is unreachable (sendto failed)"
                            || e is IOException && e.message == "No route to host (sendto failed)"
                            || e is NoRouteToHostException) {
                            logger.trace("Failed to send to {}: {}", packet.socketAddress, e.message)
                            knownUnreachable.add(packet.address)
                            deferred?.complete(false)
                            continue
                        }
                        logger.error("Failed to send $packet to ${packet.socketAddress}", e)
                    } finally {
                        deferred?.complete(true)
                    }
                }
            }
        }

        val packetsToBeSorted = Channel<ReceivedPacket>(10)
        hostSocketScope.launch(Dispatchers.IO) {
            val sha256 = MessageDigest.getInstance("SHA-256")
            val buf = DatagramPacket(ByteArray(1500), 1500)
            while (coroutineContext.isActive) {
                try {
                    hostSocket.receive(buf)
                } catch (e: Exception) {
                    if (e is SocketException && e.message?.startsWith("Network dropped connection on reset") == true) {
                        logger.trace("Ignoring nonsensical exception:", e)
                        continue
                    }
                    if (!hostSocket.isClosed) {
                        logger.error("Failed to receive:", e)
                    }
                    break
                }
                val remoteAddress = InetSocketAddress(buf.address, buf.port)
                val bytes = buf.data.sliceArray(buf.offset until buf.offset + buf.length)
                if (LOG_UDP_PACKET_CONTENT) {
                    val checksum = sha256.digest(bytes).toBase64String()
                    val content = bytes.toBase64String()
                    logger.atTrace()
                        .addKeyValue("hostAddress", hostAddress)
                        .addKeyValue("remoteAddress", remoteAddress)
                        .log("Received packet of {} bytes with checksum {}: {}", bytes.size, checksum, content)
                }
                packetsToBeSorted.send(
                    ReceivedPacket(
                        this@StunSocket,
                        null,
                        TimeSource.Monotonic.markNow(),
                        remoteAddress,
                        bytes,
                    )
                )
            }
            packetsToBeSorted.close()
        }

        hostSocketScope.launch {
            for (packet in packetsToBeSorted) {
                if (StunMessage.looksLikeStun(packet.data)) {
                    val message = manager.messageReceived(logger, packet) ?: continue
                    val relay = relayAllocations[packet.source]
                    if (relay != null) {
                        relay.stunMessageReceived(packet, message)
                    } else {
                        logger.atWarn()
                            .addKeyValue("remoteAddress", packet.source)
                            .addKeyValue("tId", message.transactionId)
                            .log("Got unexpected STUN message: {}", message)
                    }
                } else {
                    val relay = relayAllocations[packet.source]
                    if (relay != null) {
                        relay.dataPacketReceived(packet)
                    } else {
                        val server = manager.getServer(packet.source)
                        if (server != null) {
                            server.dataReceiveChannel.send(packet)
                        } else {
                            logger.atWarn()
                                .addKeyValue("remoteAddress", packet.source)
                                .log("Got unexpected data packet of {} bytes", packet.data.size)
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends the given packet via this socket.
     *
     * On success (just means the sending succeeded, not that it arrived, it's still UDP) returns `true`,
     * otherwise (i.e. on unrecoverable failure, e.g. no route to host) returns `false`.
     *
     * Use [sendUnchecked] if you don't care and don't want to wait.
     */
    suspend fun send(packet: DatagramPacket): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        hostSendChannel.send(packet to deferred)
        return deferred.await()
    }

    fun sendUnchecked(packet: DatagramPacket) {
        hostSendChannel.trySend(packet to null)
    }

    fun getEndpoint(address: InetSocketAddress): Endpoint {
        return endpoints.getOrPut(address) { Endpoint(address) }
    }

    fun allocateBinding(stunServer: InetSocketAddress): StunBinding {
        check(stunServer !in stunBindings)

        val endpoint = getEndpoint(stunServer)

        return StunBinding(endpoint).also { stunBindings[stunServer] = it }
    }

    fun allocateRelay(turnServer: InetSocketAddress): RelayAllocation {
        check(turnServer !in relayAllocations)
        
        val endpoint = getEndpoint(turnServer)

        return RelayAllocation(endpoint).also {
            endpoint.relayAllocation = it
            relayAllocations[turnServer] = it
        }
    }

    class ReceivedPacket(
        val socket: StunSocket,
        val relay: RelayAllocation?,
        val timestamp: ComparableTimeMark,
        val source: InetSocketAddress,
        val data: ByteArray,
    )

    inner class Endpoint(val address: InetSocketAddress) {
        val logger = this@StunSocket.logger.withKeyValue("remoteAddress", address)

        internal var relayAllocation: RelayAllocation? = null

        suspend fun request(message: StunMessage) = coroutineScope {
            val request = StunRequest(logger, this, ::send, address, message)
            manager.sendRequest(request)
            request.await()
        }
        
        suspend fun request(type: StunType, vararg attrs: StunAttribute) =
            request(StunMessage(type, StunClass.Request, TransactionId.create(), attrs.toList()))
    }

    inner class StunBinding(private val endpoint: Endpoint) {
        val logger = this@StunSocket.logger.withKeyValue("remoteAddress", endpoint.address)
        val scope = this@StunSocket.scope.let { it + Job(it.coroutineContext.job) }

        val mappedAddress = CompletableDeferred<InetSocketAddress>(scope.coroutineContext.job)

        init {
            scope.launch {
                try {
                    bindAndHold()
                } finally {
                    scope.cancel()
                }
            }
        }

        private suspend fun bindAndHold() {
            val initialResponse = endpoint.request(StunType.Binding)?.message
            if (initialResponse == null) {
                logger.warn("Failed to contact STUN server, timed out.")
                return
            }
            if (initialResponse.cls != StunClass.ResponseSuccess) {
                val err = initialResponse.attribute<StunAttribute.ErrorCode>()
                logger.warn("Failed to contact STUN server: {} {}", err?.code, err?.message)
                return
            }

            val mappedAddress = initialResponse.attribute<StunAttribute.XorMappedAddress>()?.address
            if (mappedAddress == null) {
                logger.warn("STUN server did not return XOR-MAPPED-ADDRESS?")
                return
            }
            logger.debug("Received public address from STUN server: {}", mappedAddress)
            this.mappedAddress.complete(mappedAddress)

            while (true) {
                delay(10.seconds)

                val refreshResponse = endpoint.request(StunType.Binding)?.message
                if (refreshResponse == null) {
                    logger.warn("Failed to refresh binding, timed out.")
                    break
                }
                if (refreshResponse.cls != StunClass.ResponseSuccess) {
                    val err = refreshResponse.attribute<StunAttribute.ErrorCode>()
                    logger.warn("Failed to refresh binding: {} {}", err?.code, err?.message)
                    break
                }
            }
        }
    }

    inner class RelayAllocation(private val endpoint: Endpoint) {
        val logger = this@StunSocket.logger.withKeyValue("turnServer", endpoint.address)
        val scope = this@StunSocket.scope.let { it + Job(it.coroutineContext.job) }

        val sendChannel = Channel<DatagramPacket>(100, BufferOverflow.DROP_OLDEST)

        val mappedAddress = CompletableDeferred<InetSocketAddress>(scope.coroutineContext.job)
        val relayedAddress = CompletableDeferred<InetSocketAddress>(scope.coroutineContext.job)

        private val channelAddressToId = mutableMapOf<InetSocketAddress, UShort>()
        private val channelIdToAddress = mutableMapOf<UShort, InetSocketAddress>()
        private val boundChannels = mutableSetOf<UShort>() // confirmed by the turn server

        init {
            scope.launch {
                try {
                    allocateAndHold()
                } finally {
                    hostSocketScope.launch {
                        release()
                    }
                    scope.cancel()
                }
            }

            scope.launch {
                for (packet in sendChannel) {
                    sendData(packet)
                }
            }
        }

        private suspend fun allocateAndHold() {
            val allocResponse = endpoint.request(StunType.Allocate, StunAttribute.RequestedTransport)?.message
            if (allocResponse == null) {
                logger.warn("Failed to contact TURN server, timed out.")
                return
            }
            if (allocResponse.cls != StunClass.ResponseSuccess) {
                val err = allocResponse.attribute<StunAttribute.ErrorCode>()
                logger.warn("Failed to allocate relay: {} {}", err?.code, err?.message)
                return
            }

            val mappedAddress = allocResponse.attribute<StunAttribute.XorMappedAddress>()?.address
            if (mappedAddress == null) {
                logger.warn("Allocate response is missing XOR-MAPPED-ADDRESS? {}", allocResponse)
                return
            }
            logger.debug("Received public address: {}", mappedAddress)
            this@RelayAllocation.mappedAddress.complete(mappedAddress)

            val relayedAddress = allocResponse.attribute<StunAttribute.XorRelayedAddress>()?.address
            if (relayedAddress == null) {
                logger.warn("Allocate response is missing XOR-RELAYED-ADDRESS? {}", allocResponse)
                return
            }
            logger.debug("Received relayed address: {}", relayedAddress)
            this@RelayAllocation.relayedAddress.complete(relayedAddress)

            while (true) {
                // In theory we could use the lifetime the Allocate response contains, but in practice NATs between us
                // and the TURN server have rather short (potentially less than 30s) timeouts on their bindings, so we
                // need to send something to refresh those quite frequently anyway, so we'll use this request to do so.
                delay(10.seconds)

                val refreshResponse = endpoint.request(StunType.Refresh)?.message
                if (refreshResponse == null) {
                    logger.warn("Failed to refresh allocation, timed out.")
                    break
                }
                if (refreshResponse.cls != StunClass.ResponseSuccess) {
                    val err = refreshResponse.attribute<StunAttribute.ErrorCode>()
                    logger.warn("Failed to refresh allocation: {} {}", err?.code, err?.message)
                    break
                }
            }
        }

        private suspend fun release() {
            val response = endpoint.request(StunType.Refresh, StunAttribute.Lifetime(0u))?.message
            if (response == null) {
                logger.warn("Failed to release TURN allocation, timed out.")
                return
            }
            val err = response.attribute<StunAttribute.ErrorCode>()
            if (response.cls == StunClass.ResponseSuccess || err?.code == 437) {
                logger.trace("TURN allocation successfully released.")
            } else {
                logger.warn("Failed to release TURN allocation: {} {}", err?.code, err?.message)
            }
        }
        
        fun createChannel(address: InetSocketAddress) {
            if (address in channelAddressToId) return
            
            val channelId = (0x4000 + channelAddressToId.size).toUShort()
            channelAddressToId[address] = channelId
            channelIdToAddress[channelId] = address
            
            scope.launch {
                if (!bindChannel(channelId, address)) {
                    return@launch
                }
                boundChannels.add(channelId)
                while (true) {
                    // Channels hold 10 minutes, however permissions only hold 5, so we need to refresh more frequently
                    delay(4.minutes)
                    if (!bindChannel(channelId, address)) {
                        return@launch
                    }
                }
            }
        }
        
        private suspend fun bindChannel(channelId: UShort, address: InetSocketAddress): Boolean {
            val response = endpoint.request(
                StunType.ChannelBind,
                StunAttribute.ChannelNumber(channelId),
                StunAttribute.XorPeerAddress(address),
            )?.message
            if (response == null) {
                logger.error("Failed to bind channel, timed out.")
                return false
            }
            if (response.cls != StunClass.ResponseSuccess) {
                val err = response.attribute<StunAttribute.ErrorCode>()
                logger.error("Failed to bind channel: {} {}", err?.code, err?.message)
                return false
            }
            return true
        }

        private var lastCreatePermissionRequest: Job? = null
        private var previouslyRequestedPermissions: Set<InetAddress> = emptySet()
        fun createPermission(vararg peerAddresses: InetAddress, immediately: Boolean = false) {
            val nowRequested = peerAddresses.toSet()
            if (previouslyRequestedPermissions.containsAll(nowRequested)) {
                return
            }

            val allRequested = previouslyRequestedPermissions + nowRequested

            // If this method was called right before a data packet is sent, then we start UNDISPATCHED so the
            // CreatePermission packet is sent (and ideally arrives) before that data packet, cause otherwise that
            // packet would just be dropped by the TURN server and we'd only get through on the next re-transmit.
            // If we aren't in a hurry, we use regular start semantics (meaning it'll only start once the current
            // coroutine suspends), so we can potentially queue multiple permission requests at once before sending
            // the actual packet.
            val start = if (immediately) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT

            lastCreatePermissionRequest?.cancel()
            lastCreatePermissionRequest = scope.launch(start = start) {
                previouslyRequestedPermissions = allRequested
                val attrs = allRequested
                    .map { StunAttribute.XorPeerAddress(InetSocketAddress(it, 0)) }
                    .toTypedArray()
                val response = endpoint.request(StunType.CreatePermission, *attrs)?.message
                if (response == null) {
                    logger.warn("Failed to install permission, timed out.")
                    return@launch
                }
                if (response.cls != StunClass.ResponseSuccess) {
                    val err = response.attribute<StunAttribute.ErrorCode>()
                    logger.warn("Failed to install permission: {} {}", err?.code, err?.message)
                    return@launch
                }
                // Nothing else to do here. We don't wait for the response to send our data, we send that out asap (and
                // let retransmissions deal with it if they don't get through).
            }
            
            // Note: Permissions ordinarily only last for five minutes, but we'll always switch to Channels before that
            //       time elapses, so we don't need to bother refreshing them.
        }

        private fun sendData(packet: DatagramPacket) {
            val destination = InetSocketAddress(packet.address, packet.port)

            val channelId = channelAddressToId[packet.socketAddress]
            if (channelId != null && channelId in boundChannels) {
                val data = ChannelData.encode(channelId, packet.data.maybeSliceArray(packet.offset, packet.length))
                this@StunSocket.sendUnchecked(DatagramPacket(data, endpoint.address))
                return
            }

            createPermission(destination.address, immediately = true)

            val msg = StunMessage(StunType.Send, StunClass.Indication, TransactionId.create(), listOf(
                StunAttribute.XorPeerAddress(destination),
                StunAttribute.Data(packet.data.maybeSliceArray(packet.offset, packet.length)),
            ))
            this@StunSocket.sendUnchecked(DatagramPacket(msg.encode(), endpoint.address))
        }

        internal suspend fun dataPacketReceived(packet: ReceivedPacket): Boolean {
            val channelData = ChannelData.tryDecode(packet.data)
            if (channelData == null) {
                logger.warn("Got unexpected packet: {}", packet.data.toHexString())
                return false
            }

            val peerAddress = channelIdToAddress[channelData.channelId]
            if (peerAddress == null) {
                logger.warn("Got ChannelData for unknown channel ${channelData.channelId}.")
                return true
            }
            
            relayedPacketReceived(ReceivedPacket(this@StunSocket, this, packet.timestamp, peerAddress, channelData.data))
            return true
        }

        internal suspend fun stunMessageReceived(packet: ReceivedPacket, message: StunMessage) {
            val logger = logger.withKeyValue("tId", message.transactionId)

            when (message.cls) {
                StunClass.Indication -> when (message.type) {
                    StunType.Data -> stunDataIndicationReceived(logger, packet, message)
                    else -> logger.warn("Got unexpected STUN indication: {}", message)
                }
                StunClass.Request -> logger.warn("Got unexpected STUN request: {}", message)
                else -> throw AssertionError("Should have been handled by StunAgent.")
            }
        }

        private suspend fun stunDataIndicationReceived(logger: Logger, packet: ReceivedPacket, message: StunMessage) {
            val peerAddress = message.attribute<StunAttribute.XorPeerAddress>()?.address
            val data = message.attribute<StunAttribute.Data>()
            if (peerAddress == null) {
                logger.warn("STUN Data indication is missing XOR-PEER-ADDRESS attribute: {}", message)
                return
            }
            if (data == null) {
                // Expected for ICMP packets, which we do not currently care about
                logger.trace("STUN Data indication without DATA attribute: {}", message)
                return
            }

            logger.trace("Received STUN Data indication from peer {} with {} bytes of data.", peerAddress, data.bytes.size)

            relayedPacketReceived(ReceivedPacket(this@StunSocket, this, packet.timestamp, peerAddress, data.bytes))
        }

        private suspend fun relayedPacketReceived(packet: ReceivedPacket) {
            if (StunMessage.looksLikeStun(packet.data)) {
                val message = manager.messageReceived(logger, packet) ?: return
                logger.atWarn()
                    .addKeyValue("remoteAddress", packet.source)
                    .addKeyValue("tId", message.transactionId)
                    .log("Got unexpected relayed STUN message: {}", message)
            } else {
                val server = manager.getServer(packet.source)
                if (server != null) {
                    server.dataReceiveChannel.send(packet)
                } else {
                    logger.atWarn()
                        .addKeyValue("remoteAddress", packet.source)
                        .log("Got unexpected relayed data packet of {} bytes", packet.data.size)
                }
            }
        }
    }
    
    companion object {
        private val LOG_UDP_PACKET_CONTENT = System.getProperty("essential.sps.log_udp_packet_content").toBoolean()

        private fun ByteArray.maybeSliceArray(offset: Int, length: Int) =
            if (offset == 0 && length == size) this else sliceArray(offset until offset + length)
    }
}
