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

import gg.essential.Essential
import gg.essential.universal.ChatColor
import gg.essential.universal.UMinecraft
import gg.essential.universal.wrappers.UPlayer
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import gg.essential.api.utils.MinecraftUtils
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.event.client.ClientTickEvent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.modal.ConfirmDenyModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.modal.sps.FirewallBlockingModal
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.util.stateBy
import gg.essential.sps.SpsAddress
import gg.essential.universal.UChat
import gg.essential.universal.UScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.apache.commons.io.IOUtils
import javax.imageio.ImageIO

//#if MC>=12106
//$$ import net.minecraft.client.world.ClientWorld
//#endif

//#if MC>=12002
//$$ import net.minecraft.SharedConstants
//#endif

//#if MC >=11400
//#else
import net.minecraft.launchwrapper.Launch
//#endif

//#if MC>=11700
//$$ import net.minecraft.client.network.ServerAddress;
//#endif

//#if FABRIC
//$$ import net.fabricmc.loader.api.FabricLoader;
//#endif

object MinecraftUtils : MinecraftUtils {
    private const val MESSAGE_PREFIX: String = "[Essential] "
    private val messages: Queue<Any> = ConcurrentLinkedQueue()
    private val mc = UMinecraft.getMinecraft()

    @JvmStatic
    //#if MC>=12002
    //$$ val currentProtocolVersion: Int = SharedConstants.getProtocolVersion()
    //#else
    val currentProtocolVersion: Int = ServerData("", "", false).version
    //#endif

    //#if MC<12105
    @Deprecated("Use `UChat.chat` instead.")
    override fun sendMessage(message: gg.essential.universal.wrappers.message.UTextComponent) {
        messages.add(message)
    }
    //#endif

    override fun sendChatMessageAndFormat(message: String) {
        sendMessage(I18n.format(message))
    }

    override fun sendChatMessageAndFormat(message: String, vararg parameters: Any) {
        sendMessage(I18n.format(message, *parameters))
    }

    override fun sendMessage(message: String) {
        sendMessage("${ChatColor.GREEN}$MESSAGE_PREFIX", message)
    }

    override fun sendMessage(prefix: String, message: String) {
        messages.add(prefix + ChatColor.RESET + I18n.format(message))
    }

    override fun isHypixel(): Boolean {
        if (!UPlayer.hasPlayer() || UMinecraft.getWorld() == null || mc.isSingleplayer) {
            return false
        }

        //#if MC>=12002
        //$$ val serverBrand = UMinecraft.getNetHandler()?.brand ?: return false
        //#else
        val serverBrand = UPlayer.getPlayer()?.serverBrand ?: return false
        //#endif
        return serverBrand.lowercase(Locale.ENGLISH).contains("hypixel")
    }

