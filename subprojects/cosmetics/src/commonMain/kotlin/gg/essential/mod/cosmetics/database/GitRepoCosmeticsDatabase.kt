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
@file:UseSerializers(InstantAsIso8601Serializer::class)

package gg.essential.mod.cosmetics.database

import gg.essential.cosmetics.CosmeticBundleId
import gg.essential.cosmetics.CosmeticCategoryId
import gg.essential.cosmetics.CosmeticId
import gg.essential.cosmetics.FeaturedPageCollectionId
import gg.essential.cosmetics.FeaturedPageWidth
import gg.essential.cosmetics.ImplicitOwnership
import gg.essential.cosmetics.ImplicitOwnershipId
import gg.essential.mod.EssentialAsset
import gg.essential.mod.cosmetics.CosmeticAssets
import gg.essential.mod.cosmetics.CosmeticBundle
import gg.essential.mod.cosmetics.CosmeticCategory
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.CosmeticTier
import gg.essential.mod.cosmetics.featured.FeaturedPage
import gg.essential.mod.cosmetics.featured.FeaturedPageCollection
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticSetting
import gg.essential.model.Side
import gg.essential.model.util.Instant
import gg.essential.model.util.InstantAsIso8601Serializer
import gg.essential.model.util.base64Decode
import gg.essential.model.util.instant
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.network.cosmetics.CosmeticBase
import gg.essential.network.cosmetics.CosmeticStoreInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.plus
import kotlin.jvm.JvmInline

/**
 * Loads cosmetics from a local clone of the cosmetics git repository.
 *
 * Works with a simple file system abstraction to allow it to work in the browser as well.
 */
