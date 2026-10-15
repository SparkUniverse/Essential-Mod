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

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.writeText

/**
 * This class keeps track the checksums of a set of files within a given directory.
 * It does not actively monitor the files but rather re-indexes all of them any time [update] is called.
 * Hover it does this in a way that is more efficient than having to fully read all files on each update.
 * Very much inspired by how git does it: https://git-scm.com/docs/racy-git/en
 *
 * Accessing entries does not perform any IO, only [update] does.
 * No entries will be present until the first call to [update].
 *
 * This class is **not** thread-safe.
 *
 * All files in the directory must be on the same file system for incremental updates to not miss any changes, and that
 * file system must have monotonically non-decreasing modification timestamps (though it does not need to be nano-second
 * accurate so long as its accuracy is consistent over time).
 */
class FilesIndexTracker(
    private val rootPath: Path,
) {
    private val entries: MutableMap<@Contextual Path, Entry> = mutableMapOf()
    private var latestObservedTime: Long = 0

    var ignore: (relPath: Path) -> Boolean = { false }

    fun getSha256Entries(): Map<Path, Pair<ByteArray, Long>> = entries.mapValues { it.value.sha256 to it.value.meta.size }

    fun getMd5(path: Path): ByteArray? =
        path.relativeToOrNull(rootPath)?.let { entries[it] }?.md5

    fun getSha256(path: Path): ByteArray? =
        path.relativeToOrNull(rootPath)?.let { entries[it] }?.sha256

    fun update(fileReader: FileReader = SimpleFileReader) {
        entries.values.forEach { it.visited = false }
        update(fileReader, rootPath, IsInThePast())
        entries.values.removeIf { !it.visited }
    }

    private fun update(fileReader: FileReader, path: Path, isInThePast: IsInThePast) {
        when {
            path.isDirectory() -> path.forEachDirectoryEntry { update(fileReader, it, isInThePast) }
            path.isRegularFile() -> checkEntry(fileReader, path, path.relativeToOrNull(rootPath) ?: return, isInThePast)
        }
    }

    private fun checkEntry(fileReader: FileReader, absPath: Path, relPath: Path, isInThePast: IsInThePast) {
        if (ignore(relPath)) {
            return
        }

        val meta = readMeta(absPath)

        val entry = entries[relPath]
        if (entry != null && entry.meta == meta) {
            entry.visited = true
            return // meta still matches, therefore content will be matching as well, so we don't need to update it
        }

        // We can only store the real meta if it is obviously in the past. If the file was modified in the current
        // second and the accuracy of the timestamps is only a second, we wouldn't be able to tell if it was modified
        // again after we read it but still within the same second.
        checkContent(fileReader, absPath, relPath, if (isInThePast(meta.mtime)) meta else MetaSnapshot.DIRTY)
    }

    private fun checkContent(fileReader: FileReader, absPath: Path, relPath: Path, meta: MetaSnapshot) {
        val md5 = MessageDigest.getInstance("MD5")
        val sha256 = MessageDigest.getInstance("SHA-256")
        var length = 0L
        fileReader.open(absPath, relPath).use { stream ->
            val buf = ByteArray(8196)
            while (true) {
                val len = stream.read(buf, 0, buf.size)
                if (len > 0) {
                    md5.update(buf, 0, len)
                    sha256.update(buf, 0, len)
                    length += len
                } else {
                    break
                }
            }
        }
        entries[relPath] = Entry(
            meta.copy(size = length),
            md5.digest(),
            sha256.digest(),
            visited = true,
        )
    }

    private fun readMeta(path: Path): MetaSnapshot {
        val attr = Files.readAttributes(path, BasicFileAttributes::class.java)
        return MetaSnapshot(
            size = attr.size(),
            ctime = attr.creationTime().toMillis(),
            mtime = attr.lastModifiedTime().toMillis(),
        )
    }

    private fun currentFileSystemTimeMillis(): Long {
        val testFile = (if (rootPath.isDirectory()) rootPath else rootPath.parent).resolve(".time-test.tmp")
        try {
            Files.write(testFile, byteArrayOf(1, 2, 3), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            return testFile.getLastModifiedTime().toMillis()
        } finally {
            Files.deleteIfExists(testFile)
        }
    }

    /**
     * A function which evaluates whether the given timestamp is definitively in the past.
     * "In the past" here means that it has a timestamp that is strictly smaller than whatever the file system
     * considers to be "now" (determined by writing a new temporary file to the directory).
     *
     * The "now" time is determined lazily the first time it is needed during any one [update] call, because if all
     * metadata still matches, then we don't need it at all
     */
    private inner class IsInThePast : (Long) -> Boolean {
        // Only update once per update operation (not once per indexed file)
        private var didUpdate = false

        override fun invoke(time: Long): Boolean {
            if (time < latestObservedTime) {
                return true
            }

            if (!didUpdate) {
                didUpdate = true
                latestObservedTime = currentFileSystemTimeMillis()
            }

            return time < latestObservedTime
        }
    }

    @Serializable
    private class Entry(
        val meta: MetaSnapshot,
        val md5: ByteArray,
        val sha256: ByteArray,
        @Transient
        var visited: Boolean = false,
    )

    @Serializable
    private data class MetaSnapshot(
        val size: Long,
        val ctime: Long,
        val mtime: Long,
    ) {
        companion object {
            // Randomly generated number, so hopefully no file will ever have a matching ctime/mtime.
            // Note: Unlike Git we cannot simply rely on the size because we want to later make use of the actual size.
            private const val MAGIC = 487961465L
            val DIRTY = MetaSnapshot(0, MAGIC, MAGIC)
        }
    }

    interface FileReader {
        fun open(absolutePath: Path, relativePath: Path): InputStream
    }

    object SimpleFileReader : FileReader {
        override fun open(absolutePath: Path, relativePath: Path): InputStream = absolutePath.inputStream()
    }

    companion object {
        fun read(file: Path, rootPath: Path): FilesIndexTracker {
            val index = FilesIndexTracker(rootPath)

            try {
                index.entries.putAll(createJsonInstance(rootPath).decodeFromString<Map<@Contextual Path, Entry>>(file.readText()))
            } catch (ignored: NoSuchFileException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return index
        }

        fun write(file: Path, index: FilesIndexTracker) {
            file.writeText(createJsonInstance(index.rootPath).encodeToString(index.entries))
        }

        private fun createJsonInstance(rootPath: Path): Json {
            return Json {
                serializersModule = SerializersModule {
                    contextual(PathAsStringSerializer(rootPath.fileSystem))
                }
            }
        }
    }
}
