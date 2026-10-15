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
package gg.essential.network

import gg.essential.connectionmanager.common.packet.Packet
import gg.essential.network.connectionmanager.handler.PacketHandler
import kotlinx.coroutines.CoroutineScope
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

interface CMConnection {
    /** A main-thread supervisor [CoroutineScope] which has all its children cancelled on disconnect. */
    val connectionScope: CoroutineScope

    val isOpen: Boolean

    val usingProtocol: Int

    fun registerOnConnected(onConnected: () -> Unit)

    fun <T : Packet> registerPacketHandler(cls: Class<T>, handler: PacketHandler<T>)

    fun call(packet: Packet): Call = Call(this, packet)

    @Deprecated("Use `call` instead.", ReplaceWith("call(packet).await()"))
    fun send(packet: Packet, callback: Consumer<Optional<Packet>>?, timeoutUnit: TimeUnit?, timeoutValue: Long?)
    @Deprecated("Use `call` instead.", ReplaceWith("call(packet).await()"))
    fun send(packet: Packet, callback: Consumer<Optional<Packet>>?) = send(packet, callback, TimeUnit.SECONDS, 10)

    fun getGatewayService(id: String): GatewayService
}

inline fun <reified T : Packet> CMConnection.registerPacketHandler(noinline handler: (T) -> Unit) =
    registerPacketHandler(T::class.java, handler)
