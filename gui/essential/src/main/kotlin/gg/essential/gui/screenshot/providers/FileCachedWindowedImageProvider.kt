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
package gg.essential.gui.screenshot.providers

import gg.essential.gui.screenshot.LocalScreenshot
import gg.essential.gui.screenshot.RemoteScreenshot
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.concurrent.PrioritizedCallable
import gg.essential.gui.screenshot.concurrent.PriorityThreadPoolExecutor
import gg.essential.gui.screenshot.downsampling.BufferBackedImage
import gg.essential.gui.screenshot.downsampling.ErrorImage
import gg.essential.gui.screenshot.downsampling.PixelBuffer
import gg.essential.util.lwjgl3.api.ImageData
import gg.essential.util.lwjgl3.api.NativeImageReader
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

class FileCachedWindowedImageProvider(
    private val innerProvider: WindowedImageProvider,
    private val cacheFunction: ScreenshotId.() -> Path,
    private val writeExecutorPool: PriorityThreadPoolExecutor,
    private val nativeImageReader: NativeImageReader,
    private val alloc: ByteBufAllocator,
    // When true, this provider only ensures that each requested item has a cached value available.
    // Missing values are computed and cached
    private val precomputeOnly: Boolean
) : WindowedImageProvider {

    override var items: List<ScreenshotId> by innerProvider::items


    override fun provide(windows: List<WindowedProvider.Window>, optional: Set<ScreenshotId>): Map<ScreenshotId, PixelBuffer> {
        val providedImages = mutableMapOf<ScreenshotId, PixelBuffer>()
        for (window in windows) {
            for (i in window.range.reversed(window.backwards)) {
                val id = items[i]
                if (id in optional) {
                    continue
                }

                val cachePath = cacheFunction(id)
                if (cachePath.exists()) {
                    if (precomputeOnly) {
                        continue
                    }
                    val read = read(cachePath)
                    if (read != null) {
                        providedImages[id] = read
                        continue
                    }
                }

                val provide =
                    innerProvider.provide(i.toSingleWindowRequest(), emptySet()).entries.firstOrNull()
                        ?: continue

                providedImages[provide.key] = provide.value

                if (provide.value !is ErrorImage) {
                    provide.value.retain()
                    // We always want these to have a lower priority than any of the read or down sampling operations
                    writeExecutorPool.submit(
                        object : PrioritizedCallable<Nothing>(Int.MAX_VALUE, CACHE_WRITE, 0) {

                            override fun call(): Nothing? {
                                save(cachePath, provide.value)
                                provide.value.release()
                                return null
                            }
                        })
                }
            }
        }
        return providedImages
    }

    private fun read(path: Path): PixelBuffer? {
        //This will never return null unless an error occurs reading
        var bytes: ByteBuf? = null

        locks.compute(path.toAbsolutePath().toString()) { _, _ ->
            // sync needed because if file is in the middle of a write and we try to read it, it
            // will read an incomplete file and not parse
            try {
                FileChannel.open(path).use { fileChannel ->
                    val size = fileChannel.size().toInt()
                    val buf = Unpooled.directBuffer(size).also { bytes = it }
                    buf.writeBytes(fileChannel, size)
                }
            } catch (e: IOException) {
                LOGGER.warn("Failed to read cached image from $path", e)
            }
            null
        }

        return try {
            BufferBackedImage(nativeImageReader.getImageData(bytes ?: return null, alloc))
        } catch (e: IOException) {
            LOGGER.warn("Failed to parse cached image from $path", e)
            null
        } finally {
            bytes?.release()
        }
    }

    private fun save(path: Path, image: PixelBuffer) {
        // sync needed because if file is in the middle of a write and we try to read it, it will
        // read an incomplete file and not parse
        locks.compute(path.toAbsolutePath().toString()) { _, _ ->
            nativeImageReader.saveImage(
                path,
                ImageData(image.content(), image.getWidth(), image.getHeight(), image.getChannels())
            )
            null
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FileCachedWindowedImageProvider::class.java)

        private val locks = ConcurrentHashMap<String, Nothing>()

        fun inDirectory(directory: Path): ScreenshotId.() -> Path = {
            when (this) {
                is LocalScreenshot -> directory.resolve(path.fileName.toString())
                is RemoteScreenshot -> directory.resolve(media.id)
            }
        }
    }

}
