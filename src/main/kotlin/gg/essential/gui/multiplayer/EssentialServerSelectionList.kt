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
package gg.essential.gui.multiplayer

import gg.essential.Essential
import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.enums.ActivityType
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.mixins.ext.client.gui.ext
import gg.essential.mixins.ext.client.multiplayer.ext
import gg.essential.mixins.ext.client.multiplayer.isTrusted
import gg.essential.mixins.ext.client.multiplayer.recommendedVersion
import gg.essential.mixins.ext.client.multiplayer.showDownloadIcon
import gg.essential.mixins.transformers.client.gui.ServerListEntryNormalAccessor
import gg.essential.mixins.transformers.client.gui.ServerSelectionListAccessor
import gg.essential.connectionmanager.common.model.serverdiscovery.Server
import gg.essential.mixins.ext.client.gui.SelectionListWithDividers
import gg.essential.mixins.ext.client.gui.essential
import gg.essential.mixins.ext.client.gui.setImpressionConsumer
import gg.essential.sps.SpsAddress
import gg.essential.universal.UMinecraft
import gg.essential.util.MinecraftUtils
import gg.essential.util.UUIDUtil
import gg.essential.util.executor
import gg.essential.util.toServerData
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.ServerListEntryNormal
import net.minecraft.client.gui.ServerSelectionList
import net.minecraft.client.multiplayer.ServerData
import java.util.*

//#if MC>=11600
//$$ import gg.essential.data.OnboardingData
//#endif

