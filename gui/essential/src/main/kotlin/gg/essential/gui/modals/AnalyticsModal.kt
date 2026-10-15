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

import gg.essential.config.EssentialConfig
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.font
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.icon
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.universal.USound
import gg.essential.util.ESSENTIAL_ANALYTICS_AND_DATA_SHARING_URI
import gg.essential.util.openInBrowser

class AnalyticsModal(
    modalManager: ModalManager,
    private val continuation: ModalFlow.ModalContinuation<Boolean>
) : EssentialModal2(modalManager, true) {

    override fun LayoutScope.layoutContent(modifier: Modifier) {
        // FIXME: The MINECRAFT_TEN font has a huge amount of padding underneath it, this is just accounting for that.
        layoutContentImpl(modifier.width(274f), Arrangement.spacedBy(12f))
    }

    override fun LayoutScope.layoutTitle() {
        title("Analytics & Improvements", Modifier.color(EssentialPalette.MODAL_TITLE_BLUE).font(EssentialPalette.MINECRAFT_TEN))
    }

    override fun LayoutScope.layoutBody() {
        column(Modifier.fillWidth(), Arrangement.spacedBy(16f)) {
            wrappedText(
                "Help us improve Essential’s features\nby sharing usage data.",
                lineSpacing = 10f,
                centered = true,
                modifier = Modifier.shadow(EssentialPalette.BLACK),
            )

            val linkModifier = Modifier.color(EssentialPalette.TEXT).shadow(EssentialPalette.BLACK).hoverColor(EssentialPalette.WHITE)

            column(Modifier.hoverScope().onLeftClick { event ->
                USound.playButtonPress()
                event.stopPropagation()
                openInBrowser(ESSENTIAL_ANALYTICS_AND_DATA_SHARING_URI)
            }) {
                val textComponent = wrappedText("Learn more about analytics {icon}", textModifier = linkModifier) {
                    "icon" {
                        icon(EssentialPalette.ARROW_UP_RIGHT_5X5, linkModifier)
                    }
                }

                box(Modifier.width(textComponent).height(1f).then(linkModifier))
            }
        }
    }

    override fun LayoutScope.layoutButtons() {
        column(Modifier.fillWidth()) {
            // This is accounting for the fact that the arrangement of the parent has a smaller spacing than it should,
            // because of the Minecraft Ten font having a large amount of padding underneath itself (see the TODO
            // in layoutContent).
            spacer(height = 2f)
            column(Modifier.fillWidth(), Arrangement.spacedBy(4f)) {
                primaryButton("Share with Essential", Modifier.fillWidth()) {
                    replaceWith(continuation.resume(true))
                }
                primaryButton("Don’t Share", style = OutlineButtonStyle.GRAY, modifier = Modifier.fillWidth()) {
                    replaceWith(continuation.resume(false))
                }
            }
        }
    }

}

suspend fun ModalFlow.analyticsModal(): Boolean {
    val acceptedAnalytics = awaitModal { AnalyticsModal(modalManager, it) }
    EssentialConfig.collectOptionalTelemetryWithSource.set { acceptedAnalytics to true }
    return acceptedAnalytics
}
