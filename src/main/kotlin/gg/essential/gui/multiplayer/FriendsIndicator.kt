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
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.Observer
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mapList
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.sps.SpsAddress
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMinecraft
import gg.essential.util.CachedAvatarImage
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.UuidNameLookup
import gg.essential.util.findChildOfTypeOrNull
import gg.essential.vigilance.gui.VigilancePalette
import net.minecraft.client.multiplayer.ServerData
import java.util.UUID

class FriendsIndicator(val server: ServerData) {
    private val connectionManager = Essential.getInstance().connectionManager

    private val host = SpsAddress.parse(server.serverIP)?.host
    private val socialStates = platform.createSocialStates()
    private val friends = socialStates.relationships.friends
        .mapEach { it to memo { isPlayingOnServer(it) } }
        .mapList { list -> list.mapNotNull { (uuid, playing) -> if (playing()) uuid else null }.sorted().sortedBy { it != host } }
        .mapEach { Friend(it) }

    private class Friend(val uuid: UUID) {
        val name: State<String> = UuidNameLookup.nameState(uuid, "Loading username…")
        val avatar: UIComponent = CachedAvatarImage.create(uuid)
    }

    private fun Observer.isPlayingOnServer(uuid: UUID): Boolean {
        return when (val activity = socialStates.activity.getActivityState(uuid)()) {
            is PlayerActivity.SPSSession -> activity.host == host
            is PlayerActivity.Multiplayer -> {
                val knownServers = connectionManager.knownServersManager.state()
                knownServers.findServerByAddress(activity.serverAddress) == knownServers.findServerByAddress(server.serverIP)
            }

            is PlayerActivity.Offline -> false
            is PlayerActivity.Online -> false
            is PlayerActivity.OnlineWithDescription -> false
        }
    }

    private fun appendInvite(uuid: UUID) = if (connectionManager.socialManager.incomingServerInvites[uuid] == server.serverIP) " (Invite)" else ""

    fun draw(
        matrixStack: UMatrixStack,
        x: Int,
        y: Int,
        listWidth: Int,
        mouseX: Int,
        mouseY: Int,
        populationInfoText: Int,
    ): String? {
        if (!EssentialConfig.essentialEnabled) return null

        val entries = friends.getUntracked()
        if (entries.isEmpty()) return null

        // Figure out the space available for head icons
        val serverNameEndPos = x + 32 + 2 + UMinecraft.getFontRenderer().getStringWidth(server.serverName) + 16
        //#if MC>=12100
        //$$ val populationInfoOffset = 5
        //#else
        val populationInfoOffset = 2
        //#endif
        val playerCountStartPos = x + listWidth - 15 - populationInfoOffset - populationInfoText - (HEAD_PADDING * 2)
        val spaceAvailable = playerCountStartPos - serverNameEndPos

        // Figure out how many heads can fit in the space available and how many to display if we need to truncate them
        val numHeadsCanFit = minOf((spaceAvailable + HEAD_PADDING) / PADDED_HEAD_WIDTH, entries.size, MAX_ALLOWED_ICONS)
        val numHeadsToDisplay = (numHeadsCanFit - if (entries.size > numHeadsCanFit && spaceAvailable - (numHeadsCanFit * PADDED_HEAD_WIDTH) < TRUNCATED_WIDTH) 1 else 0).coerceAtLeast(0)

        val displayedFriends = entries.subList(0, numHeadsToDisplay)
        val truncatedFriends = entries.subList(numHeadsToDisplay, entries.size)

        // If there's no space for any heads or the ellipses, don't draw anything
        if ((truncatedFriends.isNotEmpty() && spaceAvailable < TRUNCATED_WIDTH) || (truncatedFriends.isEmpty() && spaceAvailable < HEAD_SIZE)) return null

        // Heads are drawn left to right, but right-aligned to the player count, so figure out where to start drawing
        val startX = playerCountStartPos - ((displayedFriends.size * PADDED_HEAD_WIDTH) + if (truncatedFriends.isNotEmpty()) TRUNCATED_WIDTH - 1 else -HEAD_PADDING)
        var tooltip: String? = null

        // Display head icons
        displayedFriends.forEachIndexed { index, friend ->
            val currentX = startX + (index * PADDED_HEAD_WIDTH)
            if (mouseX in currentX until currentX + HEAD_SIZE && mouseY in y..(y + HEAD_SIZE)) {
                tooltip = friend.name.getUntracked() + appendInvite(friend.uuid)
            }
            friend.avatar.findChildOfTypeOrNull<UIImage>(recursive = true)!!.drawImage(
                matrixStack,
                currentX.toDouble(),
                y.toDouble(),
                HEAD_SIZE.toDouble(),
                HEAD_SIZE.toDouble(),
                VigilancePalette.getBrightText(),
            )
        }

        // If there are more friends than can be displayed, draw an ellipsis and set the tooltip to show all the truncated friends
        if (truncatedFriends.isNotEmpty()) {
            val ellipsesX = startX + (displayedFriends.size * PADDED_HEAD_WIDTH)
            if (mouseX in ellipsesX - 1 until ellipsesX + TRUNCATED_WIDTH + 1 && mouseY in y until y + HEAD_SIZE + 1) {
                tooltip = "Online friends:\n" + truncatedFriends.joinToString("\n") {
                    it.name.getUntracked() + appendInvite(it.uuid)
                }
            }
            EssentialPalette.ELLIPSES_5X1.create().drawImage(
                matrixStack,
                ellipsesX.toDouble(),
                y + 7.0,
                TRUNCATED_WIDTH.toDouble(),
                1.0,
                VigilancePalette.getBrightText(),
            )
        }

        return tooltip
    }

    private companion object {
        const val HEAD_SIZE = 8
        const val HEAD_PADDING = 2
        const val PADDED_HEAD_WIDTH = HEAD_SIZE + HEAD_PADDING
        const val TRUNCATED_WIDTH = 5
        const val MAX_ALLOWED_ICONS = 8
    }
}
