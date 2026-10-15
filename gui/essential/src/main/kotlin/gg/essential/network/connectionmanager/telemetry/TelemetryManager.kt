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
package gg.essential.network.connectionmanager.telemetry

import gg.essential.config.EssentialConfig.collectOptionalTelemetryWithSource
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.connectionmanager.common.packet.telemetry.ServerRecognizedTelemetryPacket
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.collections.trackedListOf
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.modals.analyticsModal
import gg.essential.gui.overlay.launchModalFlowForEach
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.NetworkedManager
import gg.essential.network.connectionmanager.queue.SequentialPacketQueue
import gg.essential.network.registerPacketHandler

class TelemetryManager(private val cmConnection: CMConnection) : NetworkedManager {
    private val telemetryQueue: SequentialPacketQueue = SequentialPacketQueue.Builder(cmConnection)
        .onTimeoutSkip()
        .create()
    private val packetList = mutableListOf<ClientTelemetryPacket>()
    private var recognizedTelemetryKeys: List<String>? = null

    private val wantToShowOptInModal = mutableStateOf(false)

    init {
        cmConnection.registerPacketHandler { packet: ServerRecognizedTelemetryPacket ->
            setRecognizedTelemetryKeys(packet.recognizedTelemetry)
            val acceptedTelemetryCategories = packet.acceptedTelemetryCategories
            if (acceptedTelemetryCategories != null) {
                if (acceptedTelemetryCategories.isEmpty()) {
                    // Empty categories list means either a new user or user just upgraded to telemetry opt-in, so we force them to select an option.
                    wantToShowOptInModal.set(true)
                } else {
                    collectOptionalTelemetryWithSource.set(acceptedTelemetryCategories.contains("OPTIONAL") to false)
                }
            }
        }
    }

    private fun setRecognizedTelemetryKeys(keys: List<String>) {
        recognizedTelemetryKeys = keys

        // Send all pending packets now that they can be filtered by recognizedTelemetryKeys
        if (packetList.isNotEmpty()) {
            // New list to iterate, as enqueue() may result in packetList.add(), if the connection was somehow lost
            val pendingPackets = packetList.toList()
            packetList.clear()
            pendingPackets.forEach { enqueue(it) }
        }
    }

    // Adds the packet to a SequentialPacketQueue if the user is connected, authenticated, and recognizedTelemetryKeys is set and matching
    // Otherwise, adds the packet to a list to be processed when the recognizedTelemetryKeys are received after connection
    fun enqueue(packet: ClientTelemetryPacket) {
        val recognizedTelemetryKeys = recognizedTelemetryKeys
        if (cmConnection.isOpen && recognizedTelemetryKeys != null) {
            // Only enqueue the packet if it has a server recognized telemetry key
            if (recognizedTelemetryKeys.contains(packet.key)) {
                telemetryQueue.enqueue(packet)
            }
        } else {
            packetList.add(packet)
        }
    }

    override fun onConnected() {
        recognizedTelemetryKeys = null // The ServerRecognizedTelemetryPacket will be sent by the server after connection to set this
    }

    fun hookUp(referenceHolder: ReferenceHolder, canShowModal: State<Boolean>) {
        val showOptInModal = memo { wantToShowOptInModal() && canShowModal() }
            .letState { if (it) trackedListOf(Unit) else trackedListOf()  }
        launchModalFlowForEach(referenceHolder, showOptInModal) { _ ->
            analyticsModal()
            wantToShowOptInModal.set(false)
        }
    }

    /**
     * Called to send a piece of telemetry when the user performs a tracked action
     * 
     * @param action the action the user performed.
     * @param context the action context (e.g. the emote activated)
     */
    @JvmOverloads
    fun clientActionPerformed(action: Actions, context: String? = null) {
        enqueue(ClientTelemetryPacket.forAction(action.name, context))
    }

    /**
     * List of tracked actions that report to infra
     */
    enum class Actions {
        EMOTE_WHEEL_ACTIVATE,
        EMOTE_ACTIVATE,
        EMOTE_WHEEL_EDIT,
        EMOTE_WARDROBE_SECTION_VIEWED,
        CART_NOT_EMPTY_WARNING,
        PERSISTENT_TOAST_CLEARED,
        PERSISTENT_TOAST_CLICKED,
    }
}
