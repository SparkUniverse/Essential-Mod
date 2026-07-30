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
package gg.essential.gui.friends.message.screenshot

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedWidth
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.fillHeight
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.floatingBox
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.layout
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.scrollable
import gg.essential.gui.layoutdsl.widthAspect
import gg.essential.gui.screenshot.components.ScreenshotPreview
import gg.essential.gui.screenshot.components.ScreenshotProviderManager
import gg.essential.gui.screenshot.providers.WindowedProvider
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.findChildrenOfType
import gg.essential.util.onLeftClick

class ScreenshotAttacher(
    private val screenshotAttachmentManager: ScreenshotAttachmentManager
) : UIContainer() {

    private val desiredImageSize = mutableStateOf(ScreenshotProviderManager.minResolutionTargetResolution)
    val screenshotProvider = ScreenshotProviderManager(
        platform.screenshotManager,
        screenshotAttachmentManager.isScreenOpen,
        screenshotAttachmentManager.selectedImages,
        desiredImageSize,
    )

    init {
        val screenshotPadding = 10f
        layout(Modifier.fillWidth().childBasedHeight()) {
            box(Modifier.fillWidth().height(83f).color(EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT)) {
                val scrollComponent: ScrollComponent = scrollable(Modifier.fillParent(), horizontal = true) {
                    box(
                        Modifier.fillHeight(padding = screenshotPadding).childBasedWidth(screenshotPadding)
                            .alignHorizontal(Alignment.Start)
                    ) {
                        row(Modifier.fillHeight(), Arrangement.spacedBy(screenshotPadding)) {
                            forEach(screenshotAttachmentManager.selectedImages) { screenshotId ->
                                RemoveableScreenshotPreview(
                                    screenshotId,
                                    desiredImageSize,
                                    screenshotAttachmentManager
                                )(Modifier.widthAspect(16f / 9f).fillHeight()).onLeftClick {
                                    it.stopPropagation()
                                }
                            }
                        }
                    }
                }.onLeftClick {
                    USound.playButtonPress()
                    screenshotAttachmentManager.isPickingScreenshots.set(true)
                }
                val scrollBar: UIComponent
                floatingBox(
                    Modifier.fillWidth().height(3f).color(EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT)
                        .alignVertical(Alignment.End)
                ) {
                    scrollBar = box(Modifier.fillHeight().color(EssentialPalette.SCROLLBAR))
                }
                scrollComponent.setHorizontalScrollBarComponent(scrollBar, hideWhenUseless = true)
            }
        }

        addUpdateFunc { _, _ ->
            // This component will usually not contain many screenshots, so we can simply request all
            val window = WindowedProvider.Window(screenshotProvider.currentIds.indices, false)
            val textures = screenshotProvider.provide(window)

            for (component in findChildrenOfType<ScreenshotPreview>(recursive = true)) {
                component.imgTexture.set(textures[component.screenshotId])
            }
        }
    }

}