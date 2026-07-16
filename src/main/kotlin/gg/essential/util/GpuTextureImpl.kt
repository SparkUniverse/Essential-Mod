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

import gg.essential.model.util.Color
import gg.essential.universal.UGraphics
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.image.GpuTexture
import gg.essential.util.image.bitmap.Bitmap
import gg.essential.util.image.bitmap.MutableBitmap
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import java.nio.ByteBuffer
import java.nio.FloatBuffer

//#if MC >= 26.2
//$$ import com.mojang.blaze3d.buffers.GpuBuffer
//$$ import com.mojang.blaze3d.systems.RenderSystem
//$$ import org.joml.Vector4f
//#endif

//#if MC >= 1.21.5
//$$ import net.minecraft.client.texture.GlTexture
//#endif

//#if MC>=12105
//$$ import com.mojang.blaze3d.opengl.GlStateManager
//#else
import net.minecraft.client.renderer.GlStateManager
//#endif

//#if MC>=11700
//$$ import org.lwjgl.opengl.GL30.glBindFramebuffer
//$$ import org.lwjgl.opengl.GL30.glFramebufferTexture2D
//$$ import org.lwjgl.opengl.GL30.glGenFramebuffers
//#elseif MC>=11400
//$$ import com.mojang.blaze3d.platform.GlStateManager.bindFramebuffer as glBindFramebuffer
//$$ import com.mojang.blaze3d.platform.GlStateManager.framebufferTexture2D as glFramebufferTexture2D
//$$ import com.mojang.blaze3d.platform.GlStateManager.genFramebuffers as glGenFramebuffers
//#else
import net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer
import net.minecraft.client.renderer.OpenGlHelper.glFramebufferTexture2D
import net.minecraft.client.renderer.OpenGlHelper.glGenFramebuffers
//#endif

abstract class GpuTextureImpl(private val format: GpuTexture.Format) : GpuTexture {
    override val glId: Int
        //#if MC >= 1.21.5
        //$$ get() = (UGraphics.getPlatformAdapter().texture(uc) as GlTexture).glId
        //#else
        get() = UGraphics.getPlatformAdapter().texture(uc)
        //#endif

    //#if MC >= 26.2
    //$$ val b3d: com.mojang.blaze3d.textures.GpuTexture
    //$$     get() = UGraphics.getPlatformAdapter().texture(uc)
    //#endif

    override fun copyFrom(sources: Iterable<GpuTexture.CopyOp>) {
        //#if MC >= 26.2
        //$$ // B3D's copyTextureToTexture is broken on OpenGL, it passes the wrong arguments to the lower layer
        //$$ if (b3d is GlTexture) {
        //$$     return copyFromOpenGl(sources)
        //$$ }
        //$$ for ((src, srcX, srcY, destX, destY, width, height) in sources) {
        //$$     src as GpuTextureImpl
        //$$     RenderSystem.getDevice().createCommandEncoder()
        //$$         .copyTextureToTexture(src.b3d, this.b3d, 0, destX, destY, srcX, srcY, width, height)
        //$$ }
        //#else
        return copyFromOpenGl(sources)
        //#endif
    }

