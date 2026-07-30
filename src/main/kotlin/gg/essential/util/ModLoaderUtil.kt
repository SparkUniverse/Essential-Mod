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
import gg.essential.connectionmanager.common.packet.mod.ClientModsAnnouncePacket
import gg.essential.connectionmanager.common.packet.partner.PartneredMod
import gg.essential.data.VersionData
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.universal.UMinecraft
import kotlin.io.path.*

//#if FABRIC
//$$ import gg.essential.lib.gson.JsonObject
//$$ import java.util.jar.JarInputStream
//$$ import java.util.zip.ZipEntry
//$$ import net.fabricmc.loader.api.FabricLoader
//$$ import net.fabricmc.loader.api.ModContainer
//#endif

//#if FORGE
import net.minecraftforge.common.ForgeVersion
//#if MC>=11400
//$$ import kotlin.streams.toList
//$$ import net.minecraftforge.fml.loading.LoadingModList
//#else
import gg.essential.mixincompat.util.MixinCompatUtils
import net.minecraftforge.fml.common.Loader
//#endif
//#endif

//#if NEOFORGE
//$$ import net.neoforged.fml.loading.LoadingModList
//$$ import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion
//#endif

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile

object ModLoaderUtil {

    @JvmField
    val KNOWN_CLIENT_SIDE_MODS =
        listOf(
            "level_head",
            "skytils",
            "patcher",
            "text_overflow_scroll",
            "keystrokesmod",
            "autogg",
            "autotip",
            "hypixel_auto_complete",
            "tnttime",
            "nick_hider",
            "popup_events",
            "sk1er-bedwars_resource_display",
            "motionblurmod",
            "compactchat",
            "bossbar_customizer",
            "sk1er_fullscreen",
            "cpsmod",
            "combat_info",
            "victory_royale",
            "mousebindfix",
            "lobby_sounds",
            "autocorrect",
            "better_fps_limiter",
            "autogl",
            "item_optimization",
            "subtitles_mod",
            "case_commands",
            "autodab",
            "thanosmod",
            "command_patcher",
            "hypixel_join_hider_2",
            "cross_chat",
            "mouse_sensitivity_tweak",
            "20_20_20",
            "winter_weather",
            "Sk1er-UHCstars",
            "cape_editor",
            "ChromaHUD"
        )

    private val mutablePartnerMods = mutableStateOf<List<PartneredMod>?>(null)
    val partnerMods: State<List<PartneredMod>?> = mutablePartnerMods
    val partnerModIds = memo { partnerMods()?.map { it.id } }

    private val mutableAreModsLoaded = mutableStateOf(false)
    @JvmField
    val areModsLoaded: State<Boolean> = mutableAreModsLoaded

    val loadedPartnerMods = memo {
        if (!areModsLoaded()) return@memo null
        val mods = getMods()
        partnerMods()?.filter { partnerMod -> mods.any { it.id == partnerMod.id } }?.toSet()
    }
    @JvmField
    val loadedPartnerModIds = memo { loadedPartnerMods()?.map { it.id } }

    private val modpackId by lazy {
        val modpackProps = UMinecraft.getMinecraft().mcDataDir.toPath() / "config" / "essential-modpack.properties"
        if (!modpackProps.exists()) { return@lazy null }
        modpackProps.bufferedReader().use { reader ->
            return@lazy Properties().apply { load(reader) }.getProperty("modpackId")
        }
    }

    @JvmStatic
    fun createModsAnnouncePacket() = ClientModsAnnouncePacket(
        VersionData.getMinecraftVersion(),
        getPlatform(),
        getPlatformVersion(),
        modpackId
    )

    private fun getPlatform(): ClientModsAnnouncePacket.Platform {
        //#if NEOFORGE
        //$$ return ClientModsAnnouncePacket.Platform.NEOFORGE
        //#elseif FORGE
        return ClientModsAnnouncePacket.Platform.FORGE
        //#elseif FABRIC
        //$$ return ClientModsAnnouncePacket.Platform.FABRIC
        //#endif
    }

    @JvmStatic
    fun populatePartnerMods(list: List<PartneredMod>) {
        mutablePartnerMods.set(list)
    }

    @JvmStatic
    fun setModsLoaded() {
        mutableAreModsLoaded.set(true)
    }

