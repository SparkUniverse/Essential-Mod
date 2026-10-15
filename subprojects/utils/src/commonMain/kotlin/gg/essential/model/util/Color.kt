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
package gg.essential.model.util

import dev.folomeev.kotgl.matrix.vectors.Vec4
import kotlin.jvm.JvmInline

/** Value type for a 32-bit (8 bits per channel) RGBA color value. The lowest byte contains the alpha value. */
@JvmInline
value class Color(val rgba: UInt) {
    constructor(
        r: UByte,
        g: UByte,
        b: UByte,
        a: UByte = 255u,
    ) : this((r.toUInt() shl 24) or (g.toUInt() shl 16) or (b.toUInt() shl 8) or a.toUInt())

    val r: UByte
        get() = (rgba shr 24).toUByte()
    val g: UByte
        get() = (rgba shr 16).toUByte()
    val b: UByte
        get() = (rgba shr 8).toUByte()
    val a: UByte
        get() = rgba.toUByte()

    val argb: UInt
        get() = (a.toUInt() shl 24) or (r.toUInt() shl 16) or (g.toUInt() shl 8) or b.toUInt()

    fun copy(r: UByte = this.r, g: UByte = this.g, b: UByte = this.b, a: UByte = this.a): Color {
        return Color(r, g, b, a)
    }

    override fun toString(): String =
        "Color($r, $g, $b, $a)"

    companion object {
        val WHITE = Color(0xffffffffu)
        val BLACK = Color(0x000000ffu)

        fun fromFloats(r: Float, g: Float, b: Float, a: Float) = Color(
            (r * 255f).toInt().toUByte(),
            (g * 255f).toInt().toUByte(),
            (b * 255f).toInt().toUByte(),
            (a * 255f).toInt().toUByte(),
        )

        fun fromVec(vec: Vec4) = fromFloats(vec.x, vec.y, vec.z, vec.w)

        fun rgba(value: UInt): Color = Color(value)

        fun argb(value: UInt) = Color(
            a = (value shr 24).toUByte(),
            r = (value shr 16).toUByte(),
            g = (value shr 8).toUByte(),
            b = value.toUByte(),
        )

    }
}