class GitRepoCosmeticsDatabase(
    /**
     * If `false`, all cosmetics will be loaded during [addFiles], otherwise they will only be loaded as requested by
     * [getCosmetic] or [getCosmetics] (which will load all cosmetics when called; to access already-loaded cosmetics,
     * use [loadedCosmetics]).
     *
     * Categories and types are always loaded eagerly.
     */
    private val lazy: Boolean,
    /**
     * Constructs an [EssentialAsset] for the given path.
     *
     * The default implementation simply encodes the content of the file as a base64 `data:` url.
     */
    val assetFromPath: AssetBuilder = { _, read ->
        EssentialAsset.of(read())
    },
    /**
     * Retrieves the content of a given asset.
     * Used by the [computeChanges] methods to retrieve assets which have changed.
     * The default implementation supports only base64 data: urls.
     */
    val fetchAsset: suspend (EssentialAsset) -> ByteArray = { asset ->
        val url = asset.url
        if (!url.startsWith(dataUrlBase64Prefix)) {
            throw UnsupportedOperationException("Can only fetch base64 data: urls")
        }
        base64Decode(url.removePrefix(dataUrlBase64Prefix))
    },
    private val json: Json = Json {
        prettyPrint = true
        serializersModule = CosmeticProperty.TheSerializer.module + CosmeticSetting.TheSerializer.module
    },
) : CosmeticsDatabase {

    private val files = mutableMapOf<Path, suspend () -> ByteArray>()
    private val filesByFolders = mutableMapOf<Path, MutableSet<Path>>()
    private val fileObservers = mutableMapOf<Path, Observers>()
    private val observedFiles = mutableMapOf<Path, MutableSet<Path>>()
    private val folderObservers = mutableMapOf<Path, Observers>()
    private val observedFolders = mutableMapOf<Path, MutableSet<Path>>()

    private val categories = mutableMapOf<CosmeticCategoryId, CosmeticCategory>()
    private val bundles = mutableMapOf<CosmeticBundleId, CosmeticBundle>()
    private val featuredPageCollections = mutableMapOf<FeaturedPageCollectionId, FeaturedPageCollection>()
    private val implicitOwnerships = mutableMapOf<ImplicitOwnershipId, CosmeticImplicitOwnership>()
    private val cosmetics = mutableMapOf<CosmeticId, Cosmetic>()
    private val lazyCosmetics = mutableMapOf<CosmeticId, Path>()

    private val categoryByPath = mutableMapOf<Path, CosmeticCategoryId>()
    private val bundleByPath = mutableMapOf<Path, CosmeticBundleId>()
    private val featuredPageCollectionByPath = mutableMapOf<Path, FeaturedPageCollectionId>()
    private val implicitOwnershipsByPath = mutableMapOf<Path, ImplicitOwnershipId>()
    private val cosmeticByPath = mutableMapOf<Path, CosmeticId>()

    val loadedCategories: Map<CosmeticCategoryId, CosmeticCategory>
        get() = categories
    val loadedBundles: Map<CosmeticBundleId, CosmeticBundle>
        get() = bundles
    val loadedFeaturedPageCollections: Map<FeaturedPageCollectionId, FeaturedPageCollection>
        get() = featuredPageCollections
    val loadedImplicitOwnerships: Map<ImplicitOwnershipId, CosmeticImplicitOwnership>
        get() = implicitOwnerships
    val loadedCosmetics: Map<CosmeticId, Cosmetic>
        get() = cosmetics

    suspend fun addFiles(files: Map<String, suspend () -> ByteArray>): Changes {
        val newPaths = files.mapKeys { Path.of(it.key) }

        this.files.putAll(newPaths)
        for (path in newPaths.keys) {
            var parent = path.parent
            while (true) {
                filesByFolders.getOrPut(parent) { mutableSetOf() }.add(path)
                if (parent.isEmpty()) {
                    break
                }
                parent = parent.parent
            }
        }

        for ((file, read) in newPaths) {
            if (file.str.endsWith(".category-metadata.json")) {
                fileObservers.getOrPut(file, ::Observers).categories.add(file)
            }
            if (file.str.endsWith(".store-bundle-metadata.json")) {
                fileObservers.getOrPut(file, ::Observers).bundles.add(file)
            }
            if (file.str.endsWith(".featured-page-metadata.json")) {
                fileObservers.getOrPut(file, ::Observers).featuredPageCollections.add(file)
            }
            if (file.str.endsWith(".implicit-ownership.json")) {
                fileObservers.getOrPut(file, ::Observers).implicitOwnerships.add(file)
            }
            if (file.str.endsWith(".cosmetic-metadata.json")) {
                if (lazy && fileObservers[file]?.cosmetics?.contains(file) != true) {
                    val metadata = json.decodeFromString<CosmeticMetadataVUnknown>(read().decodeToString())
                    val id = metadata.override.id ?: file.parent.name.uppercase()
                    lazyCosmetics[id] = file
                } else {
                    fileObservers.getOrPut(file, ::Observers).cosmetics.add(file)
                }
            }
        }

        return updateFiles(newPaths.keys)
    }

    suspend fun removeFiles(filesOrFolders: Set<String>): Changes {
        val removedFilesOrFolders = filesOrFolders.map { Path.of(it) }
        val removedFiles = this.files.keys.filter { knownFile ->
            knownFile in removedFilesOrFolders || removedFilesOrFolders.any { knownFile.isIn(it) }
        }

        removedFiles.forEach { this.files.remove(it) }
        for (path in removedFiles) {
            var parent = path.parent
            while (true) {
                val entries = filesByFolders[parent]
                if (entries != null) {
                    entries.remove(path)
                    if (entries.isEmpty()) {
                        filesByFolders.remove(parent)
                    }
                }
                if (parent.isEmpty()) {
                    break
                }
                parent = parent.parent
            }
        }

        return updateFiles(removedFiles)
    }

    private suspend fun updateFiles(files: Collection<Path>): Changes {
        val categoriesChanged = mutableSetOf<CosmeticCategoryId>()
        val cosmeticsChanged = mutableSetOf<CosmeticId>()
        val bundlesChanged = mutableSetOf<CosmeticBundleId>()
        val featuredPagesCollectionsChanged = mutableSetOf<FeaturedPageCollectionId>()
        val implicitOwnershipsChanged = mutableSetOf<ImplicitOwnershipId>()

        suspend fun updateObservers(observers: Observers) {
            observers.categories.toList().mapNotNullTo(categoriesChanged) { path ->
                val category = tryLoadCategory(path)
                if (category != null) {
                    categories[category.id] = category
                    categoryByPath[path] = category.id
                    category.id
                } else {
                    val categoryId = categoryByPath.remove(path)
                    if (categoryId != null) {
                        categories.remove(categoryId)
                        categoryId
                    } else {
                        null
                    }
                }
            }

            observers.bundles.toList().mapNotNullTo(bundlesChanged) { path ->
                val bundle = tryLoadBundle(path)
                if (bundle != null) {
                    bundles[bundle.id] = bundle
                    bundleByPath[path] = bundle.id
                    bundle.id
                } else {
                    val bundleId = bundleByPath.remove(path)
                    if (bundleId != null) {
                        bundles.remove(bundleId)
                        bundleId
                    } else {
                        null
                    }
                }
            }

            observers.featuredPageCollections.toList().mapNotNullTo(featuredPagesCollectionsChanged) { path ->
                val featuredPageCollection = tryLoadFeaturedPageCollection(path)
                if (featuredPageCollection != null) {
                    featuredPageCollections[featuredPageCollection.id] = featuredPageCollection
                    featuredPageCollectionByPath[path] = featuredPageCollection.id
                    featuredPageCollection.id
                } else {
                    val width = featuredPageCollectionByPath.remove(path)
                    if (width != null) {
                        featuredPageCollections.remove(width)
                        width
                    } else {
                        null
                    }
                }
            }

            observers.implicitOwnerships.toList().mapNotNullTo(implicitOwnershipsChanged) { path ->
                val implicitOwnership = tryLoadImplicitOwnership(path)
                if (implicitOwnership != null) {
                    implicitOwnerships[implicitOwnership.id] = implicitOwnership
                    implicitOwnershipsByPath[path] = implicitOwnership.id
                    implicitOwnership.id
                } else {
                    val width = implicitOwnershipsByPath.remove(path)
                    if (width != null) {
                        implicitOwnerships.remove(width)
                        width
                    } else {
                        null
                    }
                }
            }

            observers.cosmetics.toList().flatMapTo(cosmeticsChanged) { path ->
                val cosmetic = tryLoadCosmetic(path)
                if (cosmetic != null) {
                    val oldId = cosmeticByPath.remove(path)
                    if (oldId != null) {
                        cosmetics.remove(oldId)
                    }
                    cosmetics[cosmetic.id] = cosmetic
                    cosmeticByPath[path] = cosmetic.id
                    lazyCosmetics.remove(cosmetic.id)
                    listOfNotNull(oldId, cosmetic.id)
                } else {
                    val cosmeticId = cosmeticByPath.remove(path)
                    if (cosmeticId != null) {
                        cosmetics.remove(cosmeticId)
                        listOf(cosmeticId)
                    } else {
                        emptyList()
                    }
                }
            }
        }

        for (file in files) {
            fileObservers[file]?.let { updateObservers(it) }

            var folder = file
            while (!folder.isEmpty()) {
                folderObservers[folder]?.let { updateObservers(it) }
                folder = folder.parent
            }
        }

        return Changes(
            categoriesChanged,
            cosmeticsChanged,
            bundlesChanged,
            featuredPagesCollectionsChanged,
            implicitOwnershipsChanged,
        )
    }

    private suspend fun tryLoadCategory(metadataFile: Path): CosmeticCategory? {
        if (metadataFile !in files) return null
        return try {
            val fileAccess = FileAccessImpl(metadataFile) { categories }
            fileAccess.loadCategory(json, metadataFile, assetFromPath)
        } catch (e: Exception) {
            Exception("Failed to load category at $metadataFile", e).printStackTrace()
            null
        }
    }

    private suspend fun tryLoadBundle(metadataFile: Path): CosmeticBundle? {
        if (metadataFile !in files) return null
        return try {
            val fileAccess = FileAccessImpl(metadataFile) { bundles }
            fileAccess.loadBundle(json, metadataFile)
        } catch (e: Exception) {
            Exception("Failed to load bundle at $metadataFile", e).printStackTrace()
            null
        }
    }

    private suspend fun tryLoadFeaturedPageCollection(metadataFile: Path): FeaturedPageCollection? {
        if (metadataFile !in files) return null
        return try {
            val fileAccess = FileAccessImpl(metadataFile) { featuredPageCollections }
            fileAccess.loadFeaturedPageCollection(json, metadataFile)
        } catch (e: Exception) {
            Exception("Failed to load featured page collection at $metadataFile", e).printStackTrace()
            null
        }
    }

    private suspend fun tryLoadImplicitOwnership(metadataFile: Path): CosmeticImplicitOwnership? {
        if (metadataFile !in files) return null
        return try {
            val fileAccess = FileAccessImpl(metadataFile) { implicitOwnerships }
            fileAccess.loadImplicitOwnership(json, metadataFile)
        } catch (e: Exception) {
            Exception("Failed to load implicit ownership at $metadataFile", e).printStackTrace()
            null
        }
    }

    private suspend fun tryLoadCosmetic(metadataFile: Path): Cosmetic? {
        if (metadataFile !in files) return null
        return try {
            val fileAccess = FileAccessImpl(metadataFile) { cosmetics }
            fileAccess.loadCosmetic(json, metadataFile, assetFromPath)
        } catch (e: Exception) {
            Exception("Failed to load cosmetic at $metadataFile", e).printStackTrace()
            makeErrorCosmetic(metadataFile, Cosmetic.Diagnostic.fatal(
                e.message ?: "Unexpected error",
                stacktrace = e.stackTraceToString(),
                file = metadataFile.name,
            ))
        }
    }

    /**
     * Computes the file changes necessary to update the category with [id] to match the given [category] data.
     * Returns a map of paths relative to the repository root with associated file data (or null if the file is to be
     * deleted).
     *
     * If no such category exists, a new one is created.
     * If the passed category data is `null`, the existing category with [id] (if any) will be deleted.
     *
     * Note that does by itself not apply these changes. It is the responsibility of the caller to apply the returned
     * values to the real file system and to call [updateFiles] if they wish these changes to be reflected in the state
     * of this [GitRepoCosmeticsDatabase].
     */
    suspend fun computeChanges(id: CosmeticCategoryId, category: CosmeticCategory?): Map<String, ByteArray?> {
        val originalCategory = categories[id]

        val existingMetadataFile = categoryByPath.entries.firstNotNullOfOrNull { if (it.value == id) it.key else null }
        val metadataFile = when {
            existingMetadataFile != null -> existingMetadataFile
            category != null -> Path.of("configuration/categories/${id.lowercase()}.category-metadata.json")
            else -> return emptyMap()
        }
        val folder = metadataFile.parent

        val originalMetadata = files[metadataFile]
            ?.let { json.decodeFromString<CategoryMetadata>(it().decodeToString()) }

        val changes = mutableMapOf<Path, ByteArray?>()

        val metadata = if (category != null) {
            CategoryMetadata(
                1,
                category.id,
                category.displayNames,
                category.compactNames,
                category.descriptions,
                category.slots,
                category.tags,
                category.order,
                category.availableAfter,
                category.availableUntil,
                originalMetadata?.override ?: CategoryMetadata.Overrides(),
            )
        } else {
            null
        }

        if (metadata != originalMetadata) {
            changes[metadataFile] = metadata?.let { json.encodeToString(it).encodeToByteArray() }
        }

        return changes.mapKeys { it.key.str }
    }

    /**
     * Computes the file changes necessary to update the bundle with [id] to match the given [bundle] data.
     * Returns a map of paths relative to the repository root with associated file data (or null if the file is to be
     * deleted).
     *
     * If no such bundle exists, a new one is created.
     * If the passed bundle data is `null`, the existing bundle with [id] (if any) will be deleted.
     *
     * Note that does by itself not apply these changes. It is the responsibility of the caller to apply the returned
     * values to the real file system and to call [updateFiles] if they wish these changes to be reflected in the state
     * of this [GitRepoCosmeticsDatabase].
     */
    suspend fun computeChanges(id: CosmeticBundleId, bundle: CosmeticBundle?): Map<String, ByteArray?> {
        val existingMetadataFile = bundleByPath.entries.firstNotNullOfOrNull { if (it.value == id) it.key else null }
        val metadataFile = when {
            existingMetadataFile != null -> existingMetadataFile
            bundle != null -> Path.of("store_bundles/${id.lowercase()}.store-bundle-metadata.json")
            else -> return emptyMap()
        }

        val originalMetadata = files[metadataFile]
            ?.let { json.decodeFromString<BundleMetadata>(it().decodeToString()) }

        val changes = mutableMapOf<Path, ByteArray?>()

        val metadata = if (bundle != null) {
            BundleMetadata(
                1,
                bundle.id,
                bundle.name,
                bundle.tier,
                bundle.discountPercent,
                bundle.rotateOnPreview,
                bundle.skin,
                bundle.cosmetics,
                bundle.settings,
            )
        } else {
            null
        }

        if (metadata != originalMetadata) {
            changes[metadataFile] = metadata?.let { json.encodeToString(it).encodeToByteArray() }
        }

        return changes.mapKeys { it.key.str }
    }

    /**
     * Computes the file changes necessary to update the featured page collection with [id] to match the given [featuredPageCollection] data.
     * Returns a map of paths relative to the repository root with associated file data (or null if the file is to be
     * deleted).
     *
     * If no such featured page collection exists, a new one is created.
     * If the passed featured page collection data is `null`, the existing featured collection with [id] (if any) will be deleted.
     *
     * Note that does by itself not apply these changes. It is the responsibility of the caller to apply the returned
     * values to the real file system and to call [updateFiles] if they wish these changes to be reflected in the state
     * of this [GitRepoCosmeticsDatabase].
     */
    suspend fun computeChanges(id: FeaturedPageCollectionId, featuredPageCollection: FeaturedPageCollection?): Map<String, ByteArray?> {
        val existingMetadataFile = featuredPageCollectionByPath.entries.firstNotNullOfOrNull { if (it.value == id) it.key else null }
        val metadataFile = when {
            existingMetadataFile != null -> existingMetadataFile
            featuredPageCollection != null -> Path.of("featured/$id.featured-page-metadata.json")
            else -> return emptyMap()
        }

        val originalMetadata = files[metadataFile]
            ?.let { json.decodeFromString<FeaturedPageCollectionMetadata>(it().decodeToString()) }

        val changes = mutableMapOf<Path, ByteArray?>()

        val metadata = if (featuredPageCollection != null) {
            FeaturedPageCollectionMetadata(
                1,
                featuredPageCollection.id,
                featuredPageCollection.availability?.let { FeaturedPageCollectionMetadata.Availability(it.after, it.until, it.showTimerAfter) },
                featuredPageCollection.pages,
            )
        } else {
            null
        }

        if (metadata != originalMetadata) {
            changes[metadataFile] = metadata?.let { json.encodeToString(it).encodeToByteArray() }
        }

        return changes.mapKeys { it.key.str }
    }

    /**
     * Computes the file changes necessary to update the implicit ownership with [id] to match the given [implicitOwnership] data.
     * Returns a map of paths relative to the repository root with associated file data (or null if the file is to be
     * deleted).
     *
     * If no such implicit ownership exists, a new one is created.
     * If the passed implicit ownership data is `null`, the existing implicit ownership with [id] (if any) will be deleted.
     *
     * Note that does by itself not apply these changes. It is the responsibility of the caller to apply the returned
     * values to the real file system and to call [updateFiles] if they wish these changes to be reflected in the state
     * of this [GitRepoCosmeticsDatabase].
     */
    suspend fun computeChanges(id: ImplicitOwnershipId, implicitOwnership: CosmeticImplicitOwnership?): Map<String, ByteArray?> {
        val existingMetadataFile = implicitOwnershipsByPath.entries.firstNotNullOfOrNull { if (it.value == id) it.key else null }
        val metadataFile = when {
            existingMetadataFile != null -> existingMetadataFile
            implicitOwnership != null -> Path.of("implicit_ownership/$id.implicit-ownership.json")
            else -> return emptyMap()
        }

        val original = files[metadataFile]
            ?.let { json.decodeFromString<ImplicitOwnership>(it().decodeToString()) }

        val changes = mutableMapOf<Path, ByteArray?>()

        if (implicitOwnership != original) {
            changes[metadataFile] = implicitOwnership?.let { json.encodeToString(it).encodeToByteArray() }
        }

        return changes.mapKeys { it.key.str }
    }

    /**
     * Computes the file changes necessary to update the cosmetic with [id] to match the given [cosmetic] data.
     * Returns a map of paths relative to the repository root with associated file data (or null if the file is to be
     * deleted).
     *
     * If no such cosmetic exists, a new one is created.
     * If the passed cosmetic data is `null`, the existing cosmetic with [id] (if any) will be deleted.
     *
     * This method does not update the data of the type which is referenced by this cosmetic, only the reference itself.
     *
     * Note that does by itself not apply these changes. It is the responsibility of the caller to apply the returned
     * values to the real file system and to call [updateFiles] if they wish these changes to be reflected in the state
     * of this [GitRepoCosmeticsDatabase].
     */
    suspend fun computeChanges(id: CosmeticId, cosmetic: Cosmetic?): Map<String, ByteArray?> {
        val originalCosmetic = cosmetics[id]
        val existingMetadataFile = cosmeticByPath.entries.firstNotNullOfOrNull { if (it.value == id) it.key else null }
        return if (originalCosmetic != null && cosmetic != null && originalCosmetic.slot != cosmetic.slot) {
            // If the slot has changed, we need to delete the original and re-create it in the new folder
            computeChanges(id, null, originalCosmetic, existingMetadataFile) + computeChanges(id, cosmetic, null, null)
        } else {
            computeChanges(id, cosmetic, originalCosmetic, existingMetadataFile)
        }
    }

    private suspend fun computeChanges(
        id: CosmeticId,
        cosmetic: Cosmetic?,
        originalCosmetic: Cosmetic?,
        existingMetadataFile: Path?,
    ): Map<String, ByteArray?> {
        val metadataFile = when {
            existingMetadataFile != null -> existingMetadataFile
            cosmetic != null -> {
                val root = if (cosmetic.slot == CosmeticSlot.EMOTE) "emotes" else "cosmetics"
                val slot = if (cosmetic.slot == CosmeticSlot.EMOTE) "basic" else cosmetic.slot.id.lowercase()
                Path.of("$root/$slot/${id.lowercase()}/${id.lowercase()}.cosmetic-metadata.json")
            }
            else -> return emptyMap()
        }

        val folder = metadataFile.parent
        val fileId = folder.name

        val originalMetadataUnknownVersion = files[metadataFile]?.let { jsonWithIgnoreUnknownKeys.decodeFromString<CosmeticMetadataVUnknown>(it().decodeToString()) }

        val changes = mutableMapOf<Path, ByteArray?>()

        suspend fun writeAsset(path: String, get: CosmeticAssets.() -> EssentialAsset?) {
            val original = originalCosmetic?.baseAssets?.get()
            val updated = cosmetic?.baseAssets?.get()
            if (original?.checksum == updated?.checksum) return
            changes[folder / path] = updated?.let { fetchAsset(it) }
        }

        val override = originalMetadataUnknownVersion?.override
        writeAsset(override?.thumbnail ?: "$fileId.thumbnail.png") { thumbnail }
        writeAsset(override?.texture ?: "$fileId.texture.png") { texture }
        writeAsset(override?.emissive ?: "$fileId.emissive.png") { emissiveTexture }
        writeAsset(override?.geometrySteve ?: "$fileId.geometry.steve.json") { geometry.steve }
        writeAsset(override?.geometryAlex ?: "$fileId.geometry.alex.json") { geometry.alex }
        writeAsset(override?.animations ?: "$fileId.animations.json") { animations }
        writeAsset(override?.skinMaskSteve ?: "$fileId.skin_mask.steve.png") { defaultSkinMask.steve }
        writeAsset(override?.skinMaskAlex ?: "$fileId.skin_mask.alex.png") { defaultSkinMask.alex }
        for (side in Side.values()) {
            val sideId = side.name.lowercase()
            writeAsset("$fileId.skin_mask.steve.$sideId.png") { sidedSkinMasks[side]?.steve }
            writeAsset("$fileId.skin_mask.alex.$sideId.png") { sidedSkinMasks[side]?.alex }
        }

        val extraPaths = mutableSetOf<String>()
        extraPaths.addAll(originalCosmetic?.files?.keys ?: emptySet())
        extraPaths.addAll(cosmetic?.files?.keys ?: emptySet())
        extraPaths.removeAll(listOf(
            "thumbnail.png",
            "texture.png",
            "emissive.png",
            "geometry.steve.json",
            "geometry.alex.json",
            "animations.json",
            "skin_mask.steve.png",
            "skin_mask.alex.png",
            "settings.json",
        ))
        for (side in Side.values()) {
            val sideId = side.name.lowercase()
            extraPaths.remove("skin_mask.steve.$sideId.png")
            extraPaths.remove("skin_mask.alex.$sideId.png")
        }
        for (path in extraPaths) {
            val original = originalCosmetic?.files?.get(path)
            val updated = cosmetic?.files?.get(path)
            if (original?.checksum == updated?.checksum) continue
            changes[folder / path] = updated?.let { fetchAsset(it) }
        }

        val settingsFile = folder / (override?.settings ?: "$fileId.settings.json")
        if (cosmetic?.allProperties != originalCosmetic?.allProperties) {
            changes[settingsFile] =
                cosmetic?.allProperties?.takeUnless { it.isEmpty() }?.let { json.encodeToString(it).encodeToByteArray() }
        }

        if (cosmetic == null) {
            if (originalMetadataUnknownVersion != null) {
                // If new file is null and original is not null, pass the null along as a change
                changes[metadataFile] = null
            }
        } else if ((originalMetadataUnknownVersion?.rev ?: 3) >= 3) { // We use the new format, unless the original is in the old format
            val originalMetadataV3 = files[metadataFile]?.let { json.decodeFromString<CosmeticMetadataV3>(it().decodeToString()) }
            val newMetadataV3 = CosmeticMetadataV3(
                3,
                cosmetic.displayNames - LOCAL_PATH,
                cosmetic.categories,
                cosmetic.tags,
                cosmetic.tier,
                originalMetadataV3?.organization ?: "",
                originalMetadataV3?.revenueShare,
                cosmetic.price,
                cosmetic.availableAfter,
                cosmetic.availableUntil,
                cosmetic.showTimerAfter,
                cosmetic.defaultSortWeight.takeUnless { it == 20 },
                (override ?: CosmeticMetadataOverrides()).copy(
                    id = cosmetic.id.takeUnless { it == fileId.uppercase() },
                ),
            )
            if (originalMetadataV3 != newMetadataV3) {
                changes[metadataFile] = newMetadataV3.let { json.encodeToString(it).encodeToByteArray() }
            }
        } else {
            val originalMetadataV0 = files[metadataFile]?.let { json.decodeFromString<CosmeticMetadataV0>(it().decodeToString()) }
            val newMetadataV0 = CosmeticMetadataV0(
                0,
                cosmetic.displayNames - LOCAL_PATH,
                cosmetic.categories,
                cosmetic.tags,
                cosmetic.tier,
                originalMetadataV0?.organization ?: "",
                originalMetadataV0?.revenueShare,
                cosmetic.price,
                cosmetic.availableAfter,
                cosmetic.availableUntil,
                cosmetic.showTimerAfter,
                cosmetic.defaultSortWeight.takeUnless { it == 20 },
                (override ?: CosmeticMetadataOverrides()).copy(
                    id = cosmetic.id.takeUnless { it == fileId.uppercase() },
                ),
            )
            if (originalMetadataV0 != newMetadataV0) {
                changes[metadataFile] = newMetadataV0.let { json.encodeToString(it).encodeToByteArray() }
            }
        }

        return changes.mapKeys { it.key.str }
    }

    override suspend fun getCategory(id: CosmeticCategoryId): CosmeticCategory? = categories[id]
    override suspend fun getCategories(): List<CosmeticCategory> = categories.values.toList()

    override suspend fun getCosmeticBundle(id: CosmeticBundleId): CosmeticBundle? = bundles[id]

    override suspend fun getCosmeticBundles(): List<CosmeticBundle> = bundles.values.toList()

    override suspend fun getFeaturedPageCollection(id: FeaturedPageCollectionId): FeaturedPageCollection? = featuredPageCollections[id]

    override suspend fun getFeaturedPageCollections(): List<FeaturedPageCollection> = featuredPageCollections.values.toList()


    override suspend fun getImplicitOwnership(id: ImplicitOwnershipId): CosmeticImplicitOwnership? = implicitOwnerships[id]

    override suspend fun getImplicitOwnerships(): List<CosmeticImplicitOwnership> = implicitOwnerships.values.toList()

    override suspend fun getCosmetic(id: CosmeticId): Cosmetic? {
        return cosmetics[id] ?: lazyCosmetics[id]?.let { path ->
            val cosmetic = tryLoadCosmetic(path) ?: return@let null
            cosmetics[cosmetic.id] = cosmetic
            cosmeticByPath[path] = cosmetic.id
            lazyCosmetics.remove(cosmetic.id)
            cosmetic
        }
    }

    override suspend fun getCosmetics(): List<Cosmetic> {
        for ((id, path) in lazyCosmetics.toList()) {
            val cosmetic = tryLoadCosmetic(path) ?: continue
            cosmetics[cosmetic.id] = cosmetic
            cosmeticByPath[path] = cosmetic.id
            lazyCosmetics.remove(id)
        }
        return cosmetics.values.toList()
    }

    data class Changes(
        val categories: Set<CosmeticCategoryId>,
        val cosmetics: Set<CosmeticId>,
        val bundles: Set<CosmeticBundleId>,
        val featuredPageCollections: Set<FeaturedPageCollectionId>,
        val implicitOwnerships: Set<ImplicitOwnershipId>,
    ) {
        operator fun plus(other: Changes) =
            Changes(
                categories + other.categories,
                cosmetics + other.cosmetics,
                (bundles + other.bundles), // Remove () when removing flag
                (featuredPageCollections + other.featuredPageCollections), // Remove () when removing flag
                implicitOwnerships + other.implicitOwnerships,
            )

        companion object {
            val Empty = Changes(
                emptySet(),
                emptySet(),
                emptySet(),
                emptySet(),
                emptySet(),
            )
        }
    }

    private class Observers {
        val categories = mutableSetOf<Path>()
        val cosmetics = mutableSetOf<Path>()
        val bundles = mutableSetOf<Path>()
        val featuredPageCollections = mutableSetOf<Path>()
        val implicitOwnerships = mutableSetOf<Path>()

        fun isEmpty() = categories.isEmpty() && cosmetics.isEmpty() && bundles.isEmpty() && featuredPageCollections.isEmpty() && implicitOwnerships.isEmpty()
    }

    private inner class FileAccessImpl(
        private val observer: Path,
        private val selector: Observers.() -> MutableSet<Path>,
    ) : FileAccess {
        init {
            for (path in observedFiles.remove(observer) ?: emptySet()) {
                val observers = fileObservers[path]
                if (observers != null) {
                    observers.selector().remove(observer)
                    if (observers.isEmpty()) {
                        fileObservers.remove(path)
                    }
                }
            }
            for (path in observedFolders.remove(observer) ?: emptySet()) {
                val observers = folderObservers[path]
                if (observers != null) {
                    observers.selector().remove(observer)
                    if (observers.isEmpty()) {
                        folderObservers.remove(path)
                    }
                }
            }
        }

        override fun file(path: Path): (suspend () -> ByteArray)? {
            fileObservers.getOrPut(path, ::Observers).selector().add(observer)
            observedFiles.getOrPut(observer, ::mutableSetOf).add(path)
            return files[path]
        }

        override fun files(folder: Path): Collection<Path> {
            folderObservers.getOrPut(folder, ::Observers).selector().add(observer)
            observedFolders.getOrPut(observer, ::mutableSetOf).add(folder)
            return filesByFolders[folder] ?: emptySet()
        }
    }

    @Serializable
    data class CategoryMetadata(
        @SerialName("metadata_revision")
        val rev: Int,
        val id: CosmeticCategoryId,
        @SerialName("display_name")
        val displayNames: Map<String, String>,
        @SerialName("compact_name")
        val compactNames: Map<String, String>,
        val description: Map<String, String>,
        val slots: Set<CosmeticSlot>,
        val tags: Set<String>,
        val order: Int,
        @SerialName("available_after")
        val availableAfter: Instant?,
        @SerialName("available_until")
        val availableUntil: Instant?,
        val override: Overrides = Overrides(),
    ) {
        @Serializable
        data class Overrides(
            val icon: String? = null,
        )
    }

    @Serializable
    data class BundleMetadata(
        @SerialName("metadata_revision")
        val rev: Int,
        val id: CosmeticBundleId,
        val name: String,
        val tier: CosmeticTier,
        val discount: Float,
        @SerialName("rotate_on_preview")
        val rotateOnPreview: Boolean = false,
        var skin: CosmeticBundle.Skin? = null,
        val cosmetics: Map<CosmeticSlot, CosmeticId>,
        val settings: Map<CosmeticId, List<CosmeticSetting>>,
    )

    @Serializable
    data class FeaturedPageCollectionMetadata(
        @SerialName("metadata_revision")
        val rev: Int,
        val id: FeaturedPageCollectionId,
        val availability: Availability? = null,
        val pages: Map<FeaturedPageWidth, FeaturedPage>
    ) {

        @Serializable
        data class Availability(
            val after: Instant,
            val until: Instant,
            @SerialName("show_timer_after")
            val showTimerAfter: Instant? = null,
        )

    }

    @Serializable
    data class CosmeticMetadataVUnknown(
        @SerialName("metadata_revision")
        val rev: Int,
        val override: CosmeticMetadataOverrides,
    )

    // Named V0, but is actually revision 0, 1 and 2 as all of those were supposedly used before proper versioning, which now starts at 3
    @Serializable
    data class CosmeticMetadataV0(
        @SerialName("metadata_revision")
        val rev: Int,
        @SerialName("display_name")
        val displayName: Map<String, String>,
        val categories: Map<String, Int>,
        val tags: Set<String>,
        val tier: CosmeticTier = CosmeticTier.COMMON,
        val organization: String,
        @SerialName("revenue_share")
        val revenueShare: Double?,
        val price: Int?,
        @SerialName("available_after")
        val availableAfter: Instant?,
        @SerialName("available_until")
        val availableUntil: Instant?,
        @SerialName("show_timer_after")
        val showTimerAfter: Instant? = null,
        @SerialName("default_sort_weight")
        val defaultSortWeight: Int?,
        val override: CosmeticMetadataOverrides,
    )

    @Serializable
    data class CosmeticMetadataV3(
        @SerialName("metadata_revision")
        val rev: Int,
        @SerialName("display_name")
        val displayName: Map<String, String>,
        val categories: Map<String, Int>,
        val tags: Set<String>,
        val tier: CosmeticTier = CosmeticTier.COMMON,
        val organization: String,
        @SerialName("revenue_share")
        val revenueShare: Double?,
        val price: Int? = null,
        @SerialName("available_after")
        val availableAfter: Instant?,
        @SerialName("available_until")
        val availableUntil: Instant?,
        @SerialName("show_timer_after")
        val showTimerAfter: Instant? = null,
        @SerialName("default_sort_weight")
        val defaultSortWeight: Int?,
        val override: CosmeticMetadataOverrides,
    )

    @Serializable
    data class CosmeticMetadataOverrides(
        val id: CosmeticId? = null,

        @SerialName("asset.geometry.steve")
        val geometrySteve: String? = null,
        @SerialName("asset.geometry.alex")
        val geometryAlex: String? = null,
        @SerialName("asset.animations")
        val animations: String? = null,
        @SerialName("asset.skin_mask.steve")
        val skinMaskSteve: String? = null,
        @SerialName("asset.skin_mask.alex")
        val skinMaskAlex: String? = null,
        @SerialName("asset.settings")
        val settings: String? = null,
        @SerialName("asset.texture")
        val texture: String? = null,
        @SerialName("asset.emissive")
        val emissive: String? = null,
        @SerialName("asset.thumbnail")
        val thumbnail: String? = null,
    )

    @Serializable
    data class CosmeticImplicitOwnership(
        val id: String,
        val cosmetics: List<String>,
        val criterion: ImplicitOwnershipCriterion,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("type")
    @Serializable
    sealed interface ImplicitOwnershipCriterion {
        val type: String

        @Serializable
        @SerialName("EVERYONE")
        data object Everyone : ImplicitOwnershipCriterion {
            @Transient
            override val type: String = "EVERYONE"
        }

        @Serializable
        @SerialName("OWNED_COSMETIC")
        data class OwnedCosmetic(
            val cosmetic: String,
        ) : ImplicitOwnershipCriterion {
            @Transient
            override val type: String = "OWNED_COSMETIC"
        }
    }

}

typealias AssetBuilder = suspend (path: String, read: suspend () -> ByteArray) -> EssentialAsset

const val LOCAL_PATH = "local_path"

private val dataUrlBase64Prefix = "data:;base64,"

private val jsonWithIgnoreUnknownKeys = Json {
    prettyPrint = true
    serializersModule = CosmeticProperty.TheSerializer.module + CosmeticSetting.TheSerializer.module
    ignoreUnknownKeys = true
}

@JvmInline
private value class Path private constructor(val str: String) {
    val name: String
        get() = str.substringAfterLast("/")
    val parent: Path
        get() = this / ".."

    val parents: Sequence<Path>
        get() = generateSequence(this) { it.parent.takeUnless(Path::isEmpty) }.drop(1)

    fun isEmpty() = str.isEmpty()
    fun isIn(other: Path) = str.startsWith("$other/")
    fun relativeTo(other: Path) = Path(str.removePrefix(other.str).removePrefix("/"))

    operator fun div(other: String) = of("$this/$other")
    operator fun div(other: Path) = of("$this/$other")

    override fun toString(): String {
        return str
    }

    companion object {
        fun of(str: String): Path {
            val parts = str
                .replace('\\', '/')
                .removePrefix("~/")
                .substringAfter("/~/")
                .split("/")
                .filterNot { it == "" || it == "." }
                .toMutableList()
            var i = 0
            while (i <= parts.lastIndex) {
                if (i > 0 && parts[i] == ".." && parts[i - 1] != "..") {
                    parts.removeAt(i)
                    parts.removeAt(i - 1)
                    i--
                } else {
                    i++
                }
            }
            return Path(parts.joinToString("/"))
        }
    }
}

private interface FileAccess {
    fun file(path: Path): (suspend () -> ByteArray)?
    fun files(folder: Path): Collection<Path>
}

private suspend fun FileAccess.loadCategory(json: Json, metadataFile: Path, assetBuilder: AssetBuilder): CosmeticCategory {
    val metadataJson = file(metadataFile) ?: throw NoSuchElementException(metadataFile.toString())
    val metadata = json.decodeFromString<GitRepoCosmeticsDatabase.CategoryMetadata>(metadataJson().decodeToString())
    val id = metadata.id
    val fileId = id.lowercase()
    val folder = metadataFile.parent

    suspend fun getAsset(path: String): EssentialAsset? {
        val filePath = folder / path
        val file = file(filePath) ?: return null
        return assetBuilder(filePath.str, file)
    }

    return CosmeticCategory(
        id,
        metadata.displayNames,
        metadata.compactNames,
        metadata.description,
        metadata.slots,
        metadata.tags,
        metadata.order,
        metadata.availableAfter,
        metadata.availableUntil,
    )
}

private suspend fun FileAccess.loadBundle(json: Json, metadataFile: Path): CosmeticBundle {
    val metadataJson = file(metadataFile) ?: throw NoSuchElementException(metadataFile.toString())
    val metadata = json.decodeFromString<GitRepoCosmeticsDatabase.BundleMetadata>(metadataJson().decodeToString())
    return CosmeticBundle(
        metadata.id,
        metadata.name,
        metadata.tier,
        metadata.discount,
        metadata.rotateOnPreview,
        metadata.skin,
        metadata.cosmetics,
        metadata.settings
    )
}

private suspend fun FileAccess.loadFeaturedPageCollection(json: Json, metadataFile: Path): FeaturedPageCollection {
    val metadataJson = file(metadataFile) ?: throw NoSuchElementException(metadataFile.toString())
    val metadata = json.decodeFromString<GitRepoCosmeticsDatabase.FeaturedPageCollectionMetadata>(metadataJson().decodeToString())
    return FeaturedPageCollection(
        metadata.id,
        metadata.availability?.let { FeaturedPageCollection.Availability(it.after, it.until, it.showTimerAfter) },
        metadata.pages
    )
}

private suspend fun FileAccess.loadImplicitOwnership(json: Json, metadataFile: Path): ImplicitOwnership {
    val metadataJson = file(metadataFile) ?: throw NoSuchElementException(metadataFile.toString())
    return json.decodeFromString<ImplicitOwnership>(metadataJson().decodeToString())
}

private suspend fun FileAccess.loadCosmetic(json: Json, metadataFile: Path, assetBuilder: AssetBuilder): Cosmetic {
    val metadataJson = file(metadataFile) ?: throw NoSuchElementException(metadataFile.toString())
    val metadataUnknownVersion = jsonWithIgnoreUnknownKeys.decodeFromString<GitRepoCosmeticsDatabase.CosmeticMetadataVUnknown>(metadataJson().decodeToString())
    val metadata: GitRepoCosmeticsDatabase.CosmeticMetadataV0

    if (metadataUnknownVersion.rev >= 3) {
        // Convert v3 to v0 for simplicity
        val metadataV3 = json.decodeFromString<GitRepoCosmeticsDatabase.CosmeticMetadataV3>(metadataJson().decodeToString())
        metadata = GitRepoCosmeticsDatabase.CosmeticMetadataV0(
            metadataV3.rev,
            metadataV3.displayName,
            metadataV3.categories,
            metadataV3.tags,
            metadataV3.tier,
            metadataV3.organization,
            metadataV3.revenueShare,
            metadataV3.price,
            metadataV3.availableAfter,
            metadataV3.availableUntil,
            metadataV3.showTimerAfter,
            metadataV3.defaultSortWeight,
            metadataV3.override,
        )
    } else {
        metadata = json.decodeFromString<GitRepoCosmeticsDatabase.CosmeticMetadataV0>(metadataJson().decodeToString())
    }

    val folder = metadataFile.parent
    val fileId = folder.name
    val assets = mutableMapOf<String, EssentialAsset>()
    val assetFiles = files(folder).mapTo(mutableSetOf()) { it.relativeTo(folder) }
    assetFiles.remove(Path.of(metadataFile.name))

    suspend fun assetOverride(override: String?, name: String) {
        val relFilePath = Path.of(override ?: "$fileId.$name")
        val filePath = folder / relFilePath
        val file = file(filePath) ?: return
        assetFiles.remove(relFilePath)
        assets[name] = assetBuilder(filePath.str, file)
    }

    val override = metadata.override

    assetOverride(override.thumbnail, "thumbnail.png")
    assetOverride(override.texture, "texture.png")
    assetOverride(override.emissive, "emissive.png")
    assetOverride(override.geometrySteve, "geometry.steve.json")
    assetOverride(override.geometryAlex, "geometry.alex.json")
    assetOverride(override.animations, "animations.json")
    assetOverride(override.skinMaskSteve, "skin_mask.steve.png")
    assetOverride(override.skinMaskAlex, "skin_mask.alex.png")
    for (side in Side.values()) {
        val sideId = side.name.lowercase()
        assetOverride(null, "skin_mask.steve.$sideId.png")
        assetOverride(null, "skin_mask.alex.$sideId.png")
    }

    val settingsFile = (override.settings ?: "$fileId.settings.json")
    assetFiles.remove(Path.of(settingsFile))
    val settings = file(folder / settingsFile)
        ?.let { read ->
            try {
                CosmeticProperty.fromJsonArray(read().decodeToString())
            } catch (e: Exception) {
                return makeErrorCosmetic(metadataFile, Cosmetic.Diagnostic.fatal(
                    e.message ?: "Unexpected error",
                    stacktrace = e.stackTraceToString(),
                    file = settingsFile,
                ))
            }
        }

    for (path in assetFiles) {
        val filePath = folder / path
        val file = file(filePath)!!
        assets[path.str] = assetBuilder(filePath.str, file)
    }

    val slot =
        if (folder.parents.lastOrNull()?.str == "emotes") CosmeticSlot.EMOTE
        else CosmeticSlot.of(folder.parent.name.uppercase())

    if (slot !in CosmeticSlot.values()) {
        throw IllegalArgumentException("Cosmetic slot `${slot.id}` is unknown. If you'd like to create a new slot, contact the mod dev team.")
    }

    return Cosmetic(
        CosmeticBase(
            override.id ?: fileId.uppercase(), // repo files use lowercase ids, actual ids should be uppercase
            slot,
            metadata.tier,
            mapOf("en_us" to fileId, LOCAL_PATH to folder.str) + metadata.displayName,
            assets,
            settings ?: emptyList(),
        ),
        CosmeticStoreInfo(
            metadata.price,
            metadata.tags,
            metadata.availableAfter,
            metadata.availableUntil,
            metadata.showTimerAfter,
            metadata.categories,
            metadata.defaultSortWeight ?: 20,
        ),
    )
}

private fun makeErrorCosmetic(metadataFile: Path, diagnostic: Cosmetic.Diagnostic): Cosmetic {
    val folder = metadataFile.parent
    return Cosmetic(
        CosmeticBase(
            folder.str.replace('/', '_').uppercase(),
            CosmeticSlot.of("ERROR"),
            CosmeticTier.COMMON,
            mapOf("en_us" to folder.str, LOCAL_PATH to folder.str),
            emptyMap(),
            emptyList(),
        ),
        CosmeticStoreInfo(
            null,
            setOf("HAS_ERRORS"),
            instant(0),
            instant(0),
            null,
            mapOf("ERROR" to 0),
            0,
        ),
        listOf(diagnostic),
    )
}
