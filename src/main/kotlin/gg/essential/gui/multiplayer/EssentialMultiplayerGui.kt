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
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.data.OnboardingData
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.common.MenuButton
import gg.essential.gui.common.TextFlag
import gg.essential.gui.common.modal.ConfirmDenyModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.elementa.VanillaButtonConstraint.Companion.constrainTo
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.modals.ensurePrerequisites
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.serverdiscovery.VersionDownloadModal
import gg.essential.mixins.ext.client.gui.acc
import gg.essential.mixins.ext.client.gui.close
import gg.essential.mixins.ext.client.gui.essential
import gg.essential.mixins.ext.client.gui.ext
import gg.essential.mixins.ext.client.gui.refresh
import gg.essential.mixins.ext.client.multiplayer.ext
import gg.essential.mixins.ext.client.multiplayer.recommendedVersion
import gg.essential.mixins.ext.client.multiplayer.showDownloadIcon
import gg.essential.network.connectionmanager.serverdiscovery.NewServerDiscoveryManager
import gg.essential.universal.UScreen
import gg.essential.util.GuiUtil
import gg.essential.util.UDrawContext
import gg.essential.util.createEssentialTooltip
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.ServerSelectionList
import java.util.*

import net.minecraft.client.gui.ServerListEntryNormal
import net.minecraft.client.multiplayer.ServerData
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

//#if MC>=12106
//$$ import gg.essential.util.AdvancedDrawContext
//#endif

//#if MC >= 11600
//$$ import gg.essential.util.textLiteral
//$$ import gg.essential.util.textTranslatable
//#else
import net.minecraft.client.resources.I18n
//#endif

class EssentialMultiplayerGui {
    private var initialized = false

    private lateinit var screen: GuiMultiplayer
    private val width: Int get() = screen.width
    private val height: Int get() = screen.height
    private val shouldShowServerPrivacyModal: Boolean get() = !OnboardingData.hasSeenFriendsOption() && OnboardingData.hasAcceptedTos()
    private lateinit var favouritesTabButton: GuiButton
    private lateinit var friendsTabButton: GuiButton
    private lateinit var discoverTabButton: GuiButton
    private lateinit var addToFavouritesButton: GuiButton
    private var refreshButton: GuiButton? = null
    private var closeButton: GuiButton? = null
    private lateinit var featured: UIImage

    private lateinit var essentialServerList: EssentialServerSelectionList

    private val refHolder = ReferenceHolderImpl()
    private val connectionManager = Essential.getInstance().connectionManager

    // fixme remove lateinit after feature flags are cleaned up
    lateinit var impressionTracker: NewServerDiscoveryManager.ImpressionTracker

    private val window = Window(ElementaVersion.V10)

    private val tooltipParent = UIContainer() childOf window

    private val tooltipText = mutableStateOf("")

    private val tooltip = tooltipParent.createEssentialTooltip(
        tooltipText,
        EssentialTooltip.Position.ABOVE,
        windowPadding = 6f
    )

    private var tooltipHovered = false

