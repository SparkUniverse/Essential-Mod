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
import gg.essential.api.gui.Slot
import gg.essential.config.EssentialConfig
import gg.essential.config.FeatureFlags
import gg.essential.data.ABTestingData
import gg.essential.data.OnboardingData
import gg.essential.data.VersionData
import gg.essential.data.VersionInfo
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.constraints.XConstraint
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.coerceAtLeast
import gg.essential.elementa.dsl.coerceAtMost
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.div
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.plus
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.elementa.state.BasicState
import gg.essential.event.essential.InitMainMenuEvent
import gg.essential.event.gui.GuiDrawScreenEvent
import gg.essential.event.gui.GuiOpenEvent
import gg.essential.event.gui.InitGuiEvent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.MenuButton
import gg.essential.gui.common.TextFlag
import gg.essential.gui.common.bindConstraints
import gg.essential.gui.common.or
import gg.essential.gui.elementa.VanillaButtonConstraint.Companion.constrainTo
import gg.essential.gui.elementa.VanillaButtonGroupConstraint.Companion.constrainTo
import gg.essential.gui.elementa.state.v2.await
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toV2
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.menu.AccountManager
import gg.essential.gui.menu.RightSideBarNew
import gg.essential.gui.menu.LeftSideBar
import gg.essential.gui.modals.EssentialAutoInstalledModal
import gg.essential.gui.modals.FeaturesEnabledModal
import gg.essential.gui.modals.UpdateNotificationModal
import gg.essential.gui.modals.connectionManagerErrorModal
import gg.essential.gui.modals.updateAvailableModal
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.notification.toastButton
import gg.essential.gui.overlay.Layer
import gg.essential.gui.overlay.LayerPriority
import gg.essential.gui.proxies.ScreenWithProxiesHandler
import gg.essential.gui.proxies.ScreenWithVanillaProxyElementsExt
import gg.essential.gui.util.addTag
import gg.essential.universal.UMinecraft
import gg.essential.util.AutoUpdate
import gg.essential.util.GuiUtil
import gg.essential.util.findButtonByLabel
import gg.essential.gui.util.pollingState
import gg.essential.network.connectionmanager.serverdiscovery.NewServerDiscoveryManager
import gg.essential.network.connectionmanager.ConnectionManagerStatus
import gg.essential.network.connectionmanager.suspension.suspensionModal
import gg.essential.util.Client
import gg.essential.util.isMainMenu
import gg.essential.vigilance.utils.onLeftClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiScreen
import java.awt.Color
import java.time.Instant
import java.util.*

//#if MC>=11600
//$$ import gg.essential.mixins.transformers.client.gui.GuiScreenAccessor
//$$ import gg.essential.util.textTranslatable
//$$ import net.minecraft.client.gui.screen.MultiplayerWarningScreen
//$$ import net.minecraft.client.gui.widget.Widget
//#endif

class PauseMenuDisplay {

    private val fullRightMenuPixelWidth = 104.pixels
    private val rightMenuMinPadding = 5

    private var layer: Layer? = null
    private var initContent = false
    private var initModals = false

    private var statusCheckJob: CoroutineScope? = null

    private fun initContent(screen: GuiScreen) {
        initContent = true

        if (!isEnabled()) return

        val proxyHandler = (screen as? ScreenWithVanillaProxyElementsExt)?.`essential$getProxyHandler`()
        if (proxyHandler != null) {
            var layer = proxyHandler.layer
            if (layer == null) {
                layer = GuiUtil.createLayer(LayerPriority.AboveScreenContent(screen))
                proxyHandler.layer = layer
                initContent(screen, layer.window, proxyHandler)
            }
            this.layer = layer
            GuiUtil.addLayer(layer)
        } else {
            val window = GuiUtil.addLayer(LayerPriority.AboveScreenContent(screen))
                .also { layer = it }
                .window
            initContent(screen, window, (screen as? ScreenWithVanillaProxyElementsExt)?.`essential$getProxyHandler`())
        }
    }

