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
package gg.essential.gui.screenshot.components

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.providers.RegisteredTexture
import kotlin.math.roundToInt

abstract class ScreenshotPreview(
    val screenshotId: ScreenshotId,
    private val desiredImageSize: MutableState<Pair<Int, Int>>,
) : UIContainer() {

    val imgTexture = mutableStateOf<RegisteredTexture?>(null)

    override fun extractComponent(extractor: ElementaExtractor) {
        val realWidth = (getWidth() * extractor.guiScale).roundToInt()
        val realHeight = (getHeight() * extractor.guiScale).roundToInt()
        desiredImageSize.set(Pair(realWidth, realHeight))
    }
}