    fun initGui(screen: GuiMultiplayer) {
        if (!EssentialConfig.essentialFull) return
        this.screen = screen

        favouritesTabButton = makeButton(100, width / 2 - 154, 36, 100, 20, "Favorites")
        friendsTabButton = makeButton(200, width / 2 - 50, 36, 100, 20, "Friends")
        discoverTabButton = makeButton(300, width / 2 + 54, 36, 100, 20, "Discover")
        addToFavouritesButton =
            makeButton(400, width / 2 - 154, height - secondButtonRowYOffset, 100, 20, "Favorite")
        featured = EssentialPalette.FEATURED_16X.create()

        if (!initialized) {
            initialized = true

            essentialServerList = EssentialServerSelectionList(screen, screen.acc.serverListSelector)

            impressionTracker = connectionManager.newServerDiscoveryManager.ImpressionTracker()

            // Display the "NEW" tag in the first month if the discovery tab hasn't been visited
            if (Instant.now() < NewServerDiscoveryManager.NEW_TAG_END_DATE && !OnboardingData.seenServerDiscovery.getUntracked()) {
                val discoverTabButtonContainer = UIContainer().constrainTo({ discoverTabButton }) childOf window

                TextFlag(
                    stateOf(MenuButton.NOTICE_GREEN),
                    MenuButton.Alignment.CENTER,
                    stateOf("NEW")
                ).constrain {
                    y = CenterConstraint() boundTo discoverTabButtonContainer
                    x = 3.pixels(alignOpposite = true, alignOutside = true) boundTo discoverTabButtonContainer
                } childOf window
            }

            if (!isRefreshing) {
                // Update the seed every time the screen is opened
                serverListSeed = ThreadLocalRandom.current().nextLong()

                // If the user has no favourite servers, open the discovery tab by default
                if (screen.serverList.countServers() == 0 && OnboardingData.hasAcceptedTos()) {
                    EssentialConfig.currentMultiplayerTab = 2
                }
            }

            // Hide the "NEW" tag once the discovery tab has been visited for the first time
            if (EssentialConfig.currentMultiplayerTab == 2) {
                OnboardingData.setSeenServerDiscovery()
            }

            when (EssentialConfig.currentMultiplayerTab) {
                // 0 is already set up by vanilla
                1 -> essentialServerList.updateFriendsServers()
                2 -> essentialServerList.loadFeaturedServers(serverListSeed)
            }

            // If the screen is not being refreshed, it is being opened for the "first time" (i.e. it was
            // on another screen before)
            if (!isRefreshing) {
                sendCurrentTabTelemetry(EssentialConfig.currentMultiplayerTab, true)
            }

            if (shouldShowServerPrivacyModal) {
                GuiUtil.pushModal { manager ->
                    ShareActivityModal(manager).onPrimaryAction {
                        OnboardingData.setSeenFriendsOption()
                        EssentialConfig.sendServerUpdates = true
                    }.onCancel {
                        if (it) {
                            OnboardingData.setSeenFriendsOption()
                            EssentialConfig.sendServerUpdates = false
                        }
                    }
                }
            }

            updateFriendsButton()


            // This is set by MixinGuiMultiplayer#essential$markRefresh, which occurs when the screen is being
            // refreshed. A refresh just re-displays a new instance of the screen, so if we initialized because
            // of that, we have to mark it as false again.
            isRefreshing = false
        }
    }

    class ShareActivityModal(manager: ModalManager) : ConfirmDenyModal(manager, false) {
        init {
            configure {
                titleText = "Share your activity with friends?"
                contentText = "Display the server or world you\n" +
                        "are playing on to your friends in\n" +
                        "the social and multiplayer menu."
                contentTextColor = EssentialPalette.TEXT_MID_GRAY
                primaryButtonText = "Yes"
                cancelButtonText = "No"
            }
        }
    }

    private val oldButtons = mutableListOf<GuiButton>()

    fun setupButtons(
        buttons: List<GuiButton>,
        addButton: (GuiButton) -> GuiButton,
        removeButton: (GuiButton) -> GuiButton
    ) {
        if (!EssentialConfig.essentialFull) return
        val btnSelectServer = screen.acc.btnSelectServer

        fun removeAllButtons() {
            for (button in buttons) {
                removeButton(button)
            }
        }

        fun repositionJoinServerButton(makeSmall: Boolean, newText: String) {
            btnSelectServer.width = if (makeSmall) 75 else 100
            //#if MC==10809
            //$$ btnSelectServer.xPosition = width / 2 - 154
            //$$ btnSelectServer.yPosition = height - 42
            //#else
            btnSelectServer.x = width / 2 - 154
            btnSelectServer.y = height - 42
            //#endif
            //#if MC < 11400
            btnSelectServer.displayString = newText
            //#else
            //$$ btnSelectServer.message = textLiteral(newText)
            //#endif
        }

        when (EssentialConfig.currentMultiplayerTab) {
            0 -> {
                oldButtons.forEach { removeButton(it) }
                oldButtons.clear()
                oldButtons.add(favouritesTabButton)
                oldButtons.add(friendsTabButton)
                oldButtons.add(discoverTabButton)
            }
            1 -> {
                removeAllButtons()
                repositionJoinServerButton(false, "Join Friend")
                addButton(btnSelectServer)
                refreshButton = addButton(makeButton(8, (width shr 1) - 50, height - 42, 100, 20, "Refresh"))
                closeButton = addButton(makeButton(0, (width shr 1) + 54, height - 42, 100, 20, cancelButtonText))
            }
            2 -> {
                fun findButton(id: String) =
                    //#if MC>=11600
                    //$$ buttons.find { it.message == textTranslatable(id) }
                    //#else
                    buttons.find { it.displayString == I18n.format(id) }
                    //#endif

                removeAllButtons()
                addButton(btnSelectServer)
                findButton("selectServer.direct")?.also { addButton(it) }
                findButton("selectServer.add")?.also { addButton(it) }
                addButton(addToFavouritesButton)
                refreshButton = addButton(makeButton(8, width / 2 - 50, height - secondButtonRowYOffset, 100, 20, "Refresh"))
                closeButton = addButton(makeButton(0, width / 2 + 54, height - secondButtonRowYOffset, 100, 20, cancelButtonText))
            }
        }

        addButton(favouritesTabButton)
        addButton(friendsTabButton)
        addButton(discoverTabButton)

        updateButtonState()
    }

