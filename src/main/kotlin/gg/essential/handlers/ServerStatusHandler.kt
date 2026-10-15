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
package gg.essential.handlers

import gg.essential.Essential
import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.enums.ActivityType
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.event.gui.GuiOpenEvent
import gg.essential.event.network.server.ServerJoinEvent
import gg.essential.event.network.server.ServerLeaveEvent
import gg.essential.event.network.server.SingleplayerJoinEvent
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.mixins.ext.client.multiplayer.ext
import gg.essential.sps.SpsAddress
import gg.essential.util.AddressUtil
import gg.essential.util.AddressUtil.isLanOrLocalAddress
import gg.essential.util.AddressUtil.removeDefaultPort
import gg.essential.util.USession
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.gui.GuiMainMenu

/**
 * Updates user status on Essential network
 */
class ServerStatusHandler {

    private data class Activity(val type: ActivityType?, val metadata: String?, val privacyOverride: Boolean? = null){
        companion object {
            val None = Activity(null, null)
        }
    }

    private val eventDrivenCurrentActivity: MutableState<Activity> = mutableStateOf(Activity.None)

    // this should reflect the current activity state for the network considering privacy settings
    private val published = memo {
        val current = run {
            val worldsManager = Essential.getInstance().worldsManager
            val integratedServerWorld = worldsManager.integratedServerWorld()
            when {
                integratedServerWorld != null && integratedServerWorld.localWorldOpen() ->
                    Activity(ActivityType.PLAYING, SpsAddress(USession.active().uuid).toString())
                else -> eventDrivenCurrentActivity()
            }
        }
        if (EssentialConfig.essentialEnabledState() && (current.privacyOverride ?: EssentialConfig.sendServerUpdatesState())) current else Activity.None
    }

    private val refHolder: ReferenceHolder = ReferenceHolderImpl()

    init {
        // listen for settings or activity state changes
        effect(refHolder) {
            val (type, metadata) = published()
            Essential.getInstance().connectionManager.profileManager.updatePlayerActivity(type, metadata)
        }
    }

    @Subscribe
    fun onGuiSwitch(event: GuiOpenEvent) {
        if (event.gui is GuiMainMenu) eventDrivenCurrentActivity.set(Activity.None)
    }

    @Subscribe
    fun disconnectEvent(event: ServerLeaveEvent) {
        eventDrivenCurrentActivity.set(Activity.None)
    }

    @Subscribe
    fun connect(event: ServerJoinEvent) {
        val serverData = event.serverData
        val serverIP = serverData.serverIP
        val metadata = if (isLanOrLocalAddress(serverIP)) AddressUtil.LOCAL_SERVER else removeDefaultPort(serverIP)
        eventDrivenCurrentActivity.set(Activity(ActivityType.PLAYING, metadata,
            serverData.ext.`essential$shareWithFriends`)) // individual servers may declare their own privacy setting to override the global one
    }

    @Subscribe
    fun joinSinglePlayer(event: SingleplayerJoinEvent) {
        eventDrivenCurrentActivity.set(Activity(ActivityType.PLAYING, AddressUtil.SINGLEPLAYER))
    }
}
