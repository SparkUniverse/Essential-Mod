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
package gg.essential.gui.friends.state

import gg.essential.Essential
import gg.essential.connectionmanager.common.enums.ProfileStatus
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.collections.effectOnChange
import gg.essential.network.connectionmanager.profile.ProfileManager
import gg.essential.sps.SpsAddress
import gg.essential.util.AddressUtil
import gg.essential.util.MinecraftUtils
import gg.essential.util.UUIDUtil
import gg.essential.util.thenAcceptOnMainThread
import java.util.*


class StatusStateManagerImpl(
    private val profileManager: ProfileManager,
) : IStatusStates, IStatusManager {

    private val refHolder = ReferenceHolderImpl()

    private val statesMap = mutableMapOf<UUID, MutableState<PlayerActivity>>()
    private val remoteSpsSessions = Essential.getInstance().worldsManager.remoteSpsSessions

    init {
        profileManager.registerStateManager(this)

        remoteSpsSessions.effectOnChange(
            refHolder,
            add = { refreshActivity(it.value.hostUUID) },
            remove = { refreshActivity(it.value.hostUUID) },
        )
    }

    private fun haveSpsSession(host: UUID): Boolean {
        return remoteSpsSessions.getUntracked().any { it.hostUUID == host }
    }

    override fun getActivityState(uuid: UUID): State<PlayerActivity> = getWritableState(uuid)

    override fun getActivity(uuid: UUID): PlayerActivity {
        val status = profileManager.getStatus(uuid)
        val activity = profileManager.getActivity(uuid).orElse(null)
        return when (status) {
            ProfileStatus.OFFLINE -> PlayerActivity.Offline(
                profileManager.getLastOnline(uuid)
            )
            ProfileStatus.ONLINE -> when (activity) {
                null -> {
                    if (haveSpsSession(uuid)) {
                        PlayerActivity.SPSSession(uuid, true)
                    } else {
                        PlayerActivity.Online
                    }
                }
                else -> {
                    val address = activity.second
                    if (AddressUtil.isSpecialFormattedAddress(address)) {
                        return PlayerActivity.OnlineWithDescription(address)
                    }
                    SpsAddress.parse(address)?.host?.let { host ->
                        return PlayerActivity.SPSSession(
                            host,
                            haveSpsSession(host),
                        )
                    }
                    PlayerActivity.Multiplayer(address)
                }
            }
        }
    }

    override fun joinSession(uuid: UUID): Boolean {
        val activity = getActivity(uuid)
        val (address, playerUuidForServerName) = when {
            activity is PlayerActivity.Multiplayer -> activity.serverAddress to uuid
            activity is PlayerActivity.SPSSession && activity.invited -> SpsAddress(activity.host).toString() to activity.host
            else -> return false
        }
        UUIDUtil.getName(playerUuidForServerName).thenAcceptOnMainThread {
            MinecraftUtils.connectToServer(it, address)
        }
        return true
    }

    private fun getWritableState(uuid: UUID): MutableState<PlayerActivity> {
        return statesMap.computeIfAbsent(uuid) {
            mutableStateOf(getActivity(uuid))
        }
    }

    override fun refreshActivity(uuid: UUID) {
        getWritableState(uuid).set(getActivity(uuid))

        // If player B is on player A's SPS session and player A revokes the invite,
        // we must also update the session for player B, and all other players on that session
        statesMap.entries.forEach {
            if (it.key == uuid) {
                return
            }
            val activity = it.value.getUntracked()
            if (activity is PlayerActivity.SPSSession && activity.host == uuid) {
                refreshActivity(it.key)
            }
        }
    }

}

