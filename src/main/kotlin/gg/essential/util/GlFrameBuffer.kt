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
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UResolution
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.util.image.GpuTexture
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
//#if MC>=11700
//$$ import org.lwjgl.opengl.GL30.glBindFramebuffer
//$$ import org.lwjgl.opengl.GL30.glDeleteFramebuffers
//$$ import org.lwjgl.opengl.GL30.glFramebufferTexture2D
//$$ import org.lwjgl.opengl.GL30.glGenFramebuffers
//#elseif MC>=11400
//$$ import com.mojang.blaze3d.platform.GlStateManager.bindFramebuffer as glBindFramebuffer
//$$ import com.mojang.blaze3d.platform.GlStateManager.deleteFramebuffers as glDeleteFramebuffers
//$$ import com.mojang.blaze3d.platform.GlStateManager.framebufferTexture2D as glFramebufferTexture2D
//$$ import com.mojang.blaze3d.platform.GlStateManager.genFramebuffers as glGenFramebuffers
//#else
import net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer
import net.minecraft.client.renderer.OpenGlHelper.glDeleteFramebuffers
import net.minecraft.client.renderer.OpenGlHelper.glFramebufferTexture2D
import net.minecraft.client.renderer.OpenGlHelper.glGenFramebuffers
//#endif
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING
import java.awt.Color

class GlFrameBufferImpl(
    override val width: Int,
    override val height: Int,
    colorFormat: GpuTexture.Format,
    depthFormat: GpuTexture.Format,
) : GlFrameBuffer {
    override val frameBuffer: Int
    override val texture: GpuTextureImpl
    override val depthStencil: GpuTextureImpl

    init {
        frameBuffer = glGenFramebuffers()
        texture = OwnedGpuTextureImpl(width, height, colorFormat)
        depthStencil = OwnedGpuTextureImpl(width, height, depthFormat)

        use {
            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D,
                texture.glId,
                0
            )
            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                when (depthFormat) {
                    GpuTexture.Format.RGBA8 -> throw IllegalArgumentException()
                    GpuTexture.Format.DEPTH24_STENCIL8 -> GL_DEPTH_STENCIL_ATTACHMENT
                    GpuTexture.Format.DEPTH32 -> GL_DEPTH_ATTACHMENT
                },
                GL_TEXTURE_2D,
                depthStencil.glId,
                0
            )
        }
    }

    private var closed = false
    override fun close() {
        if (closed) return
        closed = true

        depthStencil.close()

        texture.close()

        glDeleteFramebuffers(frameBuffer)
    }

    override fun <T> use(block: () -> T): T {
        val unbind = bind(frameBuffer)
        try {
            return block()
        } finally {
            unbind()
        }
    }

    override fun useAsRenderTarget(block: (UMatrixStack, Int, Int) -> Unit) {
        use {
            // Prepare frame buffer
            val scissorState = glGetBoolean(GL_SCISSOR_TEST)
            glDisable(GL_SCISSOR_TEST)
            glViewport(0, 0, width, height)

            // Undo MC's scaling and the distortion caused by different viewport size with same projection matrix
            val stack = UMatrixStack()
            val scale = 1.0 / UResolution.scaleFactor
            stack.scale(
                scale * UResolution.viewportWidth / width,
                scale * UResolution.viewportHeight / height,
                1.0,
            )

            // Rendering
            block(stack, width, height)

            // Restore the original state
            glViewport(0, 0, UResolution.viewportWidth, UResolution.viewportHeight)
            if (scissorState) glEnable(GL_SCISSOR_TEST)
        }
    }

    override fun drawTexture(matrixStack: UMatrixStack, x: Double, y: Double, width: Double, height: Double, color: Color, sampler: UGpuSampler) {
        matrixStack.push()
        matrixStack.scale(1f, 1f, 50f)

        val red = color.red.toFloat() / 255f
        val green = color.green.toFloat() / 255f
        val blue = color.blue.toFloat() / 255f
        val alpha = color.alpha.toFloat() / 255f

        val worldRenderer = UBufferBuilder.create(UGraphics.DrawMode.QUADS, DefaultVertexFormats.POSITION_TEX_COLOR)
        worldRenderer.pos(matrixStack, x, y + height, 0.0).tex(0.0, 0.0).color(red, green, blue, alpha).endVertex()
        worldRenderer.pos(matrixStack, x + width, y + height, 0.0).tex(1.0, 0.0).color(red, green, blue, alpha).endVertex()
        worldRenderer.pos(matrixStack, x + width, y, 0.0).tex(1.0, 1.0).color(red, green, blue, alpha).endVertex()
        worldRenderer.pos(matrixStack, x, y, 0.0).tex(0.0, 1.0).color(red, green, blue, alpha).endVertex()
        worldRenderer.build()?.drawAndClose(PIPELINE) {
            texture(0, texture.ucView, sampler)
        }

        matrixStack.pop()
    }

    override fun bind(): () -> Unit {
        return bind(frameBuffer)
    }

    private fun bind(glId: Int): () -> Unit {
        val prevReadFrameBufferBinding = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val prevDrawFrameBufferBinding = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)

        glBindFramebuffer(GL_FRAMEBUFFER, glId)

        return {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFrameBufferBinding)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prevDrawFrameBufferBinding)
        }
    }

    override fun clear(clearColor: Color, clearDepth: Double, clearStencil: Int) {
        use {
            with(clearColor) {
                UGraphics.clearColor(red / 255f, green / 255f, blue / 255f, alpha / 255f)
            }
            UGraphics.clearDepth(clearDepth)
            glClearStencil(clearStencil)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
        }
    }

    companion object {
        private val PIPELINE = URenderPipeline.builderWithDefaultShader(
            "essential:framebuffer_texture",
            UGraphics.DrawMode.QUADS,
            UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
        ).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}
