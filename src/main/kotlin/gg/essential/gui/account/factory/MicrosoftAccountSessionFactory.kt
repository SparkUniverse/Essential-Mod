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
package gg.essential.gui.account.factory

import com.mojang.authlib.exceptions.InvalidCredentialsException
import gg.essential.Essential
import gg.essential.elementa.components.Window
import gg.essential.gui.EssentialPalette
import gg.essential.gui.account.MicrosoftUserAuthentication
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.notification.markdownBody
import gg.essential.lib.gson.*
import gg.essential.util.USession
import gg.essential.util.colored
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/** Provides sessions from Microsoft accounts (persisted in the given file, independent of official launcher). */
class MicrosoftAccountSessionFactory(private val savePath: Path, oldSavePath: Path) : ManagedSessionFactory {

    private val gson = GsonBuilder().apply {
        registerTypeAdapter(Instant::class.java, InstantSerializer())
    }.create()

    private val lock = ReentrantReadWriteLock()
    private val state = getState(savePath)

    init {
        // Check for old accounts path
        if (oldSavePath.exists()) {
            val oldState = getState(oldSavePath)
            // Migrate old path accounts to the new path
            LOGGER.info("Migrating microsoft accounts from old path: '$oldSavePath' to '$savePath'")
            state.accounts.addAll(
                oldState.accounts.filter { oldAccount ->
                    state.accounts.none { it.uuid == oldAccount.uuid }
                }
            )
            if (save()) {
                oldSavePath.deleteIfExists()
                oldSavePath.resolveSibling("${oldSavePath.fileName}.bak").deleteIfExists()
                LOGGER.info("Successfully migrated accounts")
            } else {
                LOGGER.error("Unable to migrate accounts")
            }
        }
    }

    private fun getState(path: Path): State {
        return if (Files.exists(path)) {
            try {
                Files.newBufferedReader(path).use {
                    gson.fromJson(it, State::class.java)
                }
            } catch (e: JsonSyntaxException) { // Caused by corrupted json
                try {
                    Files.move(
                        path,
                        path.resolveSibling("${path.fileName}.bak"),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (ignored: IOException) {
                    // If the backup fails we can't do anything about it
                }
                e.printStackTrace()
                Notifications.error("Account Error", "Failed to load accounts. Please add them again.")
                State(mutableListOf())
            }
        } else {
            State(mutableListOf())
        }
    }

    private fun save() = lock.write {

        val tempFile = Files.createTempFile(savePath.parent, "essential-microsoft-accounts", ".json")
        return@write try {
            Files.write(tempFile, gson.toJson(state).toByteArray())
            try {
                Files.move(tempFile, savePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: FileSystemException) {
                e.printStackTrace()
                Files.move(tempFile, savePath, StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            Notifications.error("Account Error", "Unable to save your accounts.")
            false
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    override fun remove(uuid: UUID): Unit = lock.write {
        val account = state.accounts.find { it.uuid == uuid } ?: return

        state.accounts.remove(account)
        save()

        // TODO: maybe log out instead of just letting it expire, then again, it should be fine to just expire
    }

    fun login(oAuthData: MicrosoftUserAuthentication.OAuthData): USession {
        val userAuthService = MicrosoftUserAuthentication()

        val (profile, token) = userAuthService.logInViaOAuth(oAuthData)
        val account = MicrosoftAccount(
            profile.id,
            profile.name,
            token,
            userAuthService
        )

        return lock.write {
            state.accounts.removeIf { it.uuid == profile.id }
            state.accounts.add(account)
            save()

            account.toSession()
        }
    }

    override fun refresh(session: USession, force: Boolean): USession = lock.write {
        val account = state.accounts.find { it.uuid == session.uuid } ?: throw InvalidCredentialsException()

        try {
            val (profile, token) = account.auth.logIn(forceRefresh = force)
            account.name = profile.name
            account.accessToken = token
        } finally {
            // Always save, even if we were unable to fully refresh the session (we might have
            // succeeded in refreshing parts of it).
            save()
        }

        account.toSession()
    }

    override val sessions: Map<UUID, USession>
        get() = lock.read {
            state.accounts.associate { it.uuid to it.toSession() }
        }

    private data class State(
        val accounts: MutableList<MicrosoftAccount>
    )

    private data class MicrosoftAccount(
        val uuid: UUID,
        var name: String,
        var accessToken: String,
        var auth: MicrosoftUserAuthentication
    ) {
        fun toSession() = USession(uuid, name, accessToken)
    }

    private class InstantSerializer : JsonSerializer<Instant>, JsonDeserializer<Instant> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Instant {
            json as JsonObject
            val seconds = json["seconds"].asLong
            val nanos = json["nanos"].asLong
            return Instant.ofEpochSecond(seconds, nanos)
        }

        override fun serialize(src: Instant, typeOfSrc: Type, context: JsonSerializationContext) = JsonObject().apply {
            add("seconds", context.serialize(src.epochSecond))
            add("nanos", context.serialize(src.nano))
        }
    }

    /**
     * If the user doesn't sign in to an account for 90 days, the root refresh token
     * of the chain will expire, and they will need to oauth again. To prevent this from
     * happening, we will automatically refresh the refresh token every 2 weeks.
     */
    fun refreshRefreshTokensIfNecessary() = lock.write {
        for (account in state.accounts) {
            val uuid = account.uuid
            account.auth.expiryTime?.let {
                val username = account.name
                Essential.logger.debug("$username $uuid expires $it")
                if (it.isBefore(Instant.now().plus(90 - 14, ChronoUnit.DAYS))) {
                    Essential.logger.info("Refreshing the refresh token for $username $uuid")
                    try {
                        account.auth.refreshRefreshToken()
                    } catch (e: InvalidCredentialsException) {
                        Window.enqueueRenderOperation {
                            Notifications.error("Account Error", "") {
                                markdownBody(
                                    "An error occurred while trying to keep " +
                                        "${username.replace("_", "\\_").colored(EssentialPalette.TEXT_HIGHLIGHT)} signed in. " +
                                        "Please add this account again."
                                )
                            }
                        }
                    } finally {
                        // Always save, even if we were unable to fully refresh the session (we might have
                        // succeeded in refreshing parts of it).
                        save()
                    }
                }
            }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MicrosoftAccountSessionFactory::class.java)
    }
}