    fun updateButtonState() {
        if (!initialized) return
        favouritesTabButton.enabled = EssentialConfig.currentMultiplayerTab != 0
        friendsTabButton.enabled = EssentialConfig.currentMultiplayerTab != 1
        discoverTabButton.enabled = EssentialConfig.currentMultiplayerTab != 2
        addToFavouritesButton.enabled =
            screen.acc.btnSelectServer.enabled
                && EssentialConfig.currentMultiplayerTab == 2
                && screen.acc.serverListSelector.selectedEntry?.let { !essentialServerList.isFavorite(it) } ?: false
    }

    fun onButtonClicked(button: GuiButton) {
        if (!EssentialConfig.essentialFull) return
        if (button.enabled) {
            when (button) {
                closeButton -> screen.ext.close()
                refreshButton -> screen.ext.refresh()
                favouritesTabButton -> switchTab(0)
                friendsTabButton -> withTosAccepted { switchTab(1) }
                discoverTabButton -> withTosAccepted { switchTab(2) }
                addToFavouritesButton -> {
                    val server = screen.acc.serverListSelector.selectedEntry ?: return
                    // Add the server to the favorites (vanilla server list)
                    essentialServerList.addFavorite(server)
                    updateButtonState()

                    // Switch to Favorites tab, select server and scroll to it
                    EssentialConfig.currentMultiplayerTab = 0
                    screen.ext.refresh()
                    val newScreen = UScreen.currentScreen as GuiMultiplayer
                    val newList = newScreen.acc.serverListSelector
                    //#if MC>=11600
                    //$$ val lastServer = newList.eventListeners.lastOrNull { it is NormalEntry }
                    //#if MC>=12109
                    //$$ newScreen.acc.serverListSelector.setSelected(lastServer)
                    //#else
                    //$$ newScreen.func_214287_a(lastServer)
                    //#endif
                    //#if MC>=12104
                    //$$ newList.scrollY = Double.MAX_VALUE
                    //#else
                    //$$ newList.scrollAmount = Double.MAX_VALUE
                    //#endif
                    //#else
                    val index = newScreen.serverList.countServers() - 1
                    newScreen.selectServer(index)
                    newList.scrollBy(newList.slotHeight * index)
                    //#endif
                }
            }
        }
    }

    fun onConnectToServer(serverData: ServerData, ci: CallbackInfo) {
        if (serverData.ext.showDownloadIcon) {
            showDownloadModal(serverData)
            ci.cancel()
            return
        }

        // We only want to send featured server join telemetry when they are joining a server from the featured tab
        if (EssentialConfig.currentMultiplayerTab != 2) {
            return
        }

        val id = connectionManager.knownServersManager.findServerByAddress(serverData.serverIP)?.id ?: return
        connectionManager.telemetryManager.enqueue(
            ClientTelemetryPacket("FEATURED_SERVER_JOIN", mapOf("server" to id))
        )
    }

    fun onClosed() {
        if (!EssentialConfig.essentialEnabled) return
        impressionTracker.submit()
    }

    fun showTooltipString(xPos: Int, yPos: Int, targetWidth: Int, targetHeight: Int, text: String) {
        tooltipHovered = true
        if (tooltipParent.getLeft() != xPos.toFloat() || tooltipParent.getTop() != yPos.toFloat()
            || tooltipParent.getWidth() != targetWidth.toFloat() || tooltipParent.getHeight() != targetHeight.toFloat()
        ) {
            tooltipParent.constrain {
                x = xPos.pixels
                y = yPos.pixels
                width = targetWidth.pixels
                height = targetHeight.pixels
            }
        }
        tooltipText.set(text)
        tooltip.showTooltip()
    }