    /**
     * Returns a list of current mods.
     */
    fun getMods(): List<ModInfo> {
        //#if FABRIC
        //$$ return FabricLoader.getInstance().allMods.filter { !(it.metadata.customValues["fabric-loom:generated"]?.asBoolean ?: false) }.mapNotNull { modContainer ->
        //$$     val modId = modContainer.metadata.id
        //$$     var sourceFile = modContainer.rootPath
        //$$     val modName = modContainer.metadata.name
        //$$     val version = modContainer.metadata.version.friendlyString
        //#else
        //#if MC>=11400
        //$$ return LoadingModList.get().modFiles.flatMap { fileInfo ->
        //$$     fileInfo.mods.map { fileInfo.file.filePath to it }
        //$$ }.mapNotNull { (sourceFileArg, modContainer) ->
        //$$     var sourceFile = sourceFileArg
        //$$     val modName = modContainer.displayName
        //#else
        return Loader.instance().indexedModList.values.mapNotNull { modContainer ->
            var sourceFile = modContainer.source.toPath()
            val modName = modContainer.name
        //#endif
            val modId = modContainer.modId
            val version = modContainer.version.toString() // Not itself a string in 1.16+
        //#endif
            try {

                // Ignore any base Forge and/or FML jar files - we do not care about them.
                when (modId.lowercase()) {
                    "forge", "fml" -> return@mapNotNull null
                }

                // ModLauncher's jar-in-jar file system inappropriately returns null for the file store
                // See net.minecraftforge.jarjar.nio.pathfs.PathFileSystemProvider.getFileStore
                if (Files.exists(sourceFile) && Files.getFileStore(sourceFile) == null) {
                    return@mapNotNull null // we don't care about inner jars, so we can simply skip these
                }

                if (sourceFile.isAbsolute && sourceFile.parent == null && sourceFile.fileStore().type() == "zipfs") {
                    // This is the root path in a ZIP file system.
                    // Could not find any nicer way to get the containing zip file :(
                    sourceFile = FileSystems.getDefault().getPath(sourceFile.fileSystem.toString())
                }
                if (!sourceFile.isRegularFile()) {
                    return@mapNotNull null
                }
                return@mapNotNull ModInfo(modId, modName, sourceFile, version)
            } catch (e: Throwable) {
                Essential.logger.error("Error occurred trying to find source file of $modId", e)
                return@mapNotNull ModInfo(modId, modName, null, version)
            }
        }
    }

    /**
     * Returns a list of mods depending on Essential
     * Please note that Forge 1.14+ is not supported and will return an empty map.
     */
    fun getModsDependingOnEssential() : List<ModInfo> {
        val dependsOnEssential = mutableListOf<ModInfo>()
        //#if FABRIC
        //$$ val findPathMethod = try {
        //$$     ModContainer::class.java.getMethod("findPath", String::class.java)
        //$$ } catch (_: NoSuchMethodException) { null }
        //$$ val getContainedMethod = try {
        //$$     ModContainer::class.java.getMethod("getContainedMods")
        //$$ } catch (_: NoSuchMethodException) { null }
        //$$
        //$$ fun ModContainer.findPath_(file: String): Path? {
        //$$     if (findPathMethod != null) {
        //$$         @Suppress("UNCHECKED_CAST")
        //$$         return (findPathMethod(this, file) as Optional<Path>).orElse(null)
        //$$     } else {
        //$$         return getPath(file).takeIf { it.exists() }
        //$$     }
        //$$ }
        //$$
        //$$ fun ModContainer.getContainedMods_(): Collection<ModContainer> {
        //$$     if (getContainedMethod == null) {
        //$$         return emptyList()
        //$$     }
        //$$     @Suppress("UNCHECKED_CAST")
        //$$     return getContainedMethod(this) as Collection<ModContainer>
        //$$ }
        //$$
        //$$ // Check through all mods for fabric.mod.json
        //$$ for (modInfo in getMods()) {
        //$$     try {
        //$$         val modId = modInfo.id
        //$$         val modContainer = FabricLoader.getInstance().getModContainer(modId).orElse(null) ?: continue
        //$$         if (modId == "essential" || modId == "essential-container") {
        //$$             continue
        //$$         }
        //$$         if (modContainer.getContainedMods_().any { it.metadata.id == "essential-loader" }) {
        //$$             dependsOnEssential += modInfo
        //$$             continue
        //$$         }
        //$$         // Find and open fabric.mod.json
        //$$         val jsonObject = (modContainer.findPath_("fabric.mod.json") ?: continue).inputStream().use {
        //$$             Essential.GSON.fromJson(String(it.readBytes()), JsonObject::class.java)
        //$$         }
        //$$         if (!jsonObject.has("jars")) {
        //$$             continue
        //$$         }
        //$$         // Get list of jars from fabric.mod.json
        //$$         val jarList = jsonObject.getAsJsonArray("jars")
        //$$             .filter { it.asJsonObject.has("file") }
        //$$             .map { it.asJsonObject.get("file").asString }
        //$$         for (jarPath in jarList) {
        //$$             try {
        //$$                 // Open up nested jar inside main mod jar
        //$$                 (modContainer.findPath_(jarPath) ?: continue).inputStream().use { nestedJarStream ->
        //$$                     JarInputStream(nestedJarStream).use { jarInputStream ->
        //$$                         var nestedEntry: ZipEntry?
        //$$                         while (jarInputStream.nextEntry.also { nestedEntry = it } != null) {
        //$$                             // Find fabric.mod.json inside nested jar
        //$$                             if (nestedEntry!!.name == "fabric.mod.json") {
        //$$                                 val json = Essential.GSON.fromJson(String(jarInputStream.readBytes()), JsonObject::class.java)
        //$$                                 // Check if fabric.mod.json has an id of "essential-loader", if true add to list
        //$$                                 if (json.has("id") && json.get("id").asString == "essential-loader") {
        //$$                                     dependsOnEssential += modInfo
        //$$                                 }
        //$$                                 break
        //$$                             }
        //$$                             jarInputStream.closeEntry()
        //$$                         }
        //$$                     }
        //$$                 }
        //$$             } catch (e: Exception) {
        //$$                 e.printStackTrace()
        //$$             }
        //$$         }
        //$$     } catch (e: Exception) {
        //$$         e.printStackTrace()
        //$$     }
        //$$ }
        //#else
        //#if MC<11400
        for (modInfo in getMods()) {
            val modPath = modInfo.path ?: continue
            JarFile(modPath.toFile()).use { jar ->
                if (MixinCompatUtils.dependsOnEssential(jar)) {
                    dependsOnEssential += modInfo
                }
            }
        }
        //#endif
        //#endif
        return dependsOnEssential
    }

