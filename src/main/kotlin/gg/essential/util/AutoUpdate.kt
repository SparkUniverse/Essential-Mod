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

import gg.essential.Essential
import gg.essential.config.EssentialConfig
import gg.essential.data.Changelog
import gg.essential.data.MenuData
import gg.essential.data.VersionData
import gg.essential.elementa.components.Window
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.lib.gson.Gson
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

//#if FABRIC
//$$ import net.fabricmc.loader.api.FabricLoader
//#endif

object AutoUpdate : AutoUpdateManager {

    private const val AUTO_UPDATE_KEY = "autoUpdate"
    private const val PENDING_UPDATE_VERSION_KEY = "pendingUpdateVersion"
    private const val PENDING_UPDATE_RESOLUTION_KEY = "pendingUpdateResolution"

    private val stage2Config = Paths.get(Essential.getInstance().baseDir.path, "loader", "stage1", getEssentialLoaderPlatform(), "stage2.${stage2GameVersion()}.properties")
    private val stage3Config = Paths.get(Essential.getInstance().baseDir.path, "essential-loader.properties")

    init {
        if (getContainerConfigValue(stage2Config, AUTO_UPDATE_KEY) == null) {
            updateConfig(stage2Config) {
                setProperty(AUTO_UPDATE_KEY, "with-prompt")
            }
        }
        if (getContainerConfigValue(stage3Config, AUTO_UPDATE_KEY) == null) {
            updateConfig(stage3Config) {
                setProperty(AUTO_UPDATE_KEY, "with-prompt")
            }
        }
    }

    var seenUpdateToast = false

    private val stage2AutoUpdate = getContainerConfigValue(stage2Config, AUTO_UPDATE_KEY)
    private val stage2PendingUpdateVersion = getContainerConfigValue(stage2Config, PENDING_UPDATE_VERSION_KEY)
    private val stage2PendingUpdateResolution = getContainerConfigValue(stage2Config, PENDING_UPDATE_RESOLUTION_KEY)
    private val stage2UpdateAvailable = stage2PendingUpdateVersion != null

    private val stage3AutoUpdate = getContainerConfigValue(stage3Config, AUTO_UPDATE_KEY)
    private val stage3PendingUpdateVersion = getContainerConfigValue(stage3Config, PENDING_UPDATE_VERSION_KEY)
    private val stage3PendingUpdateResolution = getContainerConfigValue(stage3Config, PENDING_UPDATE_RESOLUTION_KEY)
    private val stage3UpdateAvailable = stage3PendingUpdateVersion != null

    override val updateAvailable = mutableStateOf(stage3UpdateAvailable || stage2UpdateAvailable)
    override val updateIgnored = mutableStateOf(if (stage3UpdateAvailable) stage3PendingUpdateResolution == "false" else stage2PendingUpdateResolution == "false")
    val autoUpdate = mutableStateOf((stage3AutoUpdate ?: System.getProperty("essential.autoUpdate", "true")).toBoolean()
        || (stage2AutoUpdate ?: System.getProperty("essential.stage2.autoUpdate", "true")).toBoolean())

    var dismissUpdateToast : (() -> Unit)? = null
    override fun dismissUpdateNotification() {
        dismissUpdateToast?.invoke()
    }

    fun requiresUpdate() = Essential.getInstance().connectionManager.outdated
    override val isUpdateRequired: Boolean
        get() = requiresUpdate()

    override fun getNotificationTitle(includeLoaderText: Boolean) = if (requiresUpdate()) {
        "Update Required"
    } else if (!stage3UpdateAvailable && includeLoaderText) {
        "Loader Update Available"
    } else {
        "Update Available"
    }

