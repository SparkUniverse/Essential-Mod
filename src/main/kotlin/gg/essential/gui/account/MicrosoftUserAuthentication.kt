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
package gg.essential.gui.account

import com.mojang.authlib.exceptions.InvalidCredentialsException
import gg.essential.minecraftauth.exception.AuthenticationException
import gg.essential.minecraftauth.microsoft.MicrosoftAuthenticationService
import gg.essential.minecraftauth.microsoft.response.MicrosoftAccessTokenResponse
import gg.essential.minecraftauth.minecraft.MinecraftAuthenticationService
import gg.essential.minecraftauth.xbox.XboxLiveAuthenticationService
import gg.essential.lib.gson.JsonDeserializationContext
import gg.essential.lib.gson.JsonDeserializer
import gg.essential.lib.gson.JsonElement
import gg.essential.lib.gson.JsonPrimitive
import gg.essential.lib.gson.JsonSerializationContext
import gg.essential.lib.gson.JsonSerializer
import gg.essential.lib.gson.annotations.JsonAdapter
import gg.essential.util.UUIDUtil
import java.lang.reflect.Type
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MicrosoftUserAuthentication {

    private var accessToken: Token? = null

    @field:JsonAdapter(LegacyTokenSerializer::class)
    private var refreshToken: Token? = null
    private var xblToken: Token? = null
    private var xstsToken: Token? = null
    private var uhs: String? = null
    private var mcToken: Token? = null
    private var profile: GameProfile? = null
    val expiryTime: Instant?
        get() = refreshToken?.expires

    class OAuthData(
        val codeVerifier: String,
        val redirectUri: String,
        val code: String,
    )

    fun logInViaOAuth(oAuthData: OAuthData): Pair<GameProfile, String> {
        // Acquires [accessToken] and [refreshToken] via OAuth
        acquireAccessTokenViaOAuth(oAuthData)
        // uses just-acquired [accessToken] to log in
        return logIn()
    }

    fun logIn(forceRefresh: Boolean = false): Pair<GameProfile, String> {
        if (forceRefresh) {
            mcToken = null
        }
        val profile = acquireGameProfile()
        val token = acquireMCToken()
        return Pair(profile, token)
    }

    fun refreshRefreshToken() {
        acquireAccessToken(true)
    }

    private fun acquireAccessToken(forceRefresh: Boolean): String {
        if (!forceRefresh) {
            accessToken.ifValid { return it }
        }

        val refreshToken = this.refreshToken
            ?: throw InvalidCredentialsException("Re-authentication with Microsoft required")
        val response = MicrosoftAuthenticationService.refreshAccessToken(refreshToken.value)
        saveMicrosoftTokens(response)

        return response.accessToken
    }

    private fun acquireAccessTokenViaOAuth(oAuthData: OAuthData): String {
        val codeVerifier = oAuthData.codeVerifier
        val redirectUri = oAuthData.redirectUri
        val code = oAuthData.code

        val response = MicrosoftAuthenticationService.exchangeCodeForAccessToken(code, codeVerifier, redirectUri)
        saveMicrosoftTokens(response)

        return response.accessToken
    }

    private fun saveMicrosoftTokens(response: MicrosoftAccessTokenResponse) {
        // Refresh tokens last 90 days https://docs.microsoft.com/en-us/azure/active-directory/develop/refresh-tokens
        this.refreshToken = Token(response.refreshToken, Instant.now() + Duration.ofDays(90))
        this.accessToken = Token(response.accessToken, Instant.now() + Duration.ofSeconds(response.expiresIn))
    }

    // https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_XBL
    private fun acquireXBLToken(retry: Boolean = true): String {
        xblToken.ifValid { return it }

        val accessToken = acquireAccessToken(false)

        val response = try {
            XboxLiveAuthenticationService.authenticateWithXboxLive(accessToken)
        } catch (e: AuthenticationException.InvalidCredentials) {
            if (retry) {
                this.accessToken = null
                return this.acquireXBLToken(false)
            } else {
                throw e
            }
        }

        val expires = Instant.parse(response.notAfter)
        this.xblToken = Token(response.token, expires)

        return response.token
    }

    private fun acquireXSTSToken(retry: Boolean = true): Pair<String, String> {
        xstsToken.ifValid { return Pair(it, uhs!!) }

        val xblToken = acquireXBLToken()

        val (response, userHash) = try {
            XboxLiveAuthenticationService.authenticateWithXSTS(xblToken)
        } catch (e: AuthenticationException.InvalidCredentials) {
            if (retry) {
                this.xblToken = null
                return this.acquireXSTSToken(false)
            } else {
                throw e
            }
        }

        val expires = Instant.parse(response.notAfter)

        this.uhs = userHash
        this.xstsToken = Token(response.token, expires)

        return response.token to userHash
    }

    private fun acquireMCToken(retry: Boolean = true): String {
        mcToken.ifValid { return it }

        val (xstsToken, uhs) = acquireXSTSToken()

        val response = try {
            MinecraftAuthenticationService.authenticateWithXbox(xstsToken, uhs)
        } catch (e: AuthenticationException.InvalidCredentials) {
            if (retry) {
                this.xstsToken = null
                return this.acquireMCToken(false)
            } else {
                throw e
            }
        }

        val token = response.accessToken
        val expiresIn = response.expiresIn

        mcToken = Token(token, Instant.now() + Duration.ofSeconds(expiresIn))

        // Since we have a new access token for Minecraft, the profile might be different.
        profile = null

        return token
    }

    private fun acquireGameProfile(): GameProfile {
        // We acquire the token before checking the cache so that we regularly refresh the profile.
        val token = acquireMCToken()
        profile?.let { return it }

        val response = MinecraftAuthenticationService.getProfile(token)
        val profile = GameProfile(UUIDUtil.formatWithDashes(response.id), response.name)

        this.profile = profile
        return profile
    }

    data class Token(val value: String, val expires: Instant) {
        val expired: Boolean
            get() = expires.isBefore(Instant.now())
    }

    private inline fun Token?.ifValid(block: (token: String) -> Unit) {
        if (this != null && !this.expired) {
            block(value)
        }
    }

    private class LegacyTokenSerializer : JsonSerializer<Token>, JsonDeserializer<Token> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Token {
            return when (json) {
                is JsonPrimitive -> Token(json.asString, Instant.now())
                else -> context.deserialize(json, Token::class.java)
            }
        }

        override fun serialize(src: Token, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
            context.serialize(src)
    }

    data class GameProfile(val id: UUID, val name: String)
}