    fun initContent(screen: GuiScreen, window: Window, proxyHandler: ScreenWithProxiesHandler?) {
        run { // for indent
            window.addTag(MenuButton.WindowSupportsButtonRetexturingMarker)

            val menuType =
                if (screen.isMainMenu) MenuType.MAIN
                else if (UMinecraft.getMinecraft().currentServerData != null) MenuType.SERVER
                else MenuType.SINGLEPLAYER

            // Create containers around the top and bottom buttons, so we can use them for GUI alignment
            val topButtonGetter = screen.findButtonByLabel("menu.singleplayer", "menu.returnToGame")
            // topButtonAndMultiplayer is used to calculate x positioning for when single and multiplayer buttons
            //  are side by side
            val topButtonAndMultiplayer by UIContainer().constrainTo(
                listOf(topButtonGetter, screen.findButtonByLabel("menu.multiplayer"))
            ) {
                x = CenterConstraint()
                y = 25.percent + 48.pixels
                width = 200.pixels
                height = 20.pixels
            } childOf window

            val topButton by UIContainer().constrainTo(listOf(topButtonGetter)) {
                x = CenterConstraint()
                y = 25.percent + 48.pixels
                width = 200.pixels
                height = 20.pixels
            } childOf window

            val bottomButton by UIContainer().constrainTo(
                screen.findButtonByLabel("menu.quit", "menu.returnToMenu", "menu.disconnect", "replaymod.gui.exit")
            ) {
                x = CenterConstraint()
                y = SiblingConstraint(64f)
                width = 200.pixels
                height = 20.pixels
            } childOf window

            val isCompact = BasicState(EssentialConfig.essentialMenuLayout == EssentialConfig.EssentialMenuLayout.MINIMAL) or bottomButton.pollingState {
                    getRightSideMenuX(window, topButtonAndMultiplayer, fullRightMenuPixelWidth).getXPosition(window) +
                        fullRightMenuPixelWidth.value + rightMenuMinPadding >= window.getRight()
                }

            val rightContainer by UIContainer().constrain {
                height = ChildBasedMaxSizeConstraint()
            }.bindConstraints(isCompact) { isCompact ->
                if (isCompact) {
                    x = (13.pixels(alignOpposite = true) boundTo window)
                            .coerceAtLeast((0.pixels(alignOpposite = true) boundTo topButtonAndMultiplayer) + 24.pixels).coerceAtMost(rightMenuMinPadding.pixels(alignOpposite = true) boundTo window)
                    width = ChildBasedSizeConstraint()
                } else {
                    width = fullRightMenuPixelWidth
                    x = getRightSideMenuX(window, topButtonAndMultiplayer, width).coerceAtMost(rightMenuMinPadding.pixels(alignOpposite = true) boundTo window)
                }
                y = (((CenterConstraint() boundTo bottomButton) + (CenterConstraint() boundTo topButton)) / 2)
                    .coerceAtMost(16.pixels(alignOpposite = true) boundTo window)
                    .coerceAtLeast(4.pixels)
            } childOf window

            val leftContainer by UIContainer().constrain {
                width = 50.percent
                height = 100.percent
            } childOf window

            val accountManager = AccountManager()
            RightSideBarNew(menuType, proxyHandler, isCompact.toV2(), accountManager) childOf rightContainer

            LeftSideBar(window, proxyHandler, topButtonAndMultiplayer, bottomButton, rightContainer, leftContainer) childOf leftContainer

            if (menuType == MenuType.MAIN
                && Instant.now() < NewServerDiscoveryManager.NEW_TAG_END_DATE
                && !OnboardingData.seenServerDiscovery.getUntracked()
            ) {
                val multiplayerButton = UIContainer().constrainTo(
                    screen.findButtonByLabel("menu.multiplayer")
                ) {
                    x = CenterConstraint()
                    y = 25.percent + 52.pixels
                    width = 200.pixels
                    height = 20.pixels
                } childOf window

                TextFlag(
                    stateOf(MenuButton.NOTICE_GREEN),
                    MenuButton.Alignment.CENTER,
                    stateOf("NEW")
                ).constrain {
                    y = CenterConstraint() boundTo multiplayerButton
                    x = 3.pixels(alignOpposite = true, alignOutside = true) boundTo multiplayerButton
                }.onLeftClick {
                    if (OnboardingData.hasAcceptedTos()) {
                        EssentialConfig.currentMultiplayerTab = 2
                    }
                    //#if MC>=11600
                    //$$ if (UMinecraft.getMinecraft().gameSettings.skipMultiplayerWarning) {
                    //$$     GuiUtil.openScreen { MultiplayerScreen(screen) }
                    //$$ } else {
                    //$$     GuiUtil.openScreen { MultiplayerWarningScreen(screen) }
                    //$$ }
                    //#else
                    GuiUtil.openScreen { GuiMultiplayer(screen) }
                    //#endif
                } childOf window
            }
        }
    }

