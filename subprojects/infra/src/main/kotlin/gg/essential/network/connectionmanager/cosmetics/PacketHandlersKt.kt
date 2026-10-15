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
package gg.essential.network.connectionmanager.cosmetics

import gg.essential.connectionmanager.common.packet.Packet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

abstract class PacketHandlersKt(
    val coroutineScope: CoroutineScope,
) {
    private val awaitingPacketResponses: MutableMap<UUID, Pair<Consumer<Optional<Packet>>, Job>> = ConcurrentHashMap()

    fun register(packetId: UUID, timeoutUnit: TimeUnit, timeoutValue: Long, responseCallback: Consumer<Optional<Packet>>) {
        val expiryJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            delay(timeoutValue.toDuration(timeoutUnit.toDurationUnit()))
            if (awaitingPacketResponses.remove(packetId) != null) {
                responseCallback.accept(Optional.empty())
            }
        }

        awaitingPacketResponses[packetId] = Pair(responseCallback, expiryJob)

        expiryJob.start()
    }

    protected fun takePacketResponseHandler(uuid: UUID): Consumer<Optional<Packet>>? {
        val (handler, expiryJob) = awaitingPacketResponses.remove(uuid) ?: return null
        expiryJob.cancel()
        return handler
    }
}