    fun showDownloadModal(serverData: ServerData) {
        serverData.ext.recommendedVersion?.let { version ->
            GuiUtil.pushModal { VersionDownloadModal(it, version) }
        }
    }

    private fun withTosAccepted(block: () -> Unit) {
        if (!OnboardingData.hasAcceptedTos()) {
            GuiUtil.launchModalFlow {
                ensurePrerequisites()
                block()
            }
        } else {
            block()
        }
    }

    fun draw(drawContext: UDrawContext) {
        if (!EssentialConfig.essentialFull) return

        updateFriendsButton()

        if (essentialServerList.isDiscoverEmpty()
            && !connectionManager.newServerDiscoveryManager.servers.getUntracked().isEmpty()
        ) {
            screen.ext.refresh()
        }

        if (tooltipHovered) {
            tooltipHovered = false
        } else {
            tooltip.hideTooltip()
        }

        //#if MC>=12106
        //$$ AdvancedDrawContext.drawImmediate(drawContext.mc) { matrixStack ->
        //$$     window.draw(matrixStack)
        //$$ }
        //#else
        window.draw(drawContext.matrixStack)
        //#endif
    }

    init {
    }
    fun updateSpsSessions() {
        if (!EssentialConfig.essentialFull) return
        if (EssentialConfig.currentMultiplayerTab == 1) {
            essentialServerList.updateFriendsServers()
        }
    }

    fun updatePlayerActivity(uuid: UUID) {
        if (!EssentialConfig.essentialFull) return
        if (EssentialConfig.currentMultiplayerTab == 1) {
            essentialServerList.updateFriendsServers()
        }
    }

    fun switchTab(tab: Int) {
        EssentialConfig.currentMultiplayerTab = tab
        screen.ext.refresh()

        sendCurrentTabTelemetry(tab, false)
    }

    private fun sendCurrentTabTelemetry(tab: Int, initial: Boolean) {
        val tabName = when (tab) {
            0 -> "FAVORITE"
            1 -> "FRIENDS"
            2 -> "FEATURED"
            else -> "UNKNOWN"
        }

        Essential.getInstance().connectionManager.telemetryManager.enqueue(
            ClientTelemetryPacket(
                "MULTIPLAYER_SERVER_LIST_VIEW",
                mapOf("tab" to tabName, "initial" to initial)
            )
        )
    }

    private fun makeButton(id: Int, x: Int, y: Int, width: Int, height: Int, text: String): GuiButton {
        //#if MC <11400
        return GuiButton(id, x, y, width, height, text)
        //#elseif MC>=11903
        //$$ return ButtonWidget.builder(textLiteral(text)) {
        //$$     onButtonClicked(it)
        //$$ }.dimensions(x, y, width, height).build()
        //#else
        //$$ return Button(x, y, width, height, textLiteral(text)) { onButtonClicked(it) }
        //#endif
    }

    private fun updateFriendsButton() {
        val friendText = "Friends [%d]".format(essentialServerList.getFriendsServers().size)
        //#if MC<=11202
        friendsTabButton.displayString = friendText
        //#else
        //$$ friendsTabButton.message = textLiteral(friendText)
        //#endif
    }

    private val ServerSelectionList.selectedEntry
        //#if MC>=11600
        //$$ get() = (selected as? NormalEntry)?.serverData
        //#else
        get() = if (selected < 0) null else (getListEntry(selected) as? ServerListEntryNormal)?.serverData
        //#endif

    companion object {
        // We need to keep track of if the screen is refreshing for telemetry purposes.
        // `GuiMultiplayer#refresh` will just create a new instance of the screen, so we need to be able to know
        // if that instance is because of a refresh, or because the screen is actually being shown for the first time.
        var isRefreshing = false

        val cancelButtonText =
            //#if MC>=12002
            //$$ "Back"
            //#else
            "Cancel"
            //#endif

        val secondButtonRowYOffset =
            //#if MC>=11904
            //$$ 30
            //#else
            28
            //#endif

        var serverListSeed: Long = 0

        @JvmStatic
        fun getInstance(): EssentialMultiplayerGui? {
            //#if MC >= 26.2
            //$$ @Suppress("SENSELESS_COMPARISON")
            //$$ if (net.minecraft.client.Minecraft.getInstance().gui == null) return null
            //#endif
            return (GuiUtil.openedScreen() as? GuiMultiplayer)?.ext?.essential
        }
    }
}
