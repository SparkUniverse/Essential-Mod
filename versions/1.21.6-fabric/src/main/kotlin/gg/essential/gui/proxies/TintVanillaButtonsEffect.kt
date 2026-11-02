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
package gg.essential.gui.proxies

import gg.essential.Essential
import gg.essential.event.render.RenderTickEvent
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UResolution
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.util.UDrawContext
import java.awt.Color
import net.minecraft.client.gui.render.state.GuiRenderState
import gg.essential.universal.UMinecraft
import gg.essential.util.renderGuiRenderStateToTexture
import net.minecraft.client.gui.DrawContext
import gg.essential.gui.proxies.TintVanillaButtonsEffectShared.*
import gg.essential.gui.proxies.TintVanillaButtonsEffectShared.Companion.NON_WHITE_TINT_PIPELINE
import gg.essential.gui.proxies.TintVanillaButtonsEffectShared.Companion.averageButtonColor
import gg.essential.universal.render.SharedIndexBuffers
import gg.essential.universal.render.UGpuBuffer
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.UGpuSampler
import gg.essential.universal.render.UGpuTexture
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPassDescriptor
import gg.essential.util.McElementaExtractor
import gg.essential.util.NEAREST
import me.kbrewster.eventbus.Subscribe

/**
 * 1.21.6+ implementation of tinting our vanilla proxy buttons.
 *
 * This class uses [Page]s to collect all the vanilla button draws onto one texture, that can be tinted later in a single
 * pass before it is used by vanilla.
 * This texture is also enqueued to the vanilla context, before it is tinted, so that it is rendered in the correct order
 * by the vanilla gui rendering that happens later on.
 */
class TintVanillaButtonsEffect {

    /**
     * Simple method to wrap beforeDraw + afterDraw around a drawing operation.
     * Has an identical function in < 1.21.6
     */
    fun drawTinted(context: UDrawContext, x1: Int, y1: Int, x2: Int, y2: Int, color: Color, draw: (UDrawContext) -> Unit) {
        val areaToBeTinted = AreaToBeTinted(x1, y1, x2, y2)
        if (areaToBeTinted.invalid) return

        val result = Page.drawToPage(context, areaToBeTinted, color) ?: return
        // Draw the content that needs to be tinted
        draw(result.first)
        result.second.run()
    }

