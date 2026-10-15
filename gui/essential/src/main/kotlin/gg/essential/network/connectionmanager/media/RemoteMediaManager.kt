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
package gg.essential.network.connectionmanager.media

import gg.essential.connectionmanager.common.packet.media.ClientMediaCreatePacket
import gg.essential.connectionmanager.common.packet.media.ClientMediaDeleteRequestPacket
import gg.essential.connectionmanager.common.packet.media.ClientMediaGetUploadUrlPacket
import gg.essential.connectionmanager.common.packet.media.ClientMediaRequestPacket
import gg.essential.connectionmanager.common.packet.media.ClientMediaSharePacket
import gg.essential.connectionmanager.common.packet.media.ClientMediaUpdatePacket
import gg.essential.connectionmanager.common.packet.media.ServerMediaPopulatePacket
import gg.essential.connectionmanager.common.packet.media.ServerMediaUploadUrlPacket
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.media.model.Media
import gg.essential.media.model.MediaMetadata
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.NetworkedManager
import gg.essential.network.registerPacketHandler
import gg.essential.util.Sha256
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

class RemoteMediaManager(
    private val cmConnection: CMConnection,
) : NetworkedManager {
    private val mutableUploadedScreenshotsState: MutableState<Map<String, Media>> = mutableStateOf(emptyMap())

    val uploadedScreenshotsByIdState: State<Map<String, Media>> = mutableUploadedScreenshotsState
    val uploadedScreenshotsState: ListState<Media> = memo {
        mutableUploadedScreenshotsState().values.toList()
    }.toListState()

    init {
        cmConnection.registerPacketHandler<ServerMediaPopulatePacket> { packet ->
            mutableUploadedScreenshotsState.set { it + packet.medias.associateBy { media -> media.id } }
        }
    }

    override fun onConnected() {
        super.onConnected()
        cmConnection.call(ClientMediaRequestPacket(null)).fireAndForget()
    }

    override fun resetState() {
        mutableUploadedScreenshotsState.set(emptyMap())
    }

    /**
     * @throws gg.essential.network.UnexpectedResponseException if the connection-manager does not return the expected response
     * @throws gg.essential.network.NoResponseException if the connection-manager didn't respond in time
     */
    suspend fun getUploadUrl(sha256: Sha256): ServerMediaUploadUrlPacket {
        return cmConnection.call(ClientMediaGetUploadUrlPacket(sha256.hexStr)).awaitOrThrow()
    }

    /**
     * @throws gg.essential.network.UnexpectedResponseException if the connection-manager does not return the expected response
     * @throws gg.essential.network.NoResponseException if the connection-manager didn't respond in time
     */
    suspend fun createMedia(mediaId: String, title: String, description: String, metadata: MediaMetadata): Media {
        val response = cmConnection.call(ClientMediaCreatePacket(mediaId, title, description, metadata))
            .awaitOrThrow<ServerMediaPopulatePacket>()

        return response.medias.firstOrNull()
            ?: throw IllegalStateException("Expected connection-manager to return at least one media, but got none")
    }

    /**
     * @throws gg.essential.network.UnsuccessfulResponseException if the connection-manager rejected the share request
     * @throws gg.essential.network.NoResponseException if the connection-manager didn't respond in time
     */
    suspend fun shareMedia(chatChannelIds: Set<Long>, mediaIds: Set<String>) {
        cmConnection.call(ClientMediaSharePacket(chatChannelIds, mediaIds)).awaitSuccessOrThrow()
    }

    /**
     * @throws gg.essential.network.UnsuccessfulResponseException if the connection-manager rejected the deletion
     * @throws gg.essential.network.NoResponseException if the connection-manager didn't respond in time
     */
    suspend fun deleteMedia(mediaId: String) {
        cmConnection.call(ClientMediaDeleteRequestPacket(mediaId)).awaitSuccessOrThrow()
        mutableUploadedScreenshotsState.set { it - mediaId }
    }

    /**
     * @throws gg.essential.network.UnsuccessfulResponseException if the connection-manager rejected the update
     * @throws gg.essential.network.NoResponseException if the connection-manager didn't respond in time
     */
    suspend fun setFavorite(mediaId: String, favorite: Boolean) {
        // We don't need to update our state with the returned media, the packet handler registered in the init block
        // will still handle it for us.
        cmConnection.call(ClientMediaUpdatePacket(mediaId, null, null, favorite))
            .awaitOrThrow<ServerMediaPopulatePacket>()
    }

    @Deprecated("Use Kotlin instead, this should be removed when the refactor of `ScreenshotManager` is done")
    fun shareMediaAsync(chatChannelIds: Set<Long>, mediaIds: Set<String>): CompletableFuture<Unit> {
        return cmConnection.connectionScope.future { shareMedia(chatChannelIds, mediaIds) }
    }

    @Deprecated("Use Kotlin instead, this should be removed when the refactor of `ScreenshotManager` is done")
    fun deleteMediaAsync(mediaId: String): CompletableFuture<Unit> {
        return cmConnection.connectionScope.future { deleteMedia(mediaId) }
    }

    @Deprecated("Use Kotlin instead, this should be removed when the refactor of `ScreenshotManager` is done")
    fun setFavoriteAsync(mediaId: String, favorite: Boolean): CompletableFuture<Unit> {
        return cmConnection.connectionScope.future { setFavorite(mediaId, favorite) }
    }
}