class EssentialServerSelectionList(
    private val owner: GuiMultiplayer,
    private val serverSelectionList: ServerSelectionList
) {
    private val connectionManager = Essential.getInstance().connectionManager
    private val profileManager = connectionManager.profileManager
    private val spsManager = connectionManager.spsManager
    private val serverDiscoveryManager = connectionManager.serverDiscoveryManager

    private val serverListInternet
        get() = (serverSelectionList as ServerSelectionListAccessor).serverListInternet
    private val serverListLan
        get() = (serverSelectionList as ServerSelectionListAccessor).serverListLan

    private fun getKnownServers() = (0 until owner.serverList.countServers()).associate {
        val data = owner.serverList.getServerData(it)
        connectionManager.knownServersManager.normalizeAddress(data.serverIP) to data
    }

    fun isFavorite(serverData: ServerData): Boolean =
        getKnownServers().containsKey(
            connectionManager.knownServersManager.normalizeAddress(serverData.serverIP)
        )

    fun addFavorite(serverData: ServerData) {
        sendFavoriteTelemetryPacket(serverData)

        //#if MC>=11900
        //$$ owner.serverList.add(serverData, false)
        //#else
        owner.serverList.addServerData(serverData)
        //#endif
        owner.serverList.saveServerList()
    }

    private fun sendFavoriteTelemetryPacket(serverData: ServerData) {
        val id = connectionManager.knownServersManager.findServerByAddress(serverData.serverIP)?.id ?: return
        connectionManager.telemetryManager.enqueue(
            ClientTelemetryPacket("FEATURED_SERVER_FAVORITE", mapOf("server" to id))
        )
    }

    fun getFriendsServers(): Collection<ServerData> {
        val entries = mutableMapOf<String, ServerData>()
        //#if MC>=12002
        //$$ val mcServerType = ServerInfo.ServerType.OTHER
        //#else
        val mcServerType = false // aka isLan
        //#endif

        // Add all the SPS sessions which we have access to
        for (session in spsManager.remoteSessions) {
            val address = SpsAddress(session.hostUUID).toString()
            val server = ServerData("Loading username…", address, mcServerType).apply {
                ext.isTrusted = false
            }
            server.resourceMode = ServerData.ServerResourceMode.PROMPT
            UUIDUtil.getName(session.hostUUID).thenAcceptAsync({
                server.serverName = (if (it.endsWith("s", true)) "$it'" else "$it's") + " world"
            }, UMinecraft.getMinecraft().executor)
            entries[address] = server
        }

        // Collect addresses which we want to show from various sources (these will be de-duplicated later)
        val addresses = mutableSetOf<String>()

        // show all the server invites we received
        addresses.addAll(connectionManager.socialManager.incomingServerInvites.values)

        // show all the servers which our friends are playing on
        for ((uuid, activityAddressPair) in profileManager.activities.entries) {
            if (uuid == UUIDUtil.getClientUUID()) continue
            if (activityAddressPair.first == ActivityType.PLAYING && !activityAddressPair.second.isNullOrBlank()) {
                if (activityAddressPair.second == "Singleplayer") continue

                addresses.add(activityAddressPair.second)
            }
        }

        // If the user has manually added a server to their list, we always want to use their name for it
        val knownServers = getKnownServers()

        // Now that we've got a list of server addresses, we need to deduplicate them and determine a name for each.
        for (address in addresses) {
            // We only show SPS sessions which we have access to, and already do so in a dedicated loop above
            if (SpsAddress.parse(address) != null) continue

            val server = knownServers[address]
                ?: connectionManager.knownServersManager.findServerByAddress(address)?.toServerData(knownServers)
                ?: ServerData("Server", address, mcServerType).apply {
                    ext.isTrusted = false
                    resourceMode = ServerData.ServerResourceMode.PROMPT
                }
            entries[server.serverIP] = server
        }

        return entries.values
    }

    fun updateFriendsServers() {
        clearServerList()

        for (entry in getFriendsServers()) {
            serverListInternet.add(newEntry(entry))
        }

        updateList()
    }

    fun loadFeaturedServers(seed: Long) {
        clearServerList()

        val knownServers = getKnownServers()

        fun Server.toServerData(): ServerData {
            val supported = MinecraftUtils.currentProtocolVersion in protocolVersions
            if (supported) {
                knownServers[addresses[0]]?.let { return it }
            }

            return ServerData(
                getDisplayName("en_us") ?: addresses[0],
                addresses[0],
                //#if MC>=12002
                //$$ ServerInfo.ServerType.OTHER,
                //#else
                false,
                //#endif
            ).apply {
                ext.isTrusted = false
                ext.showDownloadIcon = !supported
                ext.recommendedVersion = recommendedVersion
                resourceMode = ServerData.ServerResourceMode.ENABLED
            }
        }

        connectionManager.newServerDiscoveryManager.refreshServers()
        val servers = connectionManager.newServerDiscoveryManager.servers.getUntracked()

        val random = Random(seed)
        val featured = servers.featured.shuffled(random)
        val recommended = servers.recommended.shuffled(random)

        val impressionTracker = owner.ext.essential.impressionTracker

        val dividers = mutableMapOf<Int, DividerServerListEntry>()
        if (featured.isNotEmpty()) {
            dividers[0] = DividerServerListEntry(owner, "Featured", true)
            for (server in featured) {
                serverListInternet.add(newEntry(server.toServerData())
                    .also { it.ext.setImpressionConsumer(impressionTracker.featuredConsumer) })
            }
        }

        if (recommended.isNotEmpty()) {
            dividers[serverListInternet.size] = DividerServerListEntry(owner, "Recommended")
            for (server in recommended) {
                serverListInternet.add(newEntry(server.toServerData())
                    .also { it.ext.setImpressionConsumer(impressionTracker.recommendedConsumer) })
            }
        }

        @Suppress("UNCHECKED_CAST")
        (serverSelectionList as SelectionListWithDividers<DividerServerListEntry>).`essential$setDividers`(dividers)

        updateList()
    }

    private fun clearServerList(clearLan: Boolean = true) {
        serverListInternet.clear()
        if (clearLan) {
            serverListLan.clear()
        }
    }

    private fun updateList() {
        //#if MC>=11600
        //$$ (serverSelectionList as ServerSelectionListAccessor).updateList()
        //#endif
    }

    fun isDiscoverEmpty() = EssentialConfig.currentMultiplayerTab == 2 && serverListInternet.isEmpty()

    //#if MC>=11200
    private fun newEntry(serverData: ServerData) = ServerListEntryNormalAccessor.create(
        //#if MC>=11600
        //$$ serverSelectionList,
        //#endif
        owner,
        serverData
    )
    //#else
    //$$ // FIXME: We use this on 1.8.9 because we were stuck on Mixin 0.7, but that's no longer the case, we
    //$$ // should convert this to use an accessor.
    //$$ private fun newEntry(serverData: ServerData): ServerListEntryNormal = ServerListEntryNormal::class.java
    //$$     .declaredConstructors
    //$$     .first()
    //$$     .apply { isAccessible = true }
    //$$     .newInstance(owner, serverData) as ServerListEntryNormal
    //#endif
}