    /**
     * Represents a page of vanilla button draws to be tinted later.
     * Each page collects all the draws in non overlapping spaces of the context's screen space, and then later draws
     * them all to a texture that can be tinted in one pass.
     *
     * We do it this way as the setup for renderGuiRenderStateToTexture() is quite expensive, so we want to avoid multiple calls to it.
     *
     * The page texture is handled like a sprite sheet, with logic to fit the draws into the first available space, with
     * additional pages created as space is needed, which is handled automatically by the initial page.
     */
    class Page(
        private val pageWidth: Int = UResolution.viewportWidth,
        private val pageHeight: Int = UResolution.viewportHeight,
    ) {
        val guiRenderState = GuiRenderState()
        val drawContext = DrawContext(
            UMinecraft.getMinecraft(),
            guiRenderState,
            //#if MC>=12111
            //$$ 0, 0, // mouse pos for tooltips, which our scheme doesn't support anyway
            //#endif
        )
        private val texture = TemporaryTextureAllocator.allocate(pageWidth, pageHeight)

        // List of free spaces in this page, initialized with the full page size
        private val freeSpaces = mutableListOf(Rect(0, 0, pageWidth, pageHeight))

        // List of rectangles that have been placed/allocated on the page, will be reused by the shader pass later
        private val placedRects = mutableListOf<PlacedRect>()

        // The next page to be created if this one runs out of space
        private var nextPage: Page? = null

        /**
         * Tints the page by rendering our DrawContext/GuiRenderState to a texture and then drawing that texture with a shader
         */
        fun finalizeAndTintPage() {
            assert(placedRects.isNotEmpty())

            val drawnPageTexture = renderGuiRenderStateToTexture(guiRenderState) // the expensive part, only called once per page

            val randomRect = placedRects.first()
            val averageCol = averageButtonColor(drawnPageTexture, randomRect.x, randomRect.y, randomRect.width, randomRect.height)

            if (averageCol == null) {
                drawnPageTexture.close()
                return
            }

            val device = UGraphics.getDevice()
            val vertexBuffer = UBufferBuilder.create(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
                .also { buffer ->
                    for (rectGL in placedRects) {
                        // parse the rectangle to OpenGL UV coordinates
                        val u = rectGL.x / pageWidth.toDouble()
                        val v = (pageHeight - (rectGL.y + rectGL.height)) / pageHeight.toDouble()
                        val u2 = (rectGL.x + rectGL.width) / pageWidth.toDouble()
                        val v2 = (pageHeight - rectGL.y) / pageHeight.toDouble()

                        val rect = rectGL
                        val x = rect.x.toDouble()
                        val y = rect.y.toDouble()
                        val x2 = (rect.x + rect.width).toDouble()
                        val y2 = (rect.y + rect.height).toDouble()

                        // add the quad to the buffer with the tint color
                        buffer.pos(UMatrixStack.UNIT, x, y2, 0.0).tex(u, v).color(rect.color).endVertex()
                        buffer.pos(UMatrixStack.UNIT, x2, y2, 0.0).tex(u2, v).color(rect.color).endVertex()
                        buffer.pos(UMatrixStack.UNIT, x2, y, 0.0).tex(u2, v2).color(rect.color).endVertex()
                        buffer.pos(UMatrixStack.UNIT, x, y, 0.0).tex(u, v2).color(rect.color).endVertex()
                    }
                }
                .build()!!
                .use { device.createBuffer(UGpuBuffer.Usage.VERTEX, it.toByteBuffer()) }
            vertexBuffer.use { vertexBuffer ->
                val (indexBuffer, indexType) = SharedIndexBuffers.quads(placedRects.size * 4)
                val descriptor = URenderPassDescriptor { "TintVanillaButtonsEffect" }
                    .withColorAttachment(texture, null)
                device.createRenderPass(descriptor).use { renderPass ->
                    renderPass.projectionMatrix(floatArrayOf(
                        2f/pageWidth, 0f,             0f, 0f,
                        0f,           -2f/pageHeight, 0f, 0f,
                        0f,           0f,             1f, 0f,
                        -1f,          1f,             0f, 1f,
                    ))
                    renderPass.pipeline(NON_WHITE_TINT_PIPELINE)
                    renderPass.texture("u_Button", drawnPageTexture.ucView, UGpuSampler.NEAREST)
                    renderPass.uniform("u_AverageColor", averageCol.red / 255F, averageCol.green / 255F, averageCol.blue / 255F)
                    renderPass.vertexBuffer(0, vertexBuffer.slice())
                    renderPass.indexBuffer(indexBuffer, indexType)
                    renderPass.drawIndexed(placedRects.size * 6)
                }
            }

            drawnPageTexture.close()

            nextPage?.finalizeAndTintPage()
        }

        /**
         * Draws the given draw function to the page's drawContext, placing it in the first available non-overlapping space.
         * If no space is available, it will try to draw on the next page.
         *
         * Then enqueues the page's texture to the vanilla context so it is rendered in the correct order by the vanilla gui rendering that happens later.
         */
        fun drawVanillaToPage(vanillaContext: UDrawContext, vanillaDrawArea: AreaToBeTinted, color: Color): Pair<UDrawContext, Runnable>? {
            val placedRect = tryPlaceRect(vanillaDrawArea.width, vanillaDrawArea.height, color)
            if (placedRect == null) {
                // no space, try next page
                val next = nextPage ?: Page(pageWidth, pageHeight).also { nextPage = it }

                return next.drawVanillaToPage(vanillaContext, vanillaDrawArea, color)
            }
            // we have space in this page so draw this to our context

            val placedMC = placedRect.toMC()

            drawContext.matrices.pushMatrix()
            // scissor the area to be drawn to the placed rectangle, this is especially necessary if the original draw
            // was done partially out of screen bounds as the draw() block may try to draw outside this rectangle
            drawContext.enableScissor(placedMC.x, placedMC.y, placedMC.x + placedMC.width, placedMC.y + placedMC.height)
            // offset the matrices by the difference between the vanilla draw position and the placed rect position so the draw()
            // block will be made to draw in the placed rect position
            drawContext.matrices.translate(
                (placedMC.x - vanillaDrawArea.xMC).toFloat(),
                (placedMC.y - vanillaDrawArea.yMC).toFloat()
            )

            return drawContext.let {
                UDrawContext(it, UMatrixStack(it.matrices))
            } to Runnable {
                drawContext.disableScissor()
                drawContext.matrices.popMatrix()

                // now enqueue the draw to the vanilla context
                val extractor = McElementaExtractor(vanillaContext)
                extractor.blit(
                    vanillaDrawArea.left, vanillaDrawArea.top,
                    vanillaDrawArea.right, vanillaDrawArea.bottom,
                    placedRect.left / pageWidth.toFloat(), 1 - placedRect.top / pageHeight.toFloat(),
                    placedRect.right / pageWidth.toFloat(), 1 - placedRect.bottom / pageHeight.toFloat(),
                    texture,
                    UGpuSampler.NEAREST,
                    textureContentImmutable = false,
                    premultipliedAlpha = true,
                    Color.WHITE,
                )
                extractor.close()
            }
        }

        /**
         * Attempts to place a rectangle of the given width and height in the free spaces.
         * Returns the placed rectangle if successful, or null if no space is available.
         */
        private fun tryPlaceRect(width: Int, height: Int, color: Color): PlacedRect? {
            var bestRect: Rect? = null

            for (free in freeSpaces) {
                if (width <= free.width && height <= free.height) {
                    if (bestRect == null || free.area < bestRect.area) {
                        bestRect = free
                    }
                }
            }

            bestRect?.let {
                val placed = PlacedRect(it.x, it.y, width, height, color)
                placedRects.add(placed)
                splitFreeSpaceRect(it, width, height)
                return placed
            }

            return null // no space
        }

        /**
         * Splits the given free space rectangle into smaller rectangles after placing a used rectangle.
         * Removes the original free space and adds the remaining spaces back to the list.
         */
        private fun splitFreeSpaceRect(free: Rect, usedW: Int, usedH: Int) {
            freeSpaces.remove(free)

            val spaceRemainingRight = free.width > usedW
            val spaceRemainingBottom = free.height > usedH

            if (spaceRemainingRight) {
                freeSpaces.add(Rect(free.x + usedW, free.y, free.width - usedW, usedH))
            }
            if (spaceRemainingBottom) {
                freeSpaces.add(Rect(free.x, free.y + usedH, free.width, free.height - usedH))
            }
            if (spaceRemainingRight && spaceRemainingBottom) {
                freeSpaces.add(Rect(free.x + usedW, free.y + usedH, free.width - usedW, free.height - usedH))
            }
        }

        private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
            val area = width * height
        }
        data class PlacedRect(val x: Int, val y: Int, val width: Int, val height: Int, val color: Color) {
            val left: Int get() = x
            val top: Int get() = y
            val right: Int get() = x + width
            val bottom: Int get() = y + height
            fun toMC(): PlacedRect {
                val scale = UResolution.scaleFactor.toInt()
                return copy(
                    x = x / scale,
                    y = y / scale,
                    width = width / scale,
                    height = height / scale
                )
            }
        }

        companion object {

            private var rootPage: Page? = null

            fun drawToPage(vanillaContext: UDrawContext, area: AreaToBeTinted, color: Color): Pair<UDrawContext, Runnable>? {
                val page = rootPage ?: Page().also { rootPage = it }
                return page.drawVanillaToPage(vanillaContext, area, color)
            }

            @JvmStatic
            fun renderAll() {
                val page = rootPage ?: return
                rootPage = null
                page.finalizeAndTintPage()
            }
        }
    }

