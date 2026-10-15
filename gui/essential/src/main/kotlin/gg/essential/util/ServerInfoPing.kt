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

import com.google.common.collect.MapMaker
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.essential.connectionmanager.common.packet.pingproxy.ClientPingProxyPacket
import gg.essential.connectionmanager.common.packet.pingproxy.ServerPingProxyResponsePacket
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.Observer
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO

data class ServerPingInfo(
    val host: String,
    val port: Int,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val version: String,
    val description: JsonElement,
    val iconBytes: Bytes?,
) {
    /** Name of the world. Assumes description to be formatted like vanilla. `null` if parsing fails. */
    val worldName: String?
        get() = try {
            if (description is JsonObject) {
                description.get("text").asString
            } else {
                description.asString
            }.split(" - ").drop(1).joinToString(" - ")
        } catch (exception: Exception) {
            LOGGER.warn("Failed to parse world name of `$host:$port`", exception)
            null
        }

    val iconImage: BufferedImage? by lazy {
        try {
            iconBytes?.let { ImageIO.read(it.array.inputStream()) }
        } catch (exception: Exception) {
            LOGGER.warn("Failed to decode icon of `$host:$port`", exception)
            null
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ServerPingInfo::class.java)

        suspend fun fetchViaPingProxy(address: String): ServerPingInfo? {
            val (host, port) = platform.splitHostAndPort(address)
            return fetchViaPingProxy(host, port)
        }

        suspend fun fetchViaPingProxy(host: String, port: Int): ServerPingInfo? {
            try {
                val request = ClientPingProxyPacket(host, port, platform.mcProtocolVersion)
                val response = platform.cmConnection.call(request).await<ServerPingProxyResponsePacket>()
                    ?: return null

                val json = JsonParser().parse(response.rawJson).asJsonObject

                val players = json.get("players").asJsonObject
                val onlinePlayers = players.get("online").asInt
                val maxPlayers = players.get("max").asInt
                val version = json.get("version").asJsonObject.get("name").asString
                val description = json.get("description")
                val icon = json.get("favicon")?.asString?.let { iconStr ->
                    val prefix = "data:image/png;base64,"
                    if (iconStr.startsWith(prefix)) {
                        val base64 = iconStr.substring(prefix.length)
                            // older versions have the base64 string split over multiple lines
                            .replace("\n", "")
                        Bytes(Base64.getDecoder().decode(base64))
                    } else {
                        LOGGER.warn("Don't know how to decode icon from `$host:$port`: `$iconStr`")
                        null
                    }
                }
                return ServerPingInfo(host, port, onlinePlayers, maxPlayers, version, description, icon)
            } catch (exception: Exception) {
                LOGGER.warn("Failed to fetch server ping info from `$host:$port", exception)
                return null
            }
        }
    }
}

class Bytes(val array: ByteArray) {
    override fun equals(other: Any?): Boolean = array.contentEquals((other as? Bytes)?.array)
    override fun hashCode(): Int = array.contentHashCode()
}

class ServerPingInfoState(private val address: String) : State<ServerPingInfo?> {

    private val state: MutableState<ServerPingInfo?> = mutableStateOf(null)
    private var lastUpdate: Long = -1L
    private var interested = 0
    private var fetchJob: Job? = null

    override fun Observer.get(): ServerPingInfo? {
        return state()
    }

    suspend fun refresh(refreshIntervalMillis: Long = 5000L) {
        if (lastUpdate + refreshIntervalMillis > System.currentTimeMillis()) {
            return
        }
        try {
            if (interested++ == 0) {
                fetchJob = coroutineScope.launch {
                    state.set(ServerPingInfo.fetchViaPingProxy(address))
                    lastUpdate = System.currentTimeMillis()
                }
            }
            fetchJob?.join()
        } finally {
            interested--
            if (interested == 0) {
                fetchJob?.cancel()
            }
        }
    }

    companion object {
        private val servers = MapMaker().weakValues().makeMap<String, ServerPingInfoState>()
        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Client)

        fun forServer(address: String): ServerPingInfoState {
            return servers.computeIfAbsent(address) { ServerPingInfoState(it) }!!
        }
    }

}