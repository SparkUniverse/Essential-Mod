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
package gg.essential.commands.impl

import gg.essential.Essential
import gg.essential.api.commands.Command
import gg.essential.api.commands.DefaultHandler
import gg.essential.api.commands.DisplayName
import gg.essential.api.commands.SubCommand
import gg.essential.commands.engine.EssentialFriend
import gg.essential.commands.engine.EssentialUser
import gg.essential.gui.sps.launchInviteOrHostModalFlow
import gg.essential.sps.WorldManager
import gg.essential.universal.ChatColor
import gg.essential.universal.UMinecraft
import gg.essential.util.*
import kotlinx.coroutines.future.await
import java.lang.IllegalStateException
import java.util.*

abstract class CommandOpBase(name: String) : Command(name) {

    val worldManager: WorldManager
        get() = Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()
            ?: throw IllegalStateException("Command should not be registered")

    @DefaultHandler
    suspend fun handle(@DisplayName("player") user: EssentialUser) {
        if (!worldManager.gameSettings.getUntracked().cheats) {
            MinecraftUtils.sendMessage("Cheats must be enabled to use the op command.")
            return
        }

        if (user.uuid !in worldManager.members.getUntracked()) {
            if (this is CommandOp) {
                MinecraftUtils.sendMessage("Cannot op ${user.name} because they are not invited to your world")
            } else {
                MinecraftUtils.sendMessage("Cannot deop ${user.name} because they are not invited to your world")
            }
            return
        }

        apply(user.uuid, user.name)
    }

    abstract suspend fun apply(uuid: UUID, username: String)
}

object CommandDeOp : CommandOpBase("deop") {

    override suspend fun apply(uuid: UUID, username: String) {
        if (uuid in worldManager.gameSettings.getUntracked().ops) {
            worldManager.updateGameSettings { it.copy(ops = it.ops - uuid) }
            MinecraftUtils.sendMessage("Removed op from $username.")
        } else {
            MinecraftUtils.sendMessage("$username is not opped.")
        }
    }
}

object CommandOp : CommandOpBase("op") {

    override suspend fun apply(uuid: UUID, username: String) {
        if (uuid in worldManager.gameSettings.getUntracked().ops) {
            MinecraftUtils.sendMessage("$username is already opped.")
        } else {
            worldManager.updateGameSettings { it.copy(ops = it.ops + uuid) }
            MinecraftUtils.sendMessage("$username is now opped.")
        }
    }
}

object CommandInvite : Command("einvite") {

    @DefaultHandler
    suspend fun handle(@DisplayName("friend") friend: EssentialFriend) {

        if (friend.uuid == UUIDUtil.getClientUUID()) {
            MinecraftUtils.sendMessage("You cannot invite yourself.")
            return
        }

        val username = friend.ign
        val connectionManager = Essential.getInstance().connectionManager
        val socialManager = connectionManager.socialManager
        val uuid = friend.uuid
        val serverType = ServerType.current()

        if (serverType !is ServerType.SPS.Host) {
            when (serverType) {
                is ServerType.Singleplayer -> {
                    val worldManager = Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()
                    if (worldManager != null) {
                        worldManager.updateLocalWorldInfo { it.copy(invited = it.invited + uuid) }
                    }
                    launchInviteOrHostModalFlow()
                }
                is ServerType.Multiplayer -> {
                    // Reinvite in case they're already invited, so they receive the notification again
                    socialManager.reinviteFriendsOnServer(serverType.address, setOf(uuid))
                    MinecraftUtils.sendMessage("Invited $username.")
                }
                else -> MinecraftUtils.sendMessage("You cannot invite players to this server.")
            }
            return
        }

        val worldManager = Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()
            .let { it!! } // already checked serverType above

        if (uuid in worldManager.members.getUntracked()) {
            MinecraftUtils.sendMessage("$username is already invited to your world.")
            return
        }


        worldManager.updateLocalWorldInfo { it.copy(invited = it.invited + uuid) }
        MinecraftUtils.sendMessage("Invited $username to your world.")
    }

    @SubCommand("cancel", description = "Cancel invite to player")
    suspend fun handleCancelInvite(@DisplayName("friend") friend: EssentialUser) {
        if (friend.uuid == UUIDUtil.getClientUUID()) {
            MinecraftUtils.sendMessage("You cannot remove an invite from yourself.")
            return
        }
        cancelInviteAndKick(friend.uuid, friend.name, false)
    }
}

