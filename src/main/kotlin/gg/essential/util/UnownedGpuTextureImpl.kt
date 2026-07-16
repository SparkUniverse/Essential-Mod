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
package gg.essential.util

import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.util.image.GpuTexture

class UnownedGpuTextureImpl(
    format: GpuTexture.Format,
    override val ucView: UGpuTextureView,
) : GpuTextureImpl(format) {
    override val uc: UGpuTexture
        get() = ucView.texture
    override val width: Int
        get() = uc.width
    override val height: Int
        get() = uc.height

    override fun close() {
        throw UnsupportedOperationException()
    }
}
