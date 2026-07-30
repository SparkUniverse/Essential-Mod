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

import gg.essential.config.FeatureFlags
import gg.essential.data.ABTestingData
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.Spacer
import gg.essential.gui.common.modal.EssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.common.shadow.ShadowIcon
import gg.essential.gui.overlay.ModalManager
import gg.essential.util.bindHoverEssentialTooltip
import gg.essential.gui.util.hoveredState

class FeaturesEnabledModal(modalManager: ModalManager) : EssentialModal(modalManager) {

    init {
        configure {
            titleText = "Experimental Features"
            contentText = "This is an experimental version of Essential, it has these new features:"
            contentTextColor = EssentialPalette.TEXT
            primaryButtonStyle = OutlineButtonStyle.BLUE.defaultStyle
            primaryButtonHoverStyle = OutlineButtonStyle.BLUE.hoveredStyle
        }

        // Top padding
        Spacer(height = 10f) childOf customContent

        val featureContainer by UIContainer().constrain {
            x = CenterConstraint()
            y = SiblingConstraint()
            width = 100.percent
            height = ChildBasedSizeConstraint()
        } childOf customContent

        // List of enabled features
        FeatureFlags.abTestingFlags
            .filterValues { featureData -> featureData.second }
            .filterKeys { name -> !ABTestingData.hasData("Notified:$name") }
            .forEach { (name, featureData) ->

                val featureLine by UIContainer().constrain {
                    x = CenterConstraint()
                    y = SiblingConstraint(6f)
                    width = ChildBasedSizeConstraint()
                    height = ChildBasedMaxSizeConstraint()
                } childOf featureContainer

                // The name of the feature
                val feature by EssentialUIText(name, shadowColor = EssentialPalette.BLACK).constrain {
                    y = CenterConstraint()
                    color = EssentialPalette.TEXT_HIGHLIGHT.toConstraint()
                } childOf featureLine

                val info by ShadowIcon(
                    BasicState(EssentialPalette.INFO_9X),
                    BasicState(true),
                    BasicState(EssentialPalette.TEXT),
                    BasicState(EssentialPalette.BLACK),
                ).constrain {
                    x = SiblingConstraint(4f)
                    y = CenterConstraint()
                    width = 9.pixels
                    height = AspectConstraint()
                }.bindHoverEssentialTooltip(
                    BasicState(featureData.first),
                    wrapAtWidth = featureContainer.getWidth() - 21f,
                ) childOf featureLine

                info.rebindPrimaryColor(info.hoveredState().map { if (it) EssentialPalette.TEXT_HIGHLIGHT else EssentialPalette.TEXT })
            }

        // Bottom padding
        Spacer(height = 20f) childOf customContent

        onPrimaryOrDismissAction {
            FeatureFlags.abTestingFlags.filterValues { featureData -> featureData.second }.keys.forEach { name ->
                ABTestingData.addData("Notified:$name")
            }
        }
    }
}
