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
package gg.essential.data

import gg.essential.api.data.OnboardingData
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.globalEssentialDirectory
import gg.essential.util.minecraftDirectory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object OnboardingData : OnboardingData {
    private val LOGGER = LoggerFactory.getLogger(javaClass)
    private val localFile = platform.essentialBaseDir / "onboarding.json"
    private val globalFile = globalEssentialDirectory / "onboarding.json"
    private val oldGlobalFile = minecraftDirectory.toPath() / "essential" / "onboarding.json"
    private val referenceHolder = ReferenceHolderImpl()
    private val state = mutableStateOf(State())
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    val acceptedTos = memo { state().acceptedTos == true }

    val seenServerDiscovery = state.map { it.seenServerDiscovery }

    init {
        when {
            Files.exists(oldGlobalFile) -> {
                // If the old global file exists, load it and write its contents to all other files
                tryLoadData(oldGlobalFile)
                saveData()
            }
            Files.exists(globalFile) -> tryLoadData(globalFile)
            Files.exists(localFile) -> {
                // Load local data and save to both files
                tryLoadData(localFile)
                saveData()
            }
            else -> {
                // Save default state to both files
                saveData()
            }
        }

        state.onChange(referenceHolder) {
            saveData()
        }
    }

    private fun getData() = state.getUntracked()

    @JvmStatic
    fun hasAcceptedTos(): Boolean {
        return getData().acceptedTos == true
    }

    @JvmStatic
    fun hasDeniedTos(): Boolean {
        return getData().acceptedTos == false
    }

    @JvmStatic
    fun hasShownWikiToast(): Boolean {
        return getData().hasShownWikiToast
    }

    @JvmStatic
    fun setHasShownWikiToast() {
        state.set { it.copy(hasShownWikiToast = true) }
    }

    @JvmStatic
    fun setAcceptedTos() {
        state.set { it.copy(acceptedTos = true) }
    }

    @JvmStatic
    fun setDeniedTos() {
        state.set { it.copy(acceptedTos = false) }
    }

    @JvmStatic
    fun hasSeenFriendsOption(): Boolean {
        return getData().seenFriendsOption
    }

    @JvmStatic
    fun setSeenFriendsOption() {
        state.set { it.copy(seenFriendsOption = true) }
    }

    @JvmStatic
    fun setSeenServerDiscovery() {
        state.set { it.copy(seenServerDiscovery = true) }
    }

    @JvmStatic
    fun hasSentAutoUpdateTelemetryBefore(): Boolean {
        return getData().sentAutoUpdateTelemetry
    }

    @JvmStatic
    fun setSentAutoUpdateTelemetry() {
        state.set { it.copy(sentAutoUpdateTelemetry = true) }
    }

    override fun hasAcceptedEssentialTOS(): Boolean = hasAcceptedTos()

    override fun hasDeniedEssentialTOS(): Boolean = hasDeniedTos()

    private fun tryLoadData(file: Path) {
        try {
            state.set(json.decodeFromString<State>(file.readText()))
        } catch (exception: Exception) {
            LOGGER.error("Failed to read from Onboarding JSON, rewriting file.", exception)
            saveData() // Rewrite the config with default state
        }
    }

    private fun saveData() {
        val data = getData()
        localFile.writeText(json.encodeToString(data))

        // Only write to the old global file if it exists.
        if (oldGlobalFile.exists()) {
            oldGlobalFile.writeText(json.encodeToString(data))
        }

        try {
            globalFile.parent.createDirectories()
            globalFile.writeText(json.encodeToString(data))
        } catch (exception: Exception) {
            LOGGER.error("Failed to save global Onboarding file.", exception)
        }
    }

    @Serializable
    private data class State(
        @SerialName("accepted_tos")
        val acceptedTos: Boolean? = null,
        @SerialName("seen_share_server_with_friends_option")
        val seenFriendsOption: Boolean = false,
        @SerialName("has_shown_wiki_toast")
        val hasShownWikiToast: Boolean = false,
        @SerialName("seen_server_discovery")
        val seenServerDiscovery: Boolean = false,
        @SerialName("sent_auto_update_telemetry")
        val sentAutoUpdateTelemetry: Boolean = false,
    )
}
