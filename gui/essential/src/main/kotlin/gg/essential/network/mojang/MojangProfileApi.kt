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
package gg.essential.network.mojang

import gg.essential.mod.Model
import gg.essential.util.ExponentialBackoff
import gg.essential.util.RateLimitException
import gg.essential.util.UuidAsDashlessStringSerializer
import gg.essential.util.executeAwait
import gg.essential.util.httpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * Provides a Kotlin interface to Mojang's "/minecraft/profile" API.
 *
 * All methods are completely unmanaged (no locking) and direct (no local caching or anything).
 * All methods are fully thread-safe.
 * Optionally ([retryOnRateLimit]), all methods may re-try with exponential backoff when rate-limited (though note that
 * each call does this independently!).
 *
 * You'll likely want to use the [ManagedMojangProfileApi] wrapper instead.
 */
class MojangProfileApi(
    val accessToken: String,
    val retryOnRateLimit: Boolean = false,
) {
    private val JSON = "application/json".toMediaType()
    private val URL_BASE = System.getProperty(
        "minecraft.api.services.host",
        "https://api.minecraftservices.com",
    ) + "/minecraft/profile"

    private suspend fun <T> call(
        context: CoroutineContext,
        block: suspend () -> T,
    ): T {
        val backoff = ExponentialBackoff(2.seconds, 60.seconds, 2.0)
        backoff.increment() // skip the initial 0 delay, Mojang doesn't emit 429 randomly
        while (true) {
            try {
                return withContext(context) { block() }
            } catch (e: RateLimitException) {
                if (!retryOnRateLimit) throw e
                delay(backoff.increment())
            }
        }
    }

    suspend fun fetch(): Profile = call(Dispatchers.IO) {
        val request = Request.Builder().apply {
            url(URL_BASE)
            header("Authorization", "Bearer $accessToken")
        }.build()

        httpClient().newCall(request).executeAwait().use { response ->
            decodeMojangResponse<Profile>(response)
        }
    }

    suspend fun putSkin(skin: gg.essential.mod.Skin?): Profile {
        return if (skin != null) {
            val image = call(Dispatchers.IO) {
                httpClient().newCall(Request.Builder().url(skin.url).build()).executeAwait().use { response ->
                    response.body.bytes()
                }
            }

            putSkin(image, skin.model)
        } else {
            call(Dispatchers.IO) {
                val request = Request.Builder().apply {
                    url("$URL_BASE/skins")
                    header("Authorization", "Bearer $accessToken")
                    delete()
                }.build()

                httpClient().newCall(request).executeAwait().use { response ->
                    decodeMojangResponse<Profile>(response)
                }
            }
        }
    }

    suspend fun putSkin(png: ByteArray, model: Model): Profile = call(Dispatchers.IO) {
        val request = Request.Builder().apply {
            url("$URL_BASE/skins")
            header("Authorization", "Bearer $accessToken")

            post(MultipartBody.Builder().apply {
                setType(MultipartBody.FORM)
                addFormDataPart("file", "skin.png", png.toRequestBody("image/png".toMediaType()))
                addFormDataPart("variant", model.variant)
            }.build())
        }.build()

        httpClient().newCall(request).executeAwait().use { response ->
            decodeMojangResponse<Profile>(response)
        }
    }

    suspend fun putCape(id: String?): Profile = call(Dispatchers.IO) {
        val request = Request.Builder().apply {
            url("$URL_BASE/capes/active")
            header("Authorization", "Bearer $accessToken")
            if (id != null) {
                @Serializable
                data class Payload(val capeId: String)
                val payload = json.encodeToString(Payload(id))
                put(payload.toRequestBody(JSON))
            } else {
                delete()
            }
        }.build()

        httpClient().newCall(request).executeAwait().use { response ->
            decodeMojangResponse<Profile>(response)
        }
    }

    @Serializable
    data class Profile(
        @Serializable(UuidAsDashlessStringSerializer::class)
        val id: UUID,
        val name: String,
        val skins: List<Skin>,
        val capes: List<Cape>,
    ) {
        val activeSkin: gg.essential.mod.Skin?
            get() = skins.find { it.active }?.skin
        val activeCape: Cape?
            get() = capes.find { it.active }
        val activeCapeId: String?
            get() = activeCape?.id
    }

    @Serializable
    data class Skin(
        val id: String,
        @SerialName("state")
        @Serializable(ActiveSerializer::class)
        val active: Boolean,
        val url: String,
        val textureKey: String,
        @Serializable(Model.UpperCase::class)
        val variant: Model,
    ) {
        val skin: gg.essential.mod.Skin
            get() = gg.essential.mod.Skin.fromUrl(url, variant)
    }

    @Serializable
    data class Cape(
        val id: String,
        @SerialName("state")
        @Serializable(ActiveSerializer::class)
        val active: Boolean,
        val url: String,
        @SerialName("alias")
        val name: String,
    ) {
        val hash: String
            get() = url.split("/texture/")[1]
    }

    private object ActiveSerializer : KSerializer<Boolean> {
        private val inner = String.serializer()
        override val descriptor: SerialDescriptor = inner.descriptor
        override fun deserialize(decoder: Decoder) =
            decoder.decodeSerializableValue(inner) == "ACTIVE"
        override fun serialize(encoder: Encoder, value: Boolean) =
            encoder.encodeSerializableValue(inner, if (value) "ACTIVE" else "INACTIVE")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