    private fun copyFromOpenGl(sources: Iterable<GpuTexture.CopyOp>) {
        val prevScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST)
        if (prevScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST)

        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)

        val attachment = if (format.isColor) GL30.GL_COLOR_ATTACHMENT0 else GL30.GL_DEPTH_ATTACHMENT
        val bufferBit = if (format.isColor) GL11.GL_COLOR_BUFFER_BIT else GL11.GL_DEPTH_BUFFER_BIT

        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, if (format.isColor) colorWriteFrameBuffer else depthWriteFrameBuffer)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, if (format.isColor) colorReadFrameBuffer else depthReadFrameBuffer)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, this.glId, 0)

        for ((src, srcX, srcY, destX, destY, width, height) in sources) {
            glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, src.glId, 0)
            GL30.glBlitFramebuffer(
                srcX, srcY, srcX + width, srcY + height,
                destX, destY, destX + width, destY + height,
                bufferBit, GL11.GL_NEAREST
            )
        }

        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, 0, 0)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)

        if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
    }

    override fun clearColor(color: Color) {
        //#if MC >= 26.2
        //$$ RenderSystem.getDevice().createCommandEncoder()
        //$$     .clearColorTexture(b3d, Vector4f(color.r.toFloat(), color.g.toFloat(), color.b.toFloat(), color.a.toFloat()).div(255f))
        //#else
        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, colorWriteFrameBuffer)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0)

        //#if MC >= 26.1
        //$$ GlStateManager._colorMask(com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_ALL)
        //#elseif MC>=12105
        //$$ GlStateManager._colorMask(true, true, true, true)
        //#else
        GlStateManager.colorMask(true, true, true, true)
        //#endif
        UGraphics.clearColor(color.r.toFloat() / 255, color.g.toFloat() / 255, color.b.toFloat() / 255, color.a.toFloat() / 255)
        glClear(GL11.GL_COLOR_BUFFER_BIT)

        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
        //#endif
    }

    override fun clearDepth(depth: Float) {
        //#if MC >= 26.2
        //$$ RenderSystem.getDevice().createCommandEncoder()
        //$$     .clearDepthTexture(b3d, depth.toDouble())
        //#else
        val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthWriteFrameBuffer)
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glId, 0)

        //#if MC>=12105
        //$$ GlStateManager._depthMask(true)
        //#else
        GlStateManager.depthMask(true)
        //#endif
        UGraphics.clearDepth(depth.toDouble())
        glClear(GL11.GL_DEPTH_BUFFER_BIT)

        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
        //#endif
    }

    //#if MC < 26.2
    private fun glClear(bits: Int) {
        val previousScissorState = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)

        GL11.glClear(bits)

        if (previousScissorState) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }
    //#endif

    override fun readPixelColors(x: Int, y: Int, width: Int, height: Int): Bitmap {
        //#if MC >= 26.2
        //$$ if (format != GpuTexture.Format.RGBA8) {
        //$$     throw UnsupportedOperationException("Currently only RGBA8 format is supported.")
        //$$ }
        //$$
        //$$ val bufferSize = width * height * 4
        //$$ val buffer = RenderSystem.getDevice().createBuffer(
        //$$     { "readPixelColors buffer" },
        //$$     GpuBuffer.USAGE_COPY_DST or GpuBuffer.USAGE_MAP_READ,
        //$$     bufferSize.toLong(),
        //$$ )
        //$$ try {
        //$$     val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        //$$     commandEncoder.copyTextureToBuffer(b3d, buffer, 0, {}, 0, x, y, width, height)
        //$$     val fence = commandEncoder.createFence()
        //$$     try {
        //$$         commandEncoder.submit()
        //$$         fence.awaitCompletion(Long.MAX_VALUE)
        //$$     } finally {
        //$$         fence.close()
        //$$     }
        //$$     buffer.map(true, false).use { mappedView ->
        //$$         // Note: `mappedView` is only valid until closed by above `use`, so need to copy it before returning
        //$$         val byteBuffer = ByteBuffer.allocate(bufferSize).put(mappedView.data())
        //$$         return ByteBufferBitmap(width, height, byteBuffer)
        //$$     }
        //$$ } finally {
        //$$     buffer.close()
        //$$ }
        //#else
        val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, colorReadFrameBuffer)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0)
        val result = glReadPixelColors(x, y, width, height)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)
        return result
        //#endif
    }

    override fun readPixelDepths(x: Int, y: Int, width: Int, height: Int): FloatBuffer {
        //#if MC >= 26.2
        //$$ if (format != GpuTexture.Format.DEPTH32) {
        //$$     throw UnsupportedOperationException("Currently only DEPTH32 format is supported.")
        //$$ }
        //$$
        //$$ // B3D's copyTextureToBuffer doesn't work properly for depth textures, it always uses the color attachment
        //$$ // of its internal framebuffer.
        //$$ // The Vulkan one seems fine.
        //$$ if (b3d is GlTexture) {
        //$$     return readPixelDepthsOpenGl(x, y, width, height)
        //$$ }
        //$$
        //$$ val bufferSize = width * height * 4
        //$$ val buffer = RenderSystem.getDevice().createBuffer(
        //$$     { "readPixelDepths buffer" },
        //$$     GpuBuffer.USAGE_COPY_DST or GpuBuffer.USAGE_MAP_READ,
        //$$     bufferSize.toLong(),
        //$$ )
        //$$ try {
        //$$     val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        //$$     commandEncoder.copyTextureToBuffer(b3d, buffer, 0, {}, 0, x, y, width, height)
        //$$     val fence = commandEncoder.createFence()
        //$$     try {
        //$$         commandEncoder.submit()
        //$$         fence.awaitCompletion(Long.MAX_VALUE)
        //$$     } finally {
        //$$         fence.close()
        //$$     }
        //$$     buffer.map(true, false).use { mappedView ->
        //$$         // Note: `mappedView` is only valid until closed by above `use`, so need to copy it before returning
        //$$         val byteBuffer = BufferUtils.createByteBuffer(bufferSize).put(mappedView.data())
        //$$         return byteBuffer.rewind().asFloatBuffer()
        //$$     }
        //$$ } finally {
        //$$     buffer.close()
        //$$ }
        //#else
        return readPixelDepthsOpenGl(x, y, width, height)
        //#endif
    }

    private fun readPixelDepthsOpenGl(x: Int, y: Int, width: Int, height: Int): FloatBuffer {
        val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthReadFrameBuffer)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glId, 0)
        val result = glReadPixelDepths(x, y, width, height)
        glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0)
        glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)
        return result
    }

    private class ByteBufferBitmap(
        override val width: Int,
        override val height: Int,
        private val byteBuffer: ByteBuffer
    ) : Bitmap {

        override fun get(x: Int, y: Int): Color {
            val index = (y * width + x) * 4

            return Color(
                r = byteBuffer.get(index).toUByte(),
                g = byteBuffer.get(index + 1).toUByte(),
                b = byteBuffer.get(index + 2).toUByte(),
                a = byteBuffer.get(index + 3).toUByte()
            )
        }

        override fun mutableCopy(): MutableBitmap {
            val copy = Bitmap.ofSize(width, height)
            copy.set(0, 0, width, height, this)
            return copy
        }

    }

    companion object {
        private val colorReadFrameBuffer by lazy { glGenFramebuffers() }
        private val colorWriteFrameBuffer by lazy { glGenFramebuffers() }
        private val depthReadFrameBuffer by lazy { genDepthOnlyFrameBuffer() }
        private val depthWriteFrameBuffer by lazy { genDepthOnlyFrameBuffer() }
        private fun genDepthOnlyFrameBuffer() = glGenFramebuffers().also { id ->
            val prevDrawFrameBufferBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            val prevReadFrameBufferBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, id)
            glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, id)
            // Prior to GL 4.1, read and draw buffers (both!) must be explicitly set to NONE if the framebuffer does not
            // have a color attachment, otherwise it will not be considered complete and operations on it may error.
            GL11.glDrawBuffer(GL11.GL_NONE)
            GL11.glReadBuffer(GL11.GL_NONE)
            glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
            glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)
        }

        private fun glReadPixelColors(x: Int, y: Int, width: Int, height: Int): Bitmap {
            val byteBuffer = BufferUtils.createByteBuffer(width * height * 4)
            GL11.glReadPixels(
                x,
                y,
                width,
                height,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                byteBuffer,
            )
            return ByteBufferBitmap(
                width = width,
                height = height,
                byteBuffer = byteBuffer
            )
        }

        private fun glReadPixelDepths(x: Int, y: Int, width: Int, height: Int): FloatBuffer {
            val floatBuffer = BufferUtils.createFloatBuffer(width * height)
            GL11.glReadPixels(
                x,
                y,
                width,
                height,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                floatBuffer,
            )
            if (platform.irisReversesZ) {
                for (i in 0 until width * height) {
                    floatBuffer.put(i, 1 - floatBuffer.get(i))
                }
            }
            return floatBuffer
        }
    }
}
