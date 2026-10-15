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
package gg.essential.gui.screenshot.handler

import gg.essential.gui.screenshot.LocalScreenshot
import gg.essential.gui.screenshot.components.ScreenshotProviderManager
import gg.essential.gui.screenshot.concurrent.PrioritizedCallable
import gg.essential.gui.screenshot.concurrent.PriorityThreadPoolExecutor
import gg.essential.gui.screenshot.downsampling.PixelBuffer
import gg.essential.gui.screenshot.providers.FileCachedWindowedImageProvider
import gg.essential.gui.screenshot.providers.toSingleWindowRequest
import gg.essential.image.imagescaling.ResampleOp
import gg.essential.util.lwjgl3.Lwjgl3Loader
import gg.essential.util.lwjgl3.api.NativeImageReader
import io.netty.buffer.UnpooledByteBufAllocator
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class ScreenshotPreviewCache(
    essentialDirectory: Path,
    lwjgl3Loader: Lwjgl3Loader,
) {
    private val cacheDirectory = essentialDirectory.resolve("screenshot-cache")

    private val executor: PriorityThreadPoolExecutor = PriorityThreadPoolExecutor(1)

    private val logger = LoggerFactory.getLogger(ScreenshotPreviewCache::class.java)

    private val nativeImageReader: NativeImageReader = lwjgl3Loader.get()

    private val minResolutionProvider = ScreenshotProviderManager.createFileCachedBicubicProvider(
        ScreenshotProviderManager.minResolutionTargetResolution,
        executor,
        UnpooledByteBufAllocator.DEFAULT,
        essentialDirectory,
        nativeImageReader,
        precomputeOnly = true,
    )

    /**
     * Precomputes low-resolution previews of the screenshot at the provided source [Path].
     */
    fun precompute(source: Path) {
        executor.submit(PrecomputeTask(source, minResolutionProvider))
    }

    /**
     * Removes all cached previews of the screenshot at the provided source [Path].
     */
    fun evict(source: Path) {
        if (!cacheDirectory.isDirectory()) {
            return
        }

        val resolutionDirectories = try {
            cacheDirectory.listDirectoryEntries()
        } catch (e: IOException) {
            logger.error(
                "Failed to list directory entries in screenshot cache directory '{}'",
                cacheDirectory,
                e,
            )

            return
        }

        val fileName = source.fileName.toString()

        resolutionDirectories.forEach { resolutionDirectory ->
            if (!resolutionDirectory.isDirectory()) {
                return@forEach
            }

            try {
                resolutionDirectory.resolve(fileName).deleteIfExists()
            } catch (e: IOException) {
                logger.error(
                    "Failed to delete cached version of screenshot '{}' from resolution directory '{}'",
                    fileName,
                    resolutionDirectory,
                    e,
                )
            }
        }
    }

    private class PrecomputeTask(
        private val path: Path,
        private val provider: FileCachedWindowedImageProvider,
    ) : PrioritizedCallable<Unit>(
        windowIndex = Integer.MAX_VALUE,
        providerPriority = PRECOMPUTE,
        indexInWindow = 0,
    ) {
        override fun call() {
            try {
                ResampleOp.isBackgroundTask.set(true)

                provider.items = listOf(LocalScreenshot(path))

                provider.provide(0.toSingleWindowRequest(), emptySet())
                    .values
                    .forEach(PixelBuffer::release)
            } finally {
                ResampleOp.isBackgroundTask.set(false)
            }
        }
    }
}
