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
package gg.essential.gui.screenshot.image

import gg.essential.elementa.UIComponent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.screenshot.providers.RegisteredTexture
import gg.essential.universal.render.UGpuSampler
import kotlin.math.roundToInt

open class ScreenshotImage(val texture: State<RegisteredTexture?>) : UIComponent() {

    override fun extractComponent(extractor: ElementaExtractor) {
        extractor.blit(
            (getLeft() * extractor.guiScale).roundToInt(),
            (getTop() * extractor.guiScale).roundToInt(),
            (getRight() * extractor.guiScale).roundToInt(),
            (getBottom() * extractor.guiScale).roundToInt(),
            0f, 0f, 1f, 1f,
            texture.getUntracked()?.gpuTextureView ?: return,
            SAMPLER,
            textureContentImmutable = true,
            premultipliedAlpha = false,
            getColor(),
        )
    }

    companion object {
        private val SAMPLER = UGpuSampler(
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.AddressMode.CLAMP_TO_EDGE,
            UGpuSampler.FilterMode.LINEAR,
            UGpuSampler.FilterMode.LINEAR,
            true,
        )
    }
}