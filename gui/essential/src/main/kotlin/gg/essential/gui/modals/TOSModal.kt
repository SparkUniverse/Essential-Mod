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

import gg.essential.data.OnboardingData
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.common.textStyle
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.await
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.checkboxAlt
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.tag
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.underline
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.util.focusable
import gg.essential.network.connectionmanager.ConnectionManagerStatus
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.openInBrowser
import gg.essential.vigilance.utils.onLeftClick
import java.net.URI

class TOSModal(
    modalManager: ModalManager,
    private val requiresAuth: Boolean = false,
    private val continuation: ModalFlow.ModalContinuation<Boolean>
) : EssentialModal2(modalManager, true) {

    private val ageCheckbox = mutableStateOf(false)
    private val termsAndPrivacyCheckbox = mutableStateOf(false)

    override fun LayoutScope.layoutContent(modifier: Modifier) {
        layoutContentImpl(modifier.width(305f))
    }

    override fun LayoutScope.layoutTitle() {
        title("Welcome to Essential Mod", Modifier.color(EssentialPalette.MODAL_TITLE_BLUE))
    }

    override fun LayoutScope.layoutBody() {
        column(Modifier.fillWidth(), Arrangement.spacedBy(15f)) {
            wrappedText(
                "We care about your safety and privacy.\n" +
                        "Please confirm your age and accept our terms.", centered = true
            )
            column(Arrangement.spacedBy(9f), Alignment.Start) {
                row(Arrangement.spacedBy(5f), Alignment.End) {
                    checkboxAlt(ageCheckbox)
                    text("I'm 13+ with parental consent, or 18+", Modifier.color(EssentialPalette.TEXT_MID_GRAY)).onLeftClick {
                        ageCheckbox.set { !it }
                    }
                }
                row(Arrangement.spacedBy(5f)) {
                    checkboxAlt(termsAndPrivacyCheckbox, Modifier.alignVertical(Alignment.Start))
                    column {
                        spacer(height = 1f)
                        wrappedText(
                            "I accept {tos} & {privacy}",
                            textModifier = Modifier.color(EssentialPalette.TEXT_MID_GRAY)
                        ) {
                            "tos" {
                                box {
                                    text("Terms of Use", Modifier.underline().color(EssentialPalette.TEXT_MID_GRAY).hoverColor(EssentialPalette.TEXT_HIGHLIGHT).hoverScope())
                                }.onLeftClick { event ->
                                    USound.playButtonPress()
                                    event.stopPropagation()
                                    openInBrowser(URI("https://essential.gg/terms-of-use"))
                                }
                            }
                            "privacy" {
                                box {
                                    text("Privacy Policy", Modifier.underline().color(EssentialPalette.TEXT_MID_GRAY).hoverColor(EssentialPalette.TEXT_HIGHLIGHT).hoverScope())
                                }.onLeftClick { event ->
                                    USound.playButtonPress()
                                    event.stopPropagation()
                                    openInBrowser(URI("https://essential.gg/privacy-policy"))
                                }
                            }
                        }
                    }.onLeftClick {
                        termsAndPrivacyCheckbox.set { !it }
                    }
                }
            }
        }
    }

    override fun LayoutScope.layoutButtons() {
        row(Arrangement.spacedBy(8f)) {
            val isConnecting = mutableStateOf(false)
            cancelButton(isConnecting)
            agreeButton(isConnecting)
        }
    }

    private fun LayoutScope.agreeButton(isConnecting: MutableState<Boolean>) {
        outlineButton(
            Modifier
                .width(91f)
                .shadow()
                .tag(PrimaryAction),
            style = { OutlineButtonStyle.BLUE },
            disabled = { isConnecting() || !ageCheckbox() || !termsAndPrivacyCheckbox() },
            action = {
                OnboardingData.setAcceptedTos()

                if (requiresAuth && !platform.cmConnection.isOpen) {
                    isConnecting.set(true)
                }
                replaceWith(continuation.resume(true))
            },
        ) { currentStyle ->
            text({ if (isConnecting()) "Connecting..." else "Agree" }, Modifier.textStyle(currentStyle))
        }
    }

    private fun LayoutScope.cancelButton(isConnecting: State<Boolean>) {
        outlineButton(
            Modifier
                .width(91f)
                .shadow()
                .focusable(),
            disabled = isConnecting,
            action = {
                replaceWith(continuation.resume(false))
            }
        ) { style ->
            text("Cancel", Modifier.textStyle(style))
        }
    }

}

suspend fun ModalFlow.tosModal(connectionStatus: State<ConnectionManagerStatus?>): Boolean {
    val acceptedTos = awaitModal { continuation ->
        TOSModal(
            modalManager,
            requiresAuth = true,
            continuation = continuation,
        )
    }
    if (acceptedTos) {
        OnboardingData.setAcceptedTos()

        if (!platform.cmConnection.isOpen) {
            val status = connectionStatus
                .letState { status ->
                    if (status == ConnectionManagerStatus.TOSNotAccepted) return@letState null
                    if (status != ConnectionManagerStatus.Success) return@letState status
                    if (!platform.suspensionManager.isLoaded()) return@letState null
                    if (!platform.rulesManager.isLoaded()) return@letState null
                    ConnectionManagerStatus.Success
                }.await { it != null }

            when {
                status == ConnectionManagerStatus.Outdated -> updateRequiredModal()
                status != ConnectionManagerStatus.Success -> connectionManagerErrorModal(connectionStatus)
            }
        }
    }
    return acceptedTos
}
