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
import gg.essential.gui.screenshot.downsampling.BufferBackedImage
import gg.essential.gui.screenshot.downsampling.ErrorImage
import gg.essential.gui.screenshot.downsampling.PixelBuffer
import gg.essential.util.lwjgl3.api.NativeImageReader
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import java.io.IOException

/**
 * Provide method is blocking and waits for file system
 */
class DiskWindowedImageProvider(
    private val nativeImageReader: NativeImageReader,
    private val allocator: ByteBufAllocator,
) : WindowedImageProvider {


    override var items: List<ScreenshotId> = emptyList()

    override fun provide(windows: List<WindowedProvider.Window>, optional: Set<ScreenshotId>): Map<ScreenshotId, PixelBuffer> {
        val map = mutableMapOf<ScreenshotId, PixelBuffer>()
        for (window in windows) {
            for (i in window.range.reversed(window.backwards)) {
                val id = items[i]
                if (id !in optional) {
                    map[id] = loadImage(id)
                }
            }
        }
        return map
    }

    private fun loadImage(id: ScreenshotId): PixelBuffer {
        //Will throw IOException if the image is corrupted
        try {
            val imageData =
                when (id) {
                    is LocalScreenshot -> nativeImageReader.getImageData(id.path, allocator)
                    is RemoteScreenshot -> {
                        val bytes = Unpooled.wrappedBuffer(id.open().use { it.readBytes() })
                        nativeImageReader.getImageData(bytes, allocator)
                    }
                }
            return BufferBackedImage(imageData)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ErrorImage()
    }
}