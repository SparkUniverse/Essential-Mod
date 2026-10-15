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

import gg.essential.serialization.SnakeAsUpperCaseSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic

/**
 * Skin model.
 */
@Serializable
enum class Model(
    /** The internal name used by Minecraft to refer to this Model. */
    val type: String,
    /** The name used by Mojang services to refer to this Model (case insensitive). */
    val variant: String,
) {
    @SerialName("classic")
    STEVE("default", "classic"),
    @SerialName("slim")
    ALEX("slim", "slim");

    object UpperCase : SnakeAsUpperCaseSerializer<Model>(Model.serializer())

    companion object {
        @JvmStatic
        fun byType(str: String) = values().find { it.type == str }

        @JvmStatic
        fun byTypeOrDefault(str: String) = byType(str) ?: STEVE

        @JvmStatic
        fun byVariant(str: String) = values().find { it.variant.equals(str, ignoreCase = true) }

        @JvmStatic
        fun byVariantOrDefault(str: String) = byVariant(str) ?: STEVE
    }
}