private suspend fun cancelInviteAndKick(uuid: UUID, username: String, kick: Boolean) {
    val connectionManager = Essential.getInstance().connectionManager
    val serverType = ServerType.current()

    if (serverType !is ServerType.SPS.Host) {
        if (serverType !is ServerType.Multiplayer) {
            MinecraftUtils.sendMessage("Cannot cancel invite because you are not currently on a session that supports invites")
            return
        }
        val invites = connectionManager.socialManager.getInvitesOnServer(serverType.address)
        if (uuid !in invites) {
            MinecraftUtils.sendMessage("Cannot cancel invite because $username is not invited to your current session")
            return
        }

        connectionManager.socialManager.setInvitedFriendsOnServer(
            serverType.address, invites - uuid,
        )
        MinecraftUtils.sendMessage("Cancelled invite to $username")
        return
    }

    val worldManager = Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()
        .let { it!! } // already checked serverType above

    if (uuid !in worldManager.members.getUntracked()) {
        MinecraftUtils.sendMessage("$username is not invited to your world.")
        return
    }


    worldManager.updateLocalWorldInfo { it.copy(invited = it.invited - uuid) }

    if (kick) {
        MinecraftUtils.sendMessage("Kicked $username")
    } else {
        MinecraftUtils.sendMessage("Canceled invite to $username")
    }
}

object CommandKick : Command("kick") {

    @DefaultHandler
    suspend fun handle(@DisplayName("player") player: EssentialUser) {
        if (player.uuid == UUIDUtil.getClientUUID()) {
            MinecraftUtils.sendMessage("You cannot kick yourself.")
            return
        }

        cancelInviteAndKick(player.uuid, player.name, true)
    }
}

object CommandSession : Command("esession") {

    private val connectionManager = Essential.getInstance().connectionManager
    private val socialManager = connectionManager.socialManager

    @SubCommand("open", description = "Start a world share session")
    fun handleOpen() {
        when (ServerType.current()) {
            is ServerType.SPS.Host -> MinecraftUtils.sendMessage("Cannot start session, one is already running.")
            is ServerType.Singleplayer, is ServerType.SupportsInvites -> {
                launchInviteOrHostModalFlow()
            }
            else -> MinecraftUtils.sendMessage("Cannot start session, your current world does not support invites")
        }
    }

    @SubCommand("close", description = "Close your world share session")
    fun handleClose() {
        val currentServerData = UMinecraft.getMinecraft().currentServerData

        val worldManager = Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()

        when {
            worldManager != null && worldManager.localWorldOpen.getUntracked() -> {
                worldManager.localShareSession.set(null)
                MinecraftUtils.sendMessage("Closed session")
            }

            // On a multiplayer server with friends invited
            currentServerData != null && socialManager.getInvitesOnServer(currentServerData.serverIP).isNotEmpty() -> {
                socialManager.setInvitedFriendsOnServer(currentServerData.serverIP, emptySet())
                MinecraftUtils.sendMessage("Closed session")
            }

            // Other cases do not have a session running to close
            else -> MinecraftUtils.sendMessage("No session running")
        }

    }

    @SubCommand("info", description = "Info about your world share session")
    suspend fun handleInfo() {
        val worldManager = Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()
        if (worldManager == null || !worldManager.localWorldOpen.getUntracked()) {
            handleInfoNoSps()
            return
        }
        handleInfoSps(worldManager)
    }

    suspend fun handleInfoNoSps() {
        val currentServerData = UMinecraft.getMinecraft().currentServerData
        if (currentServerData != null) {
            val invitesOnServer = socialManager.getInvitesOnServer(currentServerData.serverIP)
            if (invitesOnServer.isNotEmpty()) {
                MinecraftUtils.sendMessage("Invited Players: ")
                invitesOnServer.forEach {
                    MinecraftUtils.sendMessage(" - ${UUIDUtil.getName(it).await()}")
                }
                return
            }
        }
        MinecraftUtils.sendMessage("No session running")
    }

    suspend fun handleInfoSps(worldManager: WorldManager) {
        val settings = worldManager.gameSettings.getUntracked()
        MinecraftUtils.sendMessage("Cheats for all: ${settings.cheats}")
        MinecraftUtils.sendMessage("Default gamemode: ${settings.gameMode}")
        MinecraftUtils.sendMessage("Difficulty: ${settings.difficulty}")
        MinecraftUtils.sendMessage("Invited Players: ")
        for (user in worldManager.members.getUntracked()) {
            val username = UUIDUtil.getName(user).await()
            val colorPrefix = when {
                user == UUIDUtil.getClientUUID() -> ChatColor.AQUA
                user in worldManager.connectedMembers.getUntracked() -> ChatColor.GREEN
                else -> ChatColor.GRAY
            }
            val suffix = when(user) {
                worldManager.host.getUntracked() -> " (Host)"
                in settings.ops -> " (OP)"
                else -> ""
            }
            MinecraftUtils.sendMessage("$colorPrefix - $username$suffix")
        }
    }

}