    override fun getResourceImage(location: ResourceLocation): BufferedImage? {
        //#if MC>=11900
        //$$ val stream = mc.resourceManager.open(location)
        //#else
        val stream = mc.resourceManager.getResource(location).inputStream
        //#endif
        // Used instead of TextureUtil since 1.14+ doesn't have the method anymore
        return try {
            val bufferedimage: BufferedImage

            try {
                bufferedimage = ImageIO.read(stream)
            } finally {
                IOUtils.closeQuietly(stream)
            }

            return bufferedimage
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun isDevelopment(): Boolean {
        //#if FABRIC
        //$$ return FabricLoader.getInstance().isDevelopmentEnvironment
        //#else
        //#if MC>=11400
        //$$ val target = System.getenv("target") ?: ""
        //$$ return target.equals("fmluserdevclient", ignoreCase = true)
        //#else
        return Launch.blackboard["fml.deobfuscatedEnvironment"] == true
        //#endif
        //#endif
    }

    @Subscribe
    fun tick(event: ClientTickEvent?) {
        if (!UPlayer.hasPlayer()) return
        var message: Any?
        while (messages.poll().also { message = it } != null) {
            UChat.chat(message ?: continue)
        }
    }

    fun getWorldName(): String? {
        //#if MC<=11202
        return Minecraft.getMinecraft().integratedServer?.worldName
        //#else
        //$$ return Minecraft.getInstance().integratedServer?.serverConfiguration?.worldName
        //#endif
    }

    /**
     * Connects to a server, disconnecting from the current server. Must be called from the
     * client thread.
     *
     * @param serverName The name of the server, stored as part of the server data. Not used
     * by vanilla, but may be used by third party mods.
     * @param hostAndPort The host and port to connect to
     * @param resourceMode The resource mode to use
     * @param previousScreen The screen that should be opened after canceling the connection
     * or experiencing an abnormal disconnect (being kicked for example). Note that clicking
     * the Disconnect button will not result in this screen being opened.
     * Defaults to the multiplayer screen. If null, the title screen will be used.
     */
    @JvmOverloads
    fun connectToServer(
        serverName: String,
        hostAndPort: String,
        resourceMode: ServerData.ServerResourceMode = ServerData.ServerResourceMode.PROMPT,
        previousScreen: GuiScreen? = GuiMultiplayer(GuiMainMenu()),
        showDisconnectWarning: Boolean = true,
    ) {
        val serverData = ServerData(
            serverName,
            hostAndPort,
            //#if MC>=12002
            //$$ ServerInfo.ServerType.OTHER,
            //#else
            false,
            //#endif
        )
        serverData.resourceMode = resourceMode

        connectToServer(serverData, previousScreen, showDisconnectWarning)
    }

    /**
     * Connects to a server, prompting the user with a warning modal before disconnecting from the current server/world.
     * Must be called from the client thread.
     *
     * @param serverData The server to connect to
     * @param previousScreen The screen that should be opened after canceling the connection
     * or experiencing an abnormal disconnect (being kicked for example). Note that clicking
     * the Disconnect button will not result in this screen being opened.
     * Defaults to the multiplayer screen. If null, the title screen will be used.
     */
    @JvmOverloads
    fun connectToServer(
        serverData: ServerData,
        previousScreen: GuiScreen? = GuiMultiplayer(GuiMainMenu()),
        showDisconnectWarning: Boolean = true,
    ) {
        if (UScreen.currentScreen is GuiConnecting) {
            Essential.logger.warn("Attempted to connect to a server whilst already connecting!")
            return
        }

        if (UMinecraft.getWorld() != null && showDisconnectWarning) {
            val spsHost = SpsAddress.parse(serverData.serverIP)?.host
            val serverName =
                if (spsHost != null) {
                    UUIDUtil.getNameAsState(spsHost, "this friend").map { "$it's world" }
                } else {
                    BasicState(serverData.serverIP)
                }
            GuiUtil.pushModal { manager ->
                HangOnModal(manager, serverName) { connectToServer(serverData, previousScreen, false) }
            }
            return
        }

        if (SpsAddress.parse(serverData.serverIP) != null && FirewallUtil.isFirewallBlocking()) {
            GuiUtil.pushModal { manager ->
                FirewallBlockingModal(manager, SpsAddress.parse(serverData.serverIP)?.host) {
                    connectToServer(serverData, previousScreen)
                }
            }
            return
        }

        //#if MC>=12106
        //$$ mc.world?.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT)
        //#else
        @Suppress("UNNECESSARY_SAFE_CALL")
        mc.world?.sendQuittingDisconnectingPacket()
        //#endif

        //#if MC>=11700
        //$$ ConnectScreen.connect(previousScreen ?: TitleScreen(), mc, ServerAddress.parse(serverData.address), serverData,
            //#if MC>=12000
            //$$ false,
            //#endif
            //#if MC>=12005
            //$$ null,
            //#endif
        //$$ )
        //#else
        mc.displayGuiScreen(GuiConnecting(previousScreen ?: GuiMainMenu(), mc, serverData))
        //#endif
    }

    fun shutdown() =
        UMinecraft.getMinecraft().shutdown()

    class HangOnModal(manager: ModalManager, serverName: State<String>, action: () -> Unit) : ConfirmDenyModal(manager, false) {
        init {
            val message = stateBy {
                "Connecting to ${ChatColor.WHITE}${serverName()}${ChatColor.RESET} will disconnect you from your current world or server."
            }
            configure {
                titleText = "Hang on..."
                contentTextColor = EssentialPalette.TEXT
                primaryButtonAction = action
                setTextContent(message)
            }
        }
    }
}