    private object TemporaryTextureAllocator {
        // When we allocate a texture, we need to hold on to it until the next frame so MC's gui renderer can use it
        private val usedAllocations = mutableListOf<UGpuTextureView>()
        // We hold on to it for an additionally frame so we can re-use it instead of having to re-allocate one each frame
        private val reusableAllocations = mutableListOf<UGpuTextureView>()

        private var registered = false

        fun allocate(width: Int, height: Int): UGpuTextureView {
            val device = UGraphics.getDevice()

            var allocation = reusableAllocations.removeLastOrNull()

            if (allocation != null && (allocation.texture.width != width || allocation.texture.height != height)) {
                allocation.close()
                allocation.texture.close()
                allocation = null
            }

            if (allocation == null) {
                allocation = device.createTextureView(device.createTexture(
                    "Tinted vanilla buttons",
                    UGpuTexture.Usage.COPY_DST + UGpuTexture.Usage.RENDER_ATTACHMENT + UGpuTexture.Usage.TEXTURE_BINDING,
                    UGpuFormat.DEFAULT_RGBA,
                    width,
                    height,
                ))
            }

            device.clearColor(allocation.texture, 0f, 0f, 0f, 0f)

            usedAllocations.add(allocation)

            if (!registered) {
                registered = true
                Essential.EVENT_BUS.register(this)
            }

            return allocation
        }

        @Subscribe
        fun nextFrame(event: RenderTickEvent) {
            if (!event.isPre) return

            reusableAllocations.forEach { it.close(); it.texture.close() }
            reusableAllocations.clear()
            reusableAllocations.addAll(usedAllocations)
            usedAllocations.clear()

            if (reusableAllocations.isEmpty()) {
                registered = false
                Essential.EVENT_BUS.unregister(this)
            }
        }
    }
}