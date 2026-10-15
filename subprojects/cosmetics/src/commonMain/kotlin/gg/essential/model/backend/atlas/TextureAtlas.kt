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
package gg.essential.model.backend.atlas

import gg.essential.model.backend.RenderBackend
import gg.essential.model.backend.RenderBackend.Texture
import gg.essential.model.util.ResourceCleaner
import gg.essential.model.util.UVertexConsumer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TextureAtlas private constructor(
    private val renderBackend: RenderBackend,
    val atlasTexture: Texture,
    private val textures: Map<Texture, Entry>
) : AutoCloseable {

    init {
        val renderBackend = renderBackend
        val atlasTexture = atlasTexture
        resourceCleaner.register(this) { renderBackend.deleteTexture(atlasTexture) }
        resourceCleaner.runCleanups()
    }

    override fun close() {
        renderBackend.deleteTexture(atlasTexture)
    }

    fun offsetVertexConsumer(texture: Texture, vertexConsumer: UVertexConsumer): UVertexConsumer {
        val entry = textures.getValue(texture)
        return object : UVertexConsumer by vertexConsumer {
            override fun tex(u: Double, v: Double): UVertexConsumer {
                vertexConsumer.tex(u * entry.uScale + entry.uOffset, v * entry.vScale + entry.vOffset)
                return this
            }
        }
    }

    private class Entry(val uScale: Double, val vScale: Double, val uOffset: Double, val vOffset: Double)

    companion object {
        private val resourceCleaner = ResourceCleaner<TextureAtlas>()

        fun create(renderBackend: RenderBackend, name: String, textures: Collection<Texture>): TextureAtlas? {
            val toBePlaced = textures.map { it to WH(it.width, it.height) }

            val sorted = toBePlaced.sortedByDescending { (_, size) -> with(size) { w * h * max(w, h) / min(w, h) } }
            val packing = pack(sorted.map { it.first }, 4096) ?: return null

            val atlasTexture = renderBackend.createTexture("atlas/$name", packing.atlasWidth, packing.atlasHeight)
            renderBackend.blitTexture(atlasTexture, packing.textures.map { (texture, x, y, w, h, flipped) ->
                // TODO implement flipping, somehow
                RenderBackend.BlitOp(texture, 0, 0, x, y, w, h)
            })
            val texturesMap = packing.textures.associate { (texture, x, y, w, h, flipped) ->
                // TODO implement flipping
                texture to Entry(
                    w.toDouble() / packing.atlasWidth.toDouble(),
                    h.toDouble() / packing.atlasHeight.toDouble(),
                    x.toDouble() / packing.atlasWidth.toDouble(),
                    y.toDouble() / packing.atlasHeight.toDouble(),
                )
            }
            return TextureAtlas(renderBackend, atlasTexture, texturesMap)
        }
    }
}

private data class WH(val w: Int, val h: Int)
private data class XYWH(val x: Int, val y: Int, val w: Int, val h: Int)
private class Packing(
    val atlasWidth: Int,
    val atlasHeight: Int,
    val textures: List<Entry>,
)
private data class Entry(val texture: Texture, val x: Int, val y: Int, val w: Int, val h: Int, val flipped: Boolean)

// Packing algorithm very much based on https://github.com/TeamHypersomnia/rectpack2D#algorithm
private fun pack(textures: Iterable<Texture>, maxAtlasSize: Int): Packing? {
    val discardStep = 16
    val initialSize = 512

    var bestPacking: Packing? = packWithSize(textures, initialSize, initialSize)

    var squareSize = initialSize
    while (bestPacking == null) {
        squareSize *= 2
        if (squareSize > maxAtlasSize) {
            return null
        }
        bestPacking = packWithSize(textures, squareSize, squareSize)
    }

    var step = -squareSize / 2
    if (squareSize > initialSize) step /= 2
    while (abs(step) >= discardStep) {
        squareSize += step
        val packing = packWithSize(textures, squareSize, squareSize)
        step = if (packing == null) {
            abs(step / 2)
        } else {
            -abs(step / 2)
        }
        bestPacking = packing ?: bestPacking
    }

    bestPacking!!
    var width = bestPacking.atlasWidth
    var height = bestPacking.atlasHeight

    step = -width / 2
    while (abs(step) >= discardStep) {
        width += step
        val packing = packWithSize(textures, width, height)
        step = if (packing == null) {
            abs(step / 2)
        } else {
            -abs(step / 2)
        }
        bestPacking = packing ?: bestPacking
    }

    bestPacking!!
    width = bestPacking.atlasWidth
    height = bestPacking.atlasHeight

    step = -height / 2
    while (abs(step) >= discardStep) {
        height += step
        val packing = packWithSize(textures, width, height)
        step = if (packing == null) {
            abs(step / 2)
        } else {
            -abs(step / 2)
        }
        bestPacking = packing ?: bestPacking
    }

    return bestPacking
}

private fun packWithSize(textures: Iterable<Texture>, atlasWidth: Int, atlasHeight: Int): Packing? {
    val packedTextures = mutableListOf<Entry>()
    val freeRects = mutableListOf(XYWH(0, 0, atlasWidth, atlasHeight))
    textures@for (texture in textures) {
        // Search backwards through all free rects, so we try smaller ones first
        for (i in freeRects.lastIndex downTo 0) {
            val freeRect = freeRects[i]

            fun place(textureW: Int, textureH: Int, flipped: Boolean): Boolean {
                val remainingW = freeRect.w - textureW
                val remainingH = freeRect.h - textureH

                if (remainingW < 0 || remainingH < 0) {
                    return false // doesn't fit, try next one
                }

                // Texture fits into this free rect, place it
                packedTextures.add(Entry(texture, freeRect.x, freeRect.y, textureW, textureH, flipped))

                // Fits, remove the free rect
                // (by swapping with the last one so we don't need to shift the entire array)
                freeRects[i] = freeRects.last()
                freeRects.removeLast()

                when {
                    // Texture fills entire freeRect, nothing remains
                    remainingW == 0 && remainingH == 0 -> {}
                    // Texture fill entire width, add remaining height as new free rect
                    remainingW == 0 ->
                        freeRects.add(XYWH(freeRect.x, freeRect.y + textureH, freeRect.w, remainingH))
                    // Texture fill entire height, add remaining width as new free rect
                    remainingH == 0 ->
                        freeRects.add(XYWH(freeRect.x + textureW, freeRect.y, remainingW, freeRect.h))
                    // Texture fills neither width nor height, add remaining space as two free rects
                    else -> {
                        // Prefer one tiny and one huge free rect, assumption being that less space is wasted that way.
                        // Insert tiny one last so it is tried first for subsequent loops
                        if (remainingW > remainingH) {
                            // Large rect to the right of the texture
                            freeRects.add(XYWH(freeRect.x + textureW, freeRect.y, remainingW, freeRect.h))
                            // Small rect directly below the texture
                            freeRects.add(XYWH(freeRect.x, freeRect.y + textureH, textureW, remainingH))
                        } else {
                            // Large rect below the texture
                            freeRects.add(XYWH(freeRect.x, freeRect.y + textureH, freeRect.w, remainingH))
                            // Small rect directly to the right of the texture
                            freeRects.add(XYWH(freeRect.x + textureW, freeRect.y, remainingW, textureH))
                        }
                    }
                }

                return true
            }

            if (place(texture.width, texture.height, false)) {
                continue@textures
            }
            /* TODO implement
            if (place(texture.height, texture.width, true)) {
                continue@textures
            }
             */
        }

        // No fitting free space found, give up
        return null
    }
    return Packing(atlasWidth, atlasHeight, packedTextures)
}
