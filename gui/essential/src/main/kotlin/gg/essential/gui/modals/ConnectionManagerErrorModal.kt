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
package gg.essential.gui.modals

import gg.essential.minecraftauth.exception.AuthenticationException
import gg.essential.minecraftauth.exception.MicrosoftAuthenticationException
import gg.essential.minecraftauth.exception.MinecraftAuthenticationException
import gg.essential.minecraftauth.exception.XboxLiveAuthenticationException
import gg.essential.minecraftauth.xbox.response.XboxLiveErrorCode
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.common.outlineButton
import gg.essential.gui.common.textStyle
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.image
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.network.connectionmanager.ConnectionManagerStatus
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.openInBrowser
import gg.essential.vigilance.utils.onLeftClick
import org.slf4j.LoggerFactory
import java.awt.Color
import java.net.URI

class ConnectionManagerErrorModal(
    modalManager: ModalManager,
    status: State<ConnectionManagerStatus?>,
    continuation: ModalFlow.ModalContinuation<Unit>,
) : EssentialModal2(modalManager) {
    private class StatusContent(val title: String, val message: String, val buttons: LayoutScope.() -> Unit)

    @Suppress("JoinDeclarationAndAssignment") // having it in `init` is cleaner, looks weird up here.
    private val statusEffectCleanup: () -> Unit

    private var lastErrorStatus: ConnectionManagerStatus? = null
    private val content: State<StatusContent> = memo { contentForStatus((status() ?: lastErrorStatus)?.also { lastErrorStatus = it }) }

    init {
        statusEffectCleanup = effect(ReferenceHolder.Weak) {
            val status = status()
            if (status == null || status is ConnectionManagerStatus.Error) {
                return@effect
            }

            // If the status is no longer the "error" status, we should close the modal.
            replaceWith(continuation.resumeImmediately(Unit))
        }
    }

    override fun onClose() {
        statusEffectCleanup()

        super.onClose()
    }

    override fun LayoutScope.layoutTitle() {
        text({ content().title }, Modifier.color(EssentialPalette.RED).shadow(Color.BLACK))
    }

    override fun LayoutScope.layoutBody() {
        wrappedText(
            { content().message },
            Modifier.color(EssentialPalette.TEXT).shadow(Color.BLACK),
            centered = true,
        )
    }

    override fun LayoutScope.layoutButtons() {
        row(Arrangement.spacedBy(8f)) {
            bind({ content().buttons }) { content -> content() }
        }
    }

    private fun contentForStatus(status: ConnectionManagerStatus?): StatusContent {
        val genericAuthenticationFailure = StatusContent(
            "Account authentication failed",
            """
                We couldn't authenticate your
                Microsoft account. Try again later or
                get help with our wiki.
            """.trimIndent(),
        ) {
            cancelButton("Ignore")
            linkButton("Get help", URI.create("https://essential.gg/wiki/other-error-messages#account-authentication-failed-error"))
        }

        val genericFailure = StatusContent(
            "Essential connection error",
            """
                Unable to establish connection
                with the Essential Network.
            """.trimIndent(),
        ) {
            cancelButton("Ignore")
            linkButton("View status", URI.create("https://status.essential.gg/"))
        }

        // This may occur during transitions between states, the user won't see anything for too long.
        if (status !is ConnectionManagerStatus.Error) {
            return genericFailure
        }

        return when (status) {
            ConnectionManagerStatus.Error.GeneralFailure -> genericFailure

            ConnectionManagerStatus.Error.HostsFileModified -> StatusContent(
                "Your 'hosts' file is hijacked",
                """
                    You are redirecting traffic from
                    Mojang's authentication servers to
                    somewhere else. Reverse the
                    changes made to your game.
                """.trimIndent(),
            ) {
                cancelButton("Ignore")
                linkButton(
                    "View guide",
                    URI.create("https://essential.gg/wiki/hijacked-hosts")
                )
            }

            ConnectionManagerStatus.Error.DNSFailure -> StatusContent(
                "Your network setup has an issue",
                """
                    Your current network setup
                    can’t establish a connection
                    with the Essential network.
                """.trimIndent(),
            ) {
                cancelButton("Ignore")
                linkButton(
                    "View guide",
                    URI.create("https://essential.gg/wiki/network-setup-issue")
                )
            }

            is ConnectionManagerStatus.Error.AuthenticationFailure -> when (val throwable = status.throwable) {
                is AuthenticationException.InvalidResponse -> genericAuthenticationFailure

                is AuthenticationException.InvalidCredentials -> StatusContent(
                    "Your account was logged out",
                    """
                        We were not able to authenticate
                        your Minecraft Account. Please login
                        to your account again.
                    """.trimIndent(),
                ) {
                    cancelButton("Ignore")
                    linkButton("Login") { platform.openWebAccountManager() }
                }

                is AuthenticationException.Ratelimited -> StatusContent(
                    "Your account is ratelimited",
                    """
                        Wait a little bit before trying again.
                        If the issue persists, please
                        contact our support.
                    """.trimIndent(),
                ) {
                    cancelButton("Ignore")
                    linkButton("Get help", URI.create("https://essential.gg/discord"))
                }

                is MinecraftAuthenticationException.ProfileNotFound, is XboxLiveAuthenticationException.MissingXboxLiveClaims -> StatusContent(
                    "You don't own Minecraft",
                    """
                        Make sure you are signed into
                        a Microsoft account that owns
                        Minecraft Java Edition.
                    """.trimIndent(),
                ) {
                    primaryButton("Okay") { close() }
                }

                is MinecraftAuthenticationException.InsufficientPrivileges -> StatusContent(
                    "Your Xbox multiplayer is disabled",
                    """
                        Playing Minecraft together
                        requires your Xbox Account
                        to have Multiplayer enabled.
                    """.trimIndent(),
                ) {
                    cancelButton("Ignore")
                    linkButton(
                        "View guide",
                        URI.create("https://essential.gg/wiki/enable-multiplayer-on-xbox-account")
                    )
                }

                is XboxLiveAuthenticationException.XboxLiveError -> when (throwable.error) {
                    XboxLiveErrorCode.NoXboxLiveAccount -> StatusContent(
                        "You don't own Minecraft",
                        """
                            Make sure you are signed into
                            a Microsoft account that owns
                            Minecraft Java Edition.
                        """.trimIndent(),
                    ) {
                        primaryButton("Okay") { close() }
                    }

                    XboxLiveErrorCode.XboxLiveBannedInCountry -> StatusContent(
                        "Xbox Live is banned in your country",
                        """
                            Minecraft multiplayer and Essential
                            are unavailable due to an
                            Xbox Live ban in your country.
                        """.trimIndent(),
                    ) {
                        primaryButton("Okay") { close() }
                    }

                    XboxLiveErrorCode.AccountIsAChild -> StatusContent(
                        "Missing multiplayer permission",
                        """
                            Your guardian needs to add
                            your account to a family group
                            to play multiplayer games.
                        """.trimIndent(),
                    ) {
                        cancelButton("Ignore")
                        linkButton(
                            "View guide",
                            URI.create("https://essential.gg/wiki/family-group")
                        )
                    }

                    XboxLiveErrorCode.MultiplayerNotAllowed , XboxLiveErrorCode.RequiresAdultVerification -> StatusContent(
                        "Missing multiplayer permission",
                        """
                            Your guardian needs to grant
                            you permission to access
                            multiplayer games.
                        """.trimIndent(),
                    ) {
                        cancelButton("Ignore")
                        linkButton(
                            "View guide",
                            URI.create("https://essential.gg/wiki/enable-multiplayer-on-xbox-account")
                        )
                    }

                    XboxLiveErrorCode.TermsOfServiceNotAccepted -> StatusContent(
                        "You haven't accepted Xbox ToS",
                        """
                            Log into xbox.com to accept
                            their terms of service.
                        """.trimIndent(),
                    ) {
                        cancelButton("Ignore")
                        linkButton("xbox.com", URI.create("https://xbox.com"))
                    }

                    XboxLiveErrorCode.AccountIsBannedFromXbox -> StatusContent(
                        "You are banned from Xbox Live",
                        """
                            Minecraft multiplayer and Essential
                            are unavailable as you are
                            banned from Xbox Live.
                        """.trimIndent(),
                    ) {
                        primaryButton("Okay") { close() }
                    }

                    is XboxLiveErrorCode.Unknown -> genericAuthenticationFailure
                }

                is MicrosoftAuthenticationException, is MinecraftAuthenticationException.Failed -> genericAuthenticationFailure

                else -> {
                    LOGGER.error("Unknown authentication exception", status.throwable)
                    genericAuthenticationFailure
                }
            }
        }
    }

    private fun LayoutScope.linkButton(text: String, destination: URI) {
        linkButton(text) { openInBrowser(destination) }
    }

    private fun LayoutScope.linkButton(text: String, action: () -> Unit) {
        outlineButton(
            Modifier.width(91f).onLeftClick {
                USound.playButtonPress()
                action()
            },
            style = OutlineButtonStyle.BLUE,
        ) { style ->
            row(Arrangement.spacedBy(5f)) {
                text(text, Modifier.textStyle(style))
                image(EssentialPalette.ARROW_UP_RIGHT_5X5, Modifier.textStyle(style))
            }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ConnectionManagerErrorModal::class.java)
    }
}

suspend fun ModalFlow.connectionManagerErrorModal(status: State<ConnectionManagerStatus?>) =
    awaitModal { continuation ->
        ConnectionManagerErrorModal(
            modalManager,
            status,
            continuation = continuation
        )
    }