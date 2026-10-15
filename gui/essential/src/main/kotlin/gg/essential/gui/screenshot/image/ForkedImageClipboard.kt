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
package gg.essential.gui.screenshot.image

import gg.essential.clipboard.AWTClipboard
import gg.essential.clipboard.Clipboard
import gg.essential.util.ForkedJvm
import gg.essential.util.OperatingSystem
import gg.essential.util.os
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Utility for setting the system clipboard to an image  */
class ForkedImageClipboard : AutoCloseable {
    private val jvm = ForkedJvm(ForkedImageClipboard::class.java.name, classpath())
    private val inputStream = DataInputStream(jvm.process.inputStream)

    override fun close() {
        // On Linux, we want to wait until something else takes ownership of the clipboard before closing.
        if (os != OperatingSystem.LINUX) {
            jvm.close()
        }
    }

    fun copy(file: File): Boolean {
        jvm.process.outputStream.write(file.absoluteFile.toString().encodeToByteArray())
        jvm.process.outputStream.flush()
        jvm.process.outputStream.close()
        return inputStream.read() == 1
    }

    companion object {
        private val extractedClipboardJar: Path by lazy {
            val jarResource = ForkedImageClipboard::class.java.getResource("clipboard.jar")
                ?: throw IllegalStateException("Failed to find clipboard jar")
            val tmpFile = Files.createTempFile("essential-clipboard", ".jar")
            tmpFile.toFile().deleteOnExit()
            jarResource.openStream().use {
                Files.copy(it, tmpFile, StandardCopyOption.REPLACE_EXISTING)
            }
            tmpFile.toAbsolutePath()
        }

        private fun classpath(): String {
            return extractedClipboardJar.toString() + System.getProperty("path.separator") +
                ForkedJvm.defaultClassPath(Clipboard::class.java, OperatingSystem::class.java)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val file = File(System.`in`.readBytes().decodeToString())
            if (!file.exists()) {
                System.out.write(0)
                System.out.close()

                return
            }

            val clipboard = Clipboard.current()
            val success = clipboard.copyPNG(file)

            System.out.write(if (success) 1 else 0)
            System.out.close()

            // We want to keep serving paste requests until another process takes ownership of the clipboard on Linux.
            if (os == OperatingSystem.LINUX && clipboard is AWTClipboard) {
                clipboard.lostOwnership.acquire()
            }
        }
    }
}