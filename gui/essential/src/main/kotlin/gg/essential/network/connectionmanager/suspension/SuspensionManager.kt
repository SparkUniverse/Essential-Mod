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
package gg.essential.network.connectionmanager.suspension

import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.packet.social.ServerSocialSuspensionStatePacket
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateUsingSystemTime
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.NetworkedManager
import gg.essential.network.connectionmanager.social.Suspension
import gg.essential.network.registerPacketHandler

abstract class SuspensionManager(protected val connectionManager: CMConnection) : NetworkedManager {

    private var suspension = mutableStateOf<SuspensionStatus?>(null)
    val activeSuspension: State<Suspension?> = stateUsingSystemTime { now -> suspension()?.value?.takeIf { it.isActive(now) } }
    val isLoaded: State<Boolean> = suspension.letState { it != null }

    init {
        connectionManager.registerPacketHandler<ServerSocialSuspensionStatePacket> { packet ->
            if (packet.isSuspended) {
                suspension.set(SuspensionStatus(Suspension(packet.reason, packet.expiresAt, packet.isRecentlyStarted)))
            } else {
                suspension.set(SuspensionStatus(null))
            }
            EssentialConfig.acknowledgedPermanentSuspension.set(false)
        }
    }

    fun hookUp(referenceHolder: ReferenceHolder, canShowModal: State<Boolean>) {
        effect(referenceHolder) {
            activeSuspension()?.let { suspension ->
                if (suspension.unseen && canShowModal.getUntracked()) {
                    showSuspension(suspension)
                }
            }
        }
    }

    fun setPermanentlySuspended(reason: String) {
        suspension.set(SuspensionStatus(Suspension(reason, null, !EssentialConfig.acknowledgedPermanentSuspension.getUntracked())))
    }

    protected abstract fun showSuspension(suspension: Suspension)

    fun markSeen() {
        suspension.set { it?.copy(unseen = false) }
    }

    @Override
    override fun resetState() {
        suspension.set(null)
    }

    private data class SuspensionStatus(val value: Suspension?) {

        fun copy(unseen: Boolean) = SuspensionStatus(value?.copy(unseen = unseen))
    }
}