    private fun initModals(screen: GuiScreen) {
        initModals = true

        if (screen.isMainMenu) {
            // only triggers a modal currently
            Essential.EVENT_BUS.post(InitMainMenuEvent())
        }

        EssentialAutoInstalledModal.showModal()

        // Update available toast
        if (AutoUpdate.updateAvailable.get() && !AutoUpdate.seenUpdateToast && !AutoUpdate.updateIgnored.get()) {
            fun showUpdateToast(message: String? = null) {
                var updateClicked = false

                val updateButton = toastButton("Install",
                    backgroundModifier = Modifier.color(EssentialPalette.GREEN_BUTTON)
                        .hoverColor(EssentialPalette.GREEN_BUTTON_HOVER)
                        .shadow(Color.BLACK),
                    textModifier = Modifier.color(EssentialPalette.TEXT_HIGHLIGHT)
                        .shadow(EssentialPalette.TEXT_SHADOW)
                )

                Notifications.pushPersistentToast(AutoUpdate.getNotificationTitle(false), message ?: " ", {
                    GuiUtil.launchModalFlow { updateAvailableModal() }
                }, {
                    if (!updateClicked) {
                        AutoUpdate.ignoreUpdate()
                    }
                }, {
                    withCustomComponent(Slot.ACTION, updateButton)
                    withCustomComponent(Slot.ICON, EssentialPalette.DOWNLOAD_7X8.create())
                    trimMessage = true
                    AutoUpdate.dismissUpdateToast = {
                        updateClicked = true
                        dismissNotification()
                    }
                })
            }

            AutoUpdate.changelog.whenCompleteAsync({ changelog, _ -> showUpdateToast(changelog) }, Window::enqueueRenderOperation)

            AutoUpdate.seenUpdateToast = true
        }

        // Suspension modal
        Essential.getInstance().connectionManager.suspensionManager.activeSuspension.getUntracked()?.let { suspension ->
            if (suspension.unseen) {
                GuiUtil.launchModalFlow {
                    suspensionModal(suspension)
                }
            }
        }

        // Update Notification Modal
        if (VersionData.getMajorComponents(VersionData.essentialVersion) != VersionData.getMajorComponents(VersionData.getLastSeenModal())
            && (EssentialConfig.updateModal)
        ) {
            if (VersionData.getLastSeenModal() == VersionInfo.noSavedVersion) {
                // If first launch, update last seen modal and don't show changelog
                VersionData.updateLastSeenModal()
            } else {
                // TODO: Replace with new announcement modal for news and alerts
                GuiUtil.queueModal(UpdateNotificationModal(GuiUtil))
            }
        }

        // AB Features Enabled Modal
        if (FeatureFlags.abTestingFlags
                .filterValues { featureData -> featureData.second }
                .filterKeys { name -> !ABTestingData.hasData("Notified:$name") }
                .isNotEmpty()
        ) {
            GuiUtil.queueModal(FeaturesEnabledModal(GuiUtil))
        }

        if (statusCheckJob == null) {
            statusCheckJob = CoroutineScope(Dispatchers.Client).also { scope ->
                scope.launch {
                    val status = Essential.getInstance().connectionManager.connectionStatus.await { it != null }
                    when (status) {
                        is ConnectionManagerStatus.Error.GeneralFailure -> {
                            Notifications.error(
                                "Essential Network Error",
                                "Unable to establish connection with the Essential Network",
                            ) {
                                withCustomComponent(Slot.ACTION, toastButton("Help") {
                                    GuiUtil.launchModalFlow { connectionManagerErrorModal(stateOf(status)) }
                                })
                            }
                        }

                        is ConnectionManagerStatus.Error -> {
                            GuiUtil.launchModalFlow { connectionManagerErrorModal(stateOf(status)) }
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    @Subscribe
    fun guiOpen(event: GuiOpenEvent) {
        refresh()
    }

    @Subscribe
    fun guiInit(event: InitGuiEvent) {
        // re init so that our buttons can re-attach to the proxy vanilla buttons on screen resize
        // this massively simplifies re-attachment to the [EssentialProxyElement]'s and syncs our button lifecycles with those of vanilla

        // same as refresh() but only for screen content
        layer?.let { GuiUtil.removeLayer(it) }
        layer = null
        initContent = false
    }

    @Subscribe
    fun drawScreen(event: GuiDrawScreenEvent) {
        val screen = event.screen
        if (screen !is GuiIngameMenu && !screen.isMainMenu) {
            // If the user navigates away from the main menu, we don't want to show them any error modals
            // or toasts until they launch the game next.
            // To prevent any pending statuses from appearing, we should cancel the coroutine scope.
            statusCheckJob?.cancel()
            return
        }

        //#if MC>=11600
        //$$ if (screen is IngameMenuScreen && screen.title == textTranslatable("menu.paused")) {
        //$$     return // F3+Esc
        //$$ }
        //#endif

        if (!initContent) {
            initContent(screen)
        }

        if (!initModals) {
            initModals(screen)
        }
    }

    fun refresh() {
        layer?.let { GuiUtil.removeLayer(it) }
        layer = null
        initContent = false
        initModals = false
    }

    private fun getRightSideMenuX(window: Window, topButton: UIContainer, width: WidthConstraint): XConstraint {
        return run {
            // Keep right menu in the of middle the vanilla buttons and right side of the screen
            //  (Right menu buttons are aligned to the right with extra space to the left so remove that extra space when aligning)
            //  with some padding between it and the vanilla buttons
            ((SiblingConstraint() boundTo topButton) - (width - RightSideBarNew.BUTTON_WIDTH.pixels) +
                    (((0.pixels(alignOpposite = true) boundTo window) - (0.pixels(alignOpposite = true) boundTo topButton)) / 2f - (RightSideBarNew.BUTTON_WIDTH.pixels / 2)))
                .coerceAtLeast(SiblingConstraint(28f) boundTo topButton)
        }
    }

    companion object {
        @JvmStatic
        val minWidth = 404

        @JvmStatic
        fun isEnabled(): Boolean {
            return EssentialConfig.essentialEnabled && EssentialConfig.essentialMenuLayout != EssentialConfig.EssentialMenuLayout.OFF
        }

        @JvmStatic
        fun canRescale(screen: GuiScreen): Boolean {
            return (screen.isMainMenu || screen is GuiIngameMenu)
        }
    }

    enum class MenuType { MAIN, SINGLEPLAYER, SERVER }
}
