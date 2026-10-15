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
package gg.essential.util.image.bitmap.impl

import gg.essential.model.util.Color
import gg.essential.util.image.bitmap.MutableBitmap

/**
 * Represents a bitmap that is backed by an array of pixels.
 * These pixels are in the RGBA format.
 */
internal class IntArrayBitmap(
    override val width: Int,
    override val height: Int,
    private val pixelData: IntArray
) : MutableBitmap {
    /**
     * Creates an empty bitmap of a certain [width], and [height].
     */
    constructor(width: Int, height: Int) : this(width, height, IntArray(width * height))

    override fun get(x: Int, y: Int): Color {
        val pixel = pixelData[y * width + x]
        return Color(pixel.toUInt())
    }

    override fun set(x: Int, y: Int, color: Color) {
        pixelData[y * width + x] = color.rgba.toInt()
    }

    override fun mutableCopy(): MutableBitmap {
        return IntArrayBitmap(width, height, pixelData.copyOf())
    }
}