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
package gg.essential.mod.cosmetics

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

@Serializable(with = CosmeticSlot.Serializer::class)
data class CosmeticSlot private constructor(val id: String) {
    companion object {
        private val values = mutableListOf<CosmeticSlot>()
        private val entries = ConcurrentHashMap<String, CosmeticSlot>()

        fun of(id: String): CosmeticSlot {
            return entries.computeIfAbsent(id, ::CosmeticSlot)
        }

        private fun make(id: String): CosmeticSlot {
            return of(id).also { values.add(it) }
        }

        @JvmStatic
        fun values(): List<CosmeticSlot> = values

        @JvmField val BACK = make("BACK")
        @JvmField val EARS = make("EARS")
        @JvmField val FACE = make("FACE")
        @JvmField val FULL_BODY = make("FULL_BODY")
        @JvmField val HAT = make("HAT")
        @JvmField val PET = make("PET")
        @JvmField val TAIL = make("TAIL")
        @JvmField val ARMS = make("ARMS")
        @JvmField val SHOULDERS = make("SHOULDERS")
        @JvmField val SUITS = make("SUITS")
        @JvmField val SHOES = make("SHOES")
        @JvmField val PANTS = make("PANTS")
        @JvmField val WINGS = make("WINGS")
        @JvmField val EFFECT = make("EFFECT")
        @JvmField val CAPE = make("CAPE")
        @JvmField val EMOTE = make("EMOTE")
        @JvmField val ICON = make("ICON")
        @JvmField val TOP = make("TOP")
        @JvmField val ACCESSORY = make("ACCESSORY")
        @JvmField val HEAD = make("HEAD")
        @JvmField val SKIRT = make("SKIRT")
    }

    internal object Serializer : KSerializer<CosmeticSlot> {
        private val inner = String.serializer()
        override val descriptor: SerialDescriptor = inner.descriptor
        override fun deserialize(decoder: Decoder) = of(decoder.decodeSerializableValue(inner))
        override fun serialize(encoder: Encoder, value: CosmeticSlot) = encoder.encodeSerializableValue(inner, value.id)
    }
}
