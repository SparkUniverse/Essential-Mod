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

import gg.essential.universal.UGraphics
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.util.image.GpuTexture
import org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8
import org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL
import org.lwjgl.opengl.GL30.GL_UNSIGNED_INT_24_8

class OwnedGpuTextureImpl(
    override val width: Int,
    override val height: Int,
    format: GpuTexture.Format,
) : GpuTextureImpl(format) {
    override var uc: UGpuTexture = UGraphics.getDevice().createTexture(
        null,
        UGpuTexture.Usage.COPY_SRC + UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.RENDER_ATTACHMENT + UGpuTexture.Usage.TEXTURE_BINDING,
        when (format) {
            GpuTexture.Format.RGBA8 -> UGraphics.getPlatformAdapter().defaultGpuFormatRgba
            //#if MC >= 26.2
            //$$ GpuTexture.Format.DEPTH24_STENCIL8 -> UGraphics.getPlatformAdapter().gpuFormat(com.mojang.blaze3d.GpuFormat.D24_UNORM_S8_UINT)
            //#else
            GpuTexture.Format.DEPTH24_STENCIL8 -> UGraphics.getPlatformAdapter().gpuFormat(GL_DEPTH24_STENCIL8, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8)
            //#endif
            GpuTexture.Format.DEPTH32 -> UGraphics.getPlatformAdapter().defaultGpuFormatDepth
        },
        width,
        height,
    )
    override var ucView: UGpuTextureView = UGraphics.getDevice().createTextureView(uc)

    override fun close() {
        ucView.close()
        uc.close()
    }
}
