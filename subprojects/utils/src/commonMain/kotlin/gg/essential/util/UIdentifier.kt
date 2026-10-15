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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmStatic

@Serializable(with = UIdentifier.Serializer::class)
data class UIdentifier(val namespace: String, val path: String) {
    override fun toString(): String {
        return "$namespace:$path"
    }

    fun toLegacyString(): String {
        return if (namespace.isNotEmpty()) "$namespace:$path" else path
    }

    fun toTranslationKey(prefix: String = "", suffix: String = ""): String {
        return buildString {
            if (prefix.isNotEmpty()) {
                append(prefix)
                append('.')
            }
            if (namespace.isNotEmpty()) {
                append(namespace)
                append('.')
            }
            append(path)
            if (suffix.isNotEmpty()) {
                append('.')
                append(suffix)
            }
        }
    }

    companion object {
        @JvmStatic
        fun of(str: String): UIdentifier {
            val (namespace, path) = str.split(':', limit = 2)
            return UIdentifier(namespace, path)
        }

        @JvmStatic
        fun ofLegacy(str: String): UIdentifier {
            return if (":" in str) of(str) else UIdentifier("", str)
        }
    }

    object Serializer : KSerializer<UIdentifier> {
        private val inner = String.serializer()
        override val descriptor: SerialDescriptor
            get() = inner.descriptor

        override fun serialize(encoder: Encoder, value: UIdentifier) {
            encoder.encodeSerializableValue(inner, value.toLegacyString())
        }

        override fun deserialize(decoder: Decoder): UIdentifier {
            return ofLegacy(decoder.decodeSerializableValue(inner))
        }
    }
}
