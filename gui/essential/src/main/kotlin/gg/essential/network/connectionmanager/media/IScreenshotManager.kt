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

import com.sparkuniverse.toolbox.chat.model.Channel
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.ScreenshotInfo
import gg.essential.gui.screenshot.ScreenshotUploadToast
import gg.essential.handlers.screenshot.ClientScreenshotMetadata
import gg.essential.media.model.Media
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

interface IScreenshotManager {
    val screenshotFolder: Path

    val screenshotMetadataManager: IScreenshotMetadataManager
    val screenshots: ListState<ScreenshotInfo>

    fun setFavorite(path: Path, value: Boolean): ClientScreenshotMetadata
    fun setFavorite(path: Media, value: Boolean): ClientScreenshotMetadata

    fun deleteFile(path: Path)
    fun deleteMedia(mediaId: String, localPath: Path?)

    fun handleScreenshotEdited(source: ScreenshotId, originalMetadata: ClientScreenshotMetadata, screenshot: BufferedImage, favorite: Boolean): File

    fun upload(path: Path, metadata: ClientScreenshotMetadata?, onProgress: Consumer<ScreenshotUploadToast.ToastProgress>): CompletableFuture<Media>

    fun uploadAndCopyLinkToClipboard(path: Path): CompletableFuture<Media>
    fun uploadAndCopyLinkToClipboard(path: Path, metadata: ClientScreenshotMetadata?): CompletableFuture<Media>
    fun copyLinkToClipboard(media: Media)

    fun uploadAndShareLinkToChannels(channels: List<Channel>, path: Path): CompletableFuture<Media>
    fun uploadAndShareLinkToChannels(channels: List<Channel>,path: Path, metadata: ClientScreenshotMetadata?): CompletableFuture<Media>
    fun shareLinkToChannels(channels: List<Channel>, media: Media)

    fun getUploadedLocalPathsCache(mediaId: String): List<Path>
}
