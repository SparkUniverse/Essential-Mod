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
package gg.essential.network.connectionmanager.social.handler

import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.packet.social.SocialInviteToServerPacket
import gg.essential.gui.notification.sendServerInviteNotification
import gg.essential.network.connectionmanager.handler.PacketHandler
import gg.essential.network.connectionmanager.social.SocialManager
import gg.essential.util.Multithreading
import java.util.*
import java.util.concurrent.TimeUnit

class SocialInviteToServerPacketHandler(val socialManager: SocialManager) : PacketHandler<SocialInviteToServerPacket> {
    private val cooldowns = mutableSetOf<UUID>()

    override fun handle(packet: SocialInviteToServerPacket) {
        if (!EssentialConfig.essentialEnabled) return

        val hostUUID = packet.uuid
        val address = packet.address
        socialManager.addIncomingServerInvite(hostUUID, address)

        if (cooldowns.contains(hostUUID)) return
        cooldowns.add(hostUUID)
        Multithreading.scheduleOnMainThread({ cooldowns.remove(hostUUID) }, NOTIFICATION_COOLDOWN_DURATION, TimeUnit.SECONDS)

        sendServerInviteNotification(hostUUID, address) {
            socialManager.removeIncomingServerInvite(hostUUID)
        }

    }

    companion object {
        private const val NOTIFICATION_COOLDOWN_DURATION = 11L
    }
}