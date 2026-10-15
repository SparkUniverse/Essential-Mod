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

import com.sparkuniverse.toolbox.util.DateTime
import gg.essential.handlers.screenshot.ClientScreenshotMetadata
import gg.essential.media.model.Media
import gg.essential.media.model.MediaLocationMetadata
import gg.essential.media.model.MediaMetadata
import gg.essential.network.CMConnection
import gg.essential.sps.SpsAddress
import gg.essential.util.Sha256
import gg.essential.util.USession
import gg.essential.util.UuidNameLookup
import gg.essential.util.formatDateAndTime
import gg.essential.util.httpCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.io.path.readBytes

class ScreenshotUploader(
    private val cmConnection: CMConnection,
    private val remoteMediaManager: RemoteMediaManager,
) {
    /**
     * Uploads the screenshot at [path] and creates media for it on the connection-manager.
     *
     * This will always perform a new upload, regardless of whether a [Media] already exists for the screenshot at
     * [path].
     *
     * @param onProgress Called with the upload's progress as a percentage between 0 and 99. This is never called with
     * 100, so the caller should treat this method returning as the operation being completed. There are no guarantees
     * about which thread this will be called on.
     *
     * @throws MediaUploadFailedException If the upload returns a non-successful status code.
     * @throws Exception This method may throw any other exception, such as [java.io.IOException], or any of the
     * connection-manager related exceptions thrown by [RemoteMediaManager]. The caller should handle these gracefully.
     */
    suspend fun upload(path: Path, metadata: ClientScreenshotMetadata, onProgress: (percent: Int) -> Unit): Media = coroutineScope {
        val usernameDeferred = async { resolveAuthorUsername(metadata.authorId) }

        val (fileData, sha256) = withContext(Dispatchers.IO) {
            val bytes = path.readBytes()
            bytes to Sha256.compute(bytes)
        }

        val uploadUrlPacket = remoteMediaManager.getUploadUrl(sha256)
        onProgress(AFTER_UPLOAD_URL_PROGRESS)

        // TODO: It would be nice to gradually increment the progress while doing the upload.
        uploadFile(uploadUrlPacket.mediaId, uploadUrlPacket.uploadUrl, uploadUrlPacket.uploadHeaders, fileData)

        val username = usernameDeferred.await()
        onProgress(AFTER_USERNAME_PROGRESS)

        val time = metadata.editTime ?: metadata.time

        remoteMediaManager.createMedia(
            uploadUrlPacket.mediaId,
            "$username's Screenshot",
            "Captured ${formatDateAndTime(time.toInstant())}",
            metadata.toMediaMetadata(time),
        )
    }

    @Deprecated("Use Kotlin instead, this should be removed when the refactor of `ScreenshotManager` is done")
    fun uploadAsync(
        path: Path,
        metadata: ClientScreenshotMetadata,
        onProgress: (percent: Int) -> Unit,
    ): CompletableFuture<Media> {
        return cmConnection.connectionScope.future { upload(path, metadata, onProgress) }
    }

    private suspend fun uploadFile(mediaId: String, url: String, headers: Map<String, String>?, fileData: ByteArray) {
        val request = if (headers != null) {
            buildPresignedUploadRequest(url, headers, fileData)
        } else {
            buildMultipartUploadRequest(url, fileData)
        }

        httpCall(request).use { response ->
            if (!response.isSuccessful) {
                throw MediaUploadFailedException(mediaId, response.code)
            }
        }
    }

    private fun buildPresignedUploadRequest(url: String, headers: Map<String, String>, fileData: ByteArray): Request {
        return Request.Builder()
            .url(url)
            .put(fileData.toRequestBody(IMAGE_PNG_MEDIA_TYPE))
            .headers(headers.filterKeys { !it.equals("content-type", ignoreCase = true) }.toHeaders())
            .build()
    }

    private fun buildMultipartUploadRequest(url: String, fileData: ByteArray): Request {
        return Request.Builder()
            .url(url)
            .post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "file", fileData.toRequestBody(IMAGE_PNG_MEDIA_TYPE))
                    .build()
            )
            .build()
    }

    private suspend fun resolveAuthorUsername(authorId: UUID): String {
        return try {
            UuidNameLookup.getName(authorId).asDeferred().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOGGER.warn("Failed to resolve username for user '{}'", authorId, e)

            // If we for some reason cannot resolve the author's username, then we can just fall back to the name
            // of the current user's session.
            return USession.activeNow().username
        }
    }

    private fun ClientScreenshotMetadata.toMediaMetadata(time: DateTime): MediaMetadata {
        val spsAddress = locationMetadata.identifier?.let { SpsAddress.parse(it) }

        return MediaMetadata(
            authorId,
            time,
            MediaLocationMetadata(
                locationMetadata.type.toNetworkType(),
                locationMetadata.identifier,
                spsAddress?.host,
            ),
            favorite,
            edited,
        )
    }

    companion object {
        private val IMAGE_PNG_MEDIA_TYPE = "image/png".toMediaType()

        private val LOGGER = LoggerFactory.getLogger(ScreenshotUploader::class.java)

        private const val AFTER_UPLOAD_URL_PROGRESS = 25
        private const val AFTER_USERNAME_PROGRESS = 75
    }
}

class MediaUploadFailedException(
    mediaId: String,
    statusCode: Int,
) : Exception("Failed to upload media with ID '$mediaId': status code $statusCode")
