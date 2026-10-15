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
package gg.essential.mod

import gg.essential.model.util.base64Encode
import gg.essential.model.util.md5Hex
import kotlinx.serialization.Serializable

@Serializable
data class EssentialAsset(
    val url: String,
    val checksum: String,
) {
    companion object {
        val EMPTY = EssentialAsset("data:,", "d41d8cd98f00b204e9800998ecf8427e")

        fun of(content: String): EssentialAsset = of(content.encodeToByteArray())
        fun of(content: ByteArray): EssentialAsset =
            EssentialAsset("data:;base64," + base64Encode(content), md5Hex(content))

    }
}
