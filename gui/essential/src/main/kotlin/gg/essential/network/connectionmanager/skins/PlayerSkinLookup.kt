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
package gg.essential.network.connectionmanager.skins

import com.google.common.collect.MapMaker
import gg.essential.connectionmanager.common.packet.skin.ClientSelectedSkinsRequestPacket
import gg.essential.connectionmanager.common.packet.skin.ServerSelectedSkinsResponsePacket
import gg.essential.connectionmanager.common.packet.skin.ServerUserSkinUpdatedPacket
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.mod.Model
import gg.essential.mod.Skin
import gg.essential.network.CMConnection
import gg.essential.network.mojang.MojangProfileLookupApi
import gg.essential.network.registerPacketHandler
import gg.essential.util.Client
import gg.essential.util.ExponentialBackoff
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.RateLimitException
import gg.essential.util.Sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

object PlayerSkinLookup {
    private val LOGGER = LoggerFactory.getLogger(PlayerSkinLookup::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.Client)

    private val skins = mutableMapOf<UUID, Skin>()
    private val skinStates: MutableMap<UUID, MutableState<Skin?>> = MapMaker().weakValues().makeMap()
    private val skinsLoading = mutableSetOf<UUID>()

    private val diskCache = PlayerSkinLookupDiskCache(coroutineScope, skins)

    fun register(connection: CMConnection) {
        connection.registerPacketHandler { packet: ServerUserSkinUpdatedPacket ->
            put(packet.uuid, Skin.fromInfra(packet.skinTexture))
        }
    }

    fun loadCache(cacheDir: Path) {
        diskCache.load(cacheDir)
    }

    fun put(uuid: UUID, skin: Skin) {
        skinsLoading.remove(uuid)
        skins[uuid] = skin
        skinStates[uuid]?.set(skin)

        val cached = diskCache.state.getUntracked()
        if (cached != null && cached[uuid] != skin) {
            diskCache.scheduleUpdate()
        }
    }

    /** Returns the skin of the given player. Value may be `null` while loading. */
    fun getSkin(uuid: UUID): State<Skin?> {
        val state = skinStates.getOrPut(uuid) {
            val skin = skins[uuid]
            if (skin != null) {
                mutableStateOf(skin)
            } else {
                request(uuid)
                mutableStateOf(null)
            }
        }

        // If the skin hasn't been loaded yet, we'll fall back to what we saved to disk from previous sessions.
        // It may quite possibly be outdated, but it's better than nothing, and the State will automatically switch to
        // the up-to-date skin once that's available.
        return State { state() ?: diskCache.state()?.get(uuid) }
    }

    private val requestsToBeSent = mutableListOf<UUID>()
    private fun request(uuid: UUID) {
        if (uuid in skinsLoading) return
        skinsLoading.add(uuid)

        if (requestsToBeSent.isEmpty()) {
            coroutineScope.launch {
                delay(50.milliseconds)
                executeRequests()
            }
        }
        requestsToBeSent.add(uuid)
    }

    private suspend fun executeRequests() {
        val requestedUuids = requestsToBeSent.toMutableList()
        requestsToBeSent.clear()

        // Filter out requests if they've been fulfilled some other way by now
        requestedUuids.removeIf { it in skins }

        fetchFromInfra(requestedUuids)

        // Filter out everything our infra has supplied (or which we've got some other way by now)
        requestedUuids.removeIf { it in skins }

        fetchFromMojang(requestedUuids)
    }

    private suspend fun fetchFromInfra(uuids: List<UUID>) {
        val connection = platform.cmConnection
        if (!connection.isOpen) {
            // User is not yet connected to our infra, likely because they haven't accept TOS yet
            return
        }

        val response = connection.call(ClientSelectedSkinsRequestPacket(uuids))
            .exponentialBackoff()
            .await<ServerSelectedSkinsResponsePacket>()
            .skins

        for ((uuid, skin) in response) {
            put(uuid, Skin.fromInfra(skin))
        }
    }

    @OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
    private suspend fun fetchFromMojang(uuids: List<UUID>) {
        // Intentionally not parallelizing this to not annoy Mojang's servers
        for (uuid in uuids) {
            val skin = try {
                fetchFromMojang(uuid) ?: Skin.default(uuid.toKotlinUuid())
            } catch (e: Exception) {
                LOGGER.error("Error fetching skin for $uuid from Mojang", e)
                Skin.default(uuid.toKotlinUuid())
            }
            put(uuid, skin)
        }
    }

    private suspend fun fetchFromMojang(uuid: UUID): Skin? {
        val backoff = ExponentialBackoff(2.seconds, 60.seconds, 2.0)

        while (true) {
            try {
                return MojangProfileLookupApi.fetch(uuid)?.textures?.skin
            } catch (_: RateLimitException) {
                delay(backoff.increment())
            }
        }
    }