    override val changelog: CompletableFuture<String?> by lazy {
        val version = (if (stage3UpdateAvailable) stage3PendingUpdateVersion else stage2PendingUpdateVersion)
            ?: return@lazy CompletableFuture.completedFuture(null)

        val changelogFuture: CompletableFuture<String?>

        if (stage3UpdateAvailable) {
            val newVersion = VersionData.getMajorComponents(version)
            val currentVersion = VersionData.getMajorComponents(VersionData.essentialVersion)
            val versionComponents = mutableListOf("0", "0", "0")

            for ((index, component) in newVersion.withIndex()) {
                versionComponents[index] = component
                if (index >= currentVersion.size || currentVersion[index] != component) {
                    break
                }
            }

            val displayVersion = versionComponents.joinToString(".")

            changelogFuture = MenuData.CHANGELOGS.get(if (versionComponents == currentVersion) version else displayVersion)
                .thenApplyAsync({ (_, log) -> log.summary }, Window::enqueueRenderOperation)
        } else {
            changelogFuture = CompletableFuture.supplyAsync {
                val encodedVersion = URLEncoder.encode(version, StandardCharsets.UTF_8.toString()).replace("+", "%20").replace("#", "%23")
                val versionResponse = httpGetToStringBlocking("${MenuData.BASE_URL}/mods/v1/essential:loader-stage2/versions/$encodedVersion/changelog")
                val changelog = Gson().fromJson(versionResponse, Changelog::class.java)
                changelog.summary
            }
        }

        changelogFuture.handleAsync({ summary, exception ->
            return@handleAsync exception?.run {
                Essential.logger.error("An error occurred fetching the changelog.", this)
                null
            } ?: summary
        }, Window::enqueueRenderOperation)
    }

    override fun acceptUpdate() {
        if (stage3UpdateAvailable) {
            updateConfig(stage3Config) {
                setProperty(PENDING_UPDATE_RESOLUTION_KEY, "true")
            }
        }
        if (stage2UpdateAvailable) {
            updateConfig(stage2Config) {
                setProperty(PENDING_UPDATE_RESOLUTION_KEY, "true")
            }
        }

        updateAvailable.set(false)
    }

    override fun ignoreUpdate() {
        if (stage3UpdateAvailable) {
            updateConfig(stage3Config) { setProperty(PENDING_UPDATE_RESOLUTION_KEY, "false") }
        }
        if (stage2UpdateAvailable) {
            updateConfig(stage2Config) { setProperty(PENDING_UPDATE_RESOLUTION_KEY, "false") }
        }
        updateIgnored.set(true)
    }

    fun setAutoUpdates(shouldAutoUpdate: Boolean) {
        val updateValue = if (shouldAutoUpdate) "true" else "with-prompt"
        updateConfig(stage3Config) {
            setProperty(AUTO_UPDATE_KEY, updateValue)
        }
        updateConfig(stage2Config) {
            setProperty(AUTO_UPDATE_KEY, updateValue)
        }
        autoUpdate.set(shouldAutoUpdate)

        // Keep the config value in sync when this method is called from the modal (or elsewhere)
        if (EssentialConfig.autoUpdate != shouldAutoUpdate) {
            EssentialConfig.autoUpdate = shouldAutoUpdate
        }
    }

    private fun getContainerConfigValue(config: Path, key: String): String? {
        if (!config.exists()) { return null }
        config.bufferedReader().use { reader ->
            return Properties().apply { load(reader) }.getProperty(key)
        }
    }

    private fun updateConfig(config: Path, update: Properties.() -> Unit) {
        config.parent.createDirectories()

        val tempFile = Files.createTempFile(config.parent, "temp_", null)

        Properties().apply {
            if (Files.exists(config)) {
                config.bufferedReader().use { load(it) }
            }
            update()
            tempFile.bufferedWriter().use { store(it, null) }
        }

        Files.move(tempFile, config, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun getEssentialLoaderPlatform(): String {
        //#if FABRIC
        //$$ return "fabric"
        //#else
        val version = VersionData.getMajorComponents(VersionData.getMinecraftVersion())[1].toInt()
        return when {
            version >= 17 -> "modlauncher9"
            version >= 14 -> "modlauncher8"
            else -> "launchwrapper"
        }
        //#endif
    }

    private fun stage2GameVersion(): String {
        //#if FABRIC
        //$$ return "fabric_" + FabricLoader.getInstance().getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("unknown")
        //#else
        val version = VersionData.getMajorComponents(VersionData.getMinecraftVersion())[1].toInt()
        return "forge_" + when {
            version >= 17 -> "1.17.1"
            version >= 14 -> "1.16.5"
            version >= 12 -> "1.12.2"
            else -> "1.8.9"
        }
        //#endif
    }
}
