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
package gg.essential.gui.menu

import gg.essential.Essential
import gg.essential.config.McEssentialConfig
import gg.essential.data.VersionData
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.common.IconFlag
import gg.essential.gui.common.MenuButton
import gg.essential.gui.common.TextFlag
import gg.essential.gui.common.state
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.zip
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.friends.SocialMenu
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedMaxWidth
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.hoverTooltip
import gg.essential.gui.layoutdsl.layoutAsColumn
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.then
import gg.essential.gui.modals.updateAvailableModal
import gg.essential.gui.proxies.ProxiesHandler
import gg.essential.gui.proxies.mountWithProxy
import gg.essential.gui.screenshot.components.ScreenshotBrowser
import gg.essential.gui.sps.launchInviteOrHostModalFlow
import gg.essential.gui.util.pollingStateV2
import gg.essential.gui.wardrobe.Wardrobe
import gg.essential.handlers.PauseMenuDisplay
import gg.essential.sps.SpsAddress
import gg.essential.universal.UMinecraft
import gg.essential.util.AutoUpdate
import gg.essential.util.CachedAvatarImage
import gg.essential.util.GuiUtil
import gg.essential.util.USession
import gg.essential.vigilance.utils.onLeftClick

class RightSideBarNew(
    menuType: PauseMenuDisplay.MenuType,
    private val proxyHandler: ProxiesHandler,
    private val isMinimal: State<Boolean>,
    private val accountManager: AccountManager,
) : UIContainer() {

    val connectionManager = Essential.getInstance().connectionManager
    private val isMinimalV1 = isMinimal.toV1(this)
    private val isHostingWorld = pollingStateV2 {
        Essential.getInstance().worldsManager.integratedServerWorld.getUntracked()?.localWorldOpen?.getUntracked() ?: false
    }

    private val hostable = menuType == PauseMenuDisplay.MenuType.MAIN
            || (menuType == PauseMenuDisplay.MenuType.SINGLEPLAYER)

    private val hasInviteButton = memo {
        val currentServer = UMinecraft.getMinecraft().currentServerData
        val isSpsServer = currentServer?.let { SpsAddress.parse(it.serverIP) } != null
        (!hostable && !isSpsServer)
    }

    private val messageCount = connectionManager.chatManager.unreadMessageCount
        .zip(connectionManager.socialMenuNewFriendRequestNoticeManager.unseenFriendRequestCount())
        .map { (messages, friends) -> messages + friends }
        .map {
            if (it > 98) "99+" else it.toString()
        }

    private fun LayoutScope.buttonPlaceholder() {
        if_(isMinimal) {
            spacer(20f, 20f)
        } `else` {
            spacer(BUTTON_WIDTH, 20f)
        }
    }

    private fun LayoutScope.flagPlaceholder() {
        spacer(14f, 14f)
    }

    private val hostableOrHasInviteButton = memo { hostable || hasInviteButton() }

    init {
        layoutAsColumn(
            Modifier.childBasedMaxWidth().childBasedHeight().alignHorizontal(Alignment.End),
            Arrangement.spacedBy(4f),
            Alignment.End
        ) {
            if (true) { // for indent, may be removed when convenient
                inviteOrHostButton()
                row(Arrangement.spacedBy(3f)) {
                    messageFlag()
                    socialButton()
                }
                wardrobeButton()
                picturesButton()
                row(Arrangement.spacedBy(3f)) {
                    betaFlag()
                    updateFlag()
                    settingsButton()
                }
                if (menuType == PauseMenuDisplay.MenuType.MAIN) {
                    accountButton()
                }
                if_({ !hostableOrHasInviteButton() }) {
                    buttonPlaceholder() // offset the invite/host button placeholder if it is not visible
                }
            }
        }
    }

    private fun LayoutScope.betaFlag() {
        mountWithProxy(proxyHandler, "beta") {
            if_({ VersionData.essentialBranch != "stable" }) {
                TextFlag(
                    stateOf(MenuButton.LIGHT_RED),
                    MenuButton.Alignment.CENTER,
                    stateOf("BETA"),
                ).onLeftClick {
                    GuiUtil.openScreen { McEssentialConfig.gui() }
                }(Modifier.hoverTooltip("Branch: ${VersionData.essentialBranch}", position = EssentialTooltip.Position.ABOVE)
                    .hoverScope())
            } `else` {
                flagPlaceholder()
            }
        }
    }

    private fun LayoutScope.updateFlag() {
        mountWithProxy(proxyHandler, "update") {
            if_(AutoUpdate.updateAvailable) {
                IconFlag(stateOf(MenuButton.NOTICE_GREEN), EssentialPalette.DOWNLOAD_7X8).onLeftClick {
                    GuiUtil.launchModalFlow { updateAvailableModal() }
                }(Modifier.hoverScope().then(State {
                    val position = if (isMinimal()) EssentialTooltip.Position.LEFT else EssentialTooltip.Position.ABOVE
                    Modifier.hoverTooltip("Update Available!", position = position)
                }).hoverScope())
            } `else` {
                flagPlaceholder()
            }
        }
    }

    private fun LayoutScope.inviteButton() {
        MenuButton("Invite", textAlignment = MenuButton.Alignment.LEFT) {
            hostOrInviteButtonPressed()
        }.constrain {
            width = BUTTON_WIDTH.pixels
            height = 20.pixels
        }.setIcon(EssentialPalette.ENVELOPE_9X7.state(), rightAligned = true, xOffset = -1f)
            .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Invite"))
    }

    private fun LayoutScope.hostButton() {
        MenuButton("Host", textAlignment = MenuButton.Alignment.LEFT) {
            hostOrInviteButtonPressed()
        }.constrain {
            width = BUTTON_WIDTH.pixels
            height = 20.pixels
        }.setIcon(EssentialPalette.HOST_5X.state(), rightAligned = true, xOffset = -3f, yOffset = -1f)
            .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Host"))
    }

    private fun LayoutScope.inviteOrHostButton() {
        mountWithProxy(proxyHandler, "invite_host") {
            if_({ hostableOrHasInviteButton() && hasInviteButton() }) {
                inviteButton()
            } `else` {
                if_(hostableOrHasInviteButton) {
                    hostButton()
                } `else` {
                    buttonPlaceholder()
                }
            }
        }
    }

    private fun socialButtonPressed() {
        GuiUtil.openScreen { SocialMenu() }
    }

    private fun LayoutScope.socialButton() {
        mountWithProxy(proxyHandler, "social") {
            MenuButton(BasicState("Social"), textAlignment = MenuButton.Alignment.LEFT) {
                socialButtonPressed()
            }.constrain {
                width = BUTTON_WIDTH.pixels
                height = 20.pixels
            }.setIcon(BasicState(EssentialPalette.SOCIAL_10X), rightAligned = true, xOffset = -1f)
                .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Social"))
        }
    }

    private fun LayoutScope.messageFlag() {
        mountWithProxy(proxyHandler, "message",) {
            if_({ messageCount() != "0" }) {
                TextFlag(
                    stateOf(MenuButton.LIGHT_RED),
                    MenuButton.Alignment.CENTER,
                    messageCount,
                ).onLeftClick { socialButtonPressed() }()
            } `else` {
                flagPlaceholder()
            }
        }
    }

    private fun LayoutScope.wardrobeButton() {
        mountWithProxy(proxyHandler, "wardrobe") {
            MenuButton("Wardrobe", textAlignment = MenuButton.Alignment.LEFT) {
                GuiUtil.openScreen { Wardrobe() }
            }.constrain {
                width = BUTTON_WIDTH.pixels
                height = 20.pixels
            }.setIcon(BasicState(EssentialPalette.COSMETICS_10X7), rightAligned = true, xOffset = -1f)
                .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Wardrobe"))
        }
    }

    private fun LayoutScope.picturesButton() {
        mountWithProxy(proxyHandler, "pictures") {
            MenuButton(BasicState("Pictures"), textAlignment = MenuButton.Alignment.LEFT) {
                GuiUtil.openScreen { ScreenshotBrowser() }
            }.constrain {
                width = BUTTON_WIDTH.pixels
                height = 20.pixels
            }.setIcon(BasicState(EssentialPalette.PICTURES_10X10), rightAligned = true, xOffset = -1f, yOffset = 2f)
                .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Pictures"))
        }
    }

    private fun LayoutScope.settingsButton() {
        mountWithProxy(proxyHandler, "settings") {
            MenuButton(BasicState("Settings"), textAlignment = MenuButton.Alignment.LEFT) {
                GuiUtil.openScreen { McEssentialConfig.gui() }
            }.constrain {
                width = BUTTON_WIDTH.pixels
                height = 20.pixels
            }.setIcon(BasicState(EssentialPalette.SETTINGS_9X8), rightAligned = true, xOffset = -1f, yOffset = 1f)
                .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Settings"))
        }
    }

    private fun LayoutScope.accountButton() {
        mountWithProxy(proxyHandler, "account") {
            MenuButton("Account", textAlignment = MenuButton.Alignment.LEFT) {
                GuiUtil.pushModal { AccountManagerModal(it, accountManager) }
            }.constrain {
                width = BUTTON_WIDTH.pixels
                height = 20.pixels
            }.setIcon({
                bind(USession.active) { active ->
                    CachedAvatarImage.create(active.uuid)(Modifier.shadow(EssentialPalette.TEXT_SHADOW))
                }
            }, rightAligned = true, xOffset = -1f)
                .bindCollapsed(isMinimalV1, 20f)(Modifier.buttonTooltip("Account"))
        }
    }

    private fun Modifier.buttonTooltip(content: State<String>): Modifier {
        return hoverScope().then(State {
            if (isMinimal()) {
                Modifier.hoverTooltip(content, position = EssentialTooltip.Position.LEFT)
            } else {
                Modifier
            }
        })
    }

    private fun Modifier.buttonTooltip(content: String) = buttonTooltip(stateOf(content))

    companion object {

        const val BUTTON_WIDTH = 80f

        fun hostOrInviteButtonPressed() {
            launchInviteOrHostModalFlow()
        }

    }

}
