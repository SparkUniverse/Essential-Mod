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
package gg.essential.model.light

import kotlin.jvm.JvmInline

/** Value type containing sky and block light information at a single point. Equivalent to MC's packed light `int`s. */
@JvmInline
value class Light(val value: UInt) {
    constructor(blockLight: UShort, skyLight: UShort) : this(blockLight.toUInt() or (skyLight.toUInt() shl 16))

    val blockLight: UShort
        get() = value.toUShort()

    val skyLight: UShort
        get() = (value shr 16).toUShort()

    override fun toString(): String =
        "Light(block=$blockLight,sky=$skyLight)"

    companion object {
        /** Completely dark. */
        val MIN_VALUE = Light(0u, 0u)
        /** Fully bright. */
        val MAX_VALUE = Light(240u, 240u)
    }
}