    private fun getPlatformVersion(): String {
        //#if FABRIC
        //$$ val loader = FabricLoader.getInstance()
        //$$ try {
        //$$     return loader.allMods
        //$$         .map { it.metadata }
        //$$         .find { it.id == "fabricloader" || "fabricloader" in it.provides }
        //$$         ?.let { it.id + ":" + it.version.friendlyString }
        //$$         ?: "unknown"
        //$$ } catch (e: Throwable) {
        //$$     e.printStackTrace()
        //$$
        //$$     // Versions of quilt_loader prior to 0.17.4 do not properly support `provides`
        //$$     if (e is UnsupportedOperationException && e.message == "Provides cannot be represented as a Fabric construct") {
        //$$         val version = loader.getModContainer("quilt_loader")
        //$$             .orElse(null)
        //$$             ?.metadata
        //$$             ?.version
        //$$             ?.friendlyString
        //$$         return "quilt_loader:$version"
        //$$     }
        //$$
        //$$     return "error:${loader.javaClass.name}-${e.message}"
        //$$ }
        //#elseif MC>=11400
        //#if NEOFORGE
        //$$ return NeoForgeVersion.getVersion()
        //#else
        //$$ return ForgeVersion.getVersion()
        //#endif
        //#else
        return ForgeVersion.getBuildVersion().toString()
        //#endif
    }

    /**
     * This method is not safe to call in early init on legacy forge.
     * Test usage thoroughly.
     */
    fun isModLoaded(modId: String): Boolean {
        //#if FABRIC
        //$$ return FabricLoader.getInstance().isModLoaded(modId)
        //#elseif MC>=11400
        //$$ return LoadingModList.get().getModFileById(modId) != null
        //#else
        return modId in Loader.instance().indexedModList
        //#endif
    }

    /**
     * This method is not safe to call in early init on legacy forge.
     * Test usage thoroughly.
     */
    fun getModVersion(modId: String): String? {
        //#if FABRIC
        //$$ return FabricLoader.getInstance().getModContainer(modId).orElse(null)?.metadata?.version?.toString()
        //#elseif MC>=11400
        //$$ return LoadingModList.get().mods.find { it.modId == modId }?.version?.toString()
        //#else
        return Loader.instance().indexedModList[modId]?.version
        //#endif
    }

}