    fun supplySkinFromGame(uuid: UUID, future: CompletableFuture<Skin>) {
        // The value the game (usually supplied from the launcher) knows about is probably older than
        // what we've already got (or started fetching), so we'll ignore it.
        if (uuid in skins || uuid in skinsLoading) return

        if (future.isDone) {
            put(uuid, future.join())
        } else {
            skinsLoading.add(uuid)
            coroutineScope.launch {
                val skin = future.asDeferred().await()
                if (uuid !in skins) {
                    put(uuid, skin)
                }
            }
        }
    }

    init {
        // EssentialBot is used internally for the announcement channel. We don't need every user requesting it.
        // Its skin probably won't change and isn't actually displayed anywhere anyway (unless you try to befriend it).
        put(UUID.fromString("cd899a14-de78-4de8-8d31-9d42fff31d7a"), Skin("eb512ba3adade5ebc366392b85c3db144c8a625e8774ec3e402711f45c430c18", Model.STEVE))
    }
}

/**
 * Stores discovered uuid-to-skin mappings on disk, so we can use them on next boot as a fallback while fetching the
 * latest skin data from infra/mojang.
 */
private class PlayerSkinLookupDiskCache(
    private val coroutineScope: CoroutineScope,
    /** Live view of all currently known up-to-date skins. */
    private val skins: Map<UUID, Skin>,
) {
    private lateinit var file: Path

    val state: MutableState<Map<UUID, Skin>?> = mutableStateOf(null)

    fun load(cacheDir: Path) {
        file = cacheDir.resolve("uuid-skin-map.bin")

        coroutineScope.launch {
            val map = withContext(Dispatchers.IO) {
                try {
                    loadDiskCache()
                } catch (e: Exception) {
                    LOGGER.error("Failed to load $file", e)
                    emptyMap()
                }
            }

            state.set(map)

            if (skins.any { (uuid, newSkin) -> map[uuid] != newSkin }) {
                scheduleUpdate()
            }
        }
    }

    private fun loadDiskCache(): Map<UUID, Skin> {
        val bytes = try {
            file.readBytes()
        } catch (_: NoSuchFileException) {
            return emptyMap()
        }

        val map = mutableMapOf<UUID, Skin>()

        val stream = DataInputStream(bytes.inputStream())
        while (stream.available() >= 16 + 32 + 1) {
            val uuid = UUID(stream.readLong(), stream.readLong())
            val hash = Sha256(ByteArray(32).also { stream.readFully(it) })
            val model = if (stream.readBoolean()) Model.ALEX else Model.STEVE
            map[uuid] = Skin(hash.hexStr.trimStart('0'), model)
        }

        return map
    }

    private fun saveDiskCache(cache: Map<UUID, Skin>) {
        val bytes = ByteArrayOutputStream()
        val stream = DataOutputStream(bytes)
        for ((uuid, skin) in cache) {
            stream.writeLong(uuid.mostSignificantBits)
            stream.writeLong(uuid.leastSignificantBits)
            stream.write(Sha256(skin.hash.padStart(64, '0')).bytes)
            stream.writeBoolean(skin.model == Model.ALEX)
        }

        file.createParentDirectories()
        file.writeBytes(bytes.toByteArray())
    }

    private var pendingDiskCacheUpdateJob: Job? = null
    private var activeDiskCacheUpdateJob: Job? = null
    fun scheduleUpdate() {
        if (pendingDiskCacheUpdateJob != null) return

        val oldCache = state.getUntracked() ?: return

        pendingDiskCacheUpdateJob = coroutineScope.launch {
            delay(10.seconds)

            val newCache = mutableMapOf<UUID, Skin>()
            for ((uuid, oldSkin) in oldCache) {
                if (newCache.size >= MAX_OLD_DISK_CACHE_ENTRIES) {
                    break
                }
                if (uuid !in skins) {
                    newCache[uuid] = oldSkin
                }
            }
            newCache.putAll(skins)

            state.set(newCache)
            pendingDiskCacheUpdateJob = null

            val prevJob = activeDiskCacheUpdateJob
            activeDiskCacheUpdateJob = coroutineScope.launch(Dispatchers.IO) {
                prevJob?.join()
                try {
                    saveDiskCache(newCache)
                } catch (e: Exception) {
                    LOGGER.error("Failed to save $file", e)
                }
            }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(PlayerSkinLookupDiskCache::class.java)
        private const val MAX_OLD_DISK_CACHE_ENTRIES = 1000
    }
}
