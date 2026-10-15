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
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket
import gg.essential.util.ExponentialBackoff
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Call(
    val connection: CMConnection,
    val packet: Packet,
) {
    private var timeout: Duration = 10.seconds
    private var ignoreUnexpectedPackets: Boolean = false

    fun timeout(duration: Duration = timeout) = apply {
        this.timeout = duration
    }

    fun ignoreUnexpected() = apply {
        ignoreUnexpectedPackets = true
    }

    fun exponentialBackoff(start: Duration = 2.seconds, max: Duration = 60.seconds, factor: Double = 2.0): CallWithRetry {
        return CallWithRetry(this, ExponentialBackoff(start, max, factor))
    }

    fun fireAndForget() {
        @Suppress("DEPRECATION")
        connection.send(packet, null, TimeUnit.MILLISECONDS, timeout.inWholeMilliseconds)
    }

    suspend fun awaitResponseActionPacket(): Boolean {
        return await<ResponseActionPacket>()?.isSuccessful == true
    }

    suspend fun awaitSuccessOrThrow() {
        val response = awaitOrThrow<ResponseActionPacket>()
        if (!response.isSuccessful) {
            throw UnsuccessfulResponseException(packet, response.errorMessage)
        }
    }

    suspend inline fun <reified T : Packet> await(): T? {
        return awaitOneOf(T::class.java) as T?
    }

    suspend inline fun <reified T : Packet> awaitOrThrow(): T {
        return await<T>() ?: throw NoResponseException(packet)
    }

    suspend fun awaitOneOf(vararg classes: Class<out Packet>): Packet? {
        return suspendCancellableCoroutine { cont ->
            @Suppress("DEPRECATION")
            connection.send(packet, { maybeResponse ->
                val response = maybeResponse.orElse(null)
                when {
                    response == null -> cont.resume(null)
                    classes.any { it.isInstance(response) } -> cont.resume(response)
                    else -> {
                        if (ignoreUnexpectedPackets) {
                            LOGGER.error("Got unexpected reply $response for $packet")
                            cont.resume(null)
                        } else {
                            cont.resumeWithException(UnexpectedResponseException(packet, response))
                        }
                    }
                }
            }, TimeUnit.MILLISECONDS, timeout.inWholeMilliseconds)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Call::class.java)
    }
}

class CallWithRetry(
    private val base: Call,
    private val backoff: ExponentialBackoff,
) {
    suspend inline fun <reified T : Packet> await(): T {
        return awaitOneOf(T::class.java) as T
    }

    suspend fun awaitOneOf(vararg classes: Class<out Packet>): Packet {
        while (true) {
            val response = base.awaitOneOf(*classes)
            if (response != null) {
                return response
            }
            delay(backoff.increment())
        }
    }
}

class NoResponseException(val request: Packet) : Exception("No response for $request")
class UnexpectedResponseException(val request: Packet, val response: Packet) : Exception("Unexpected response $response for $request")
class UnsuccessfulResponseException(val request: Packet, val errorMessage: String?) : Exception("Unsuccessful response for $request: ${errorMessage ?: "no error message"}")
