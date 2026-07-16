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
package gg.essential.network.connectionmanager.knownservers

import gg.essential.connectionmanager.common.model.knownserver.KnownServer
import gg.essential.connectionmanager.common.packet.knownservers.ClientKnownServersRequestPacket
import gg.essential.connectionmanager.common.packet.knownservers.ServerKnownServersResponsePacket
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.NetworkedManager
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.map

class KnownServersManager(val connectionManager: CMConnection) : NetworkedManager, KnownServers {
    private val mutableState = mutableStateOf(KnownServersImpl(emptyList()))
    val state: State<KnownServers> = mutableState

    override fun onConnected() {
        connectionManager.connectionScope.launch { refreshKnownServers() }
    }

    private suspend fun refreshKnownServers() {
        val response =
            connectionManager.call(ClientKnownServersRequestPacket())
                .exponentialBackoff()
                .await<ServerKnownServersResponsePacket>()
        mutableState.set(KnownServersImpl(response.knownServers))
    }

    override fun findServerByAddress(address: String): KnownServer? = state.getUntracked().findServerByAddress(address)

    override fun normalizeAddress(address: String): String = state.getUntracked().normalizeAddress(address)

    private data class KnownServersImpl(val servers: List<KnownServer>) : KnownServers {
        private val serversByAddress = servers.flatMap { server -> server.addresses.filter { !isRegex(it) }.map { it to server } }.toMap()
        private val serversByRegex = servers.flatMap { server -> server.addresses.filter { isRegex(it) }.map { Pattern.compile(it) to server } }.toMap()

        override fun findServerByAddress(address: String): KnownServer? {
            serversByAddress[address]?.let { return it }

            for ((pattern, server) in serversByRegex) {
                if (pattern.matcher(address).matches()) {
                    return server
                }
            }

            return null
        }

        override fun normalizeAddress(address: String): String {
            findServerByAddress(address)?.let { return it.addresses[0] }
            return address
        }
    }

    companion object {
        private fun isRegex(address: String) = address.startsWith("^") && address.endsWith("$")
    }
}
