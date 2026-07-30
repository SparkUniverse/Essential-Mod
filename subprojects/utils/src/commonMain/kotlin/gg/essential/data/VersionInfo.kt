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
package gg.essential.data

open class VersionInfo {
    companion object {
        const val noSavedVersion = "0.0.0"
    }

    private val versionAndCommit: Pair<String, String> by lazy {
        val version = this::class.java.getResource("/assets/essential/version.txt")!!.readText()

        // Release build
        val commitFile = this::class.java.getResource("/assets/essential/commit.txt")
        if (commitFile != null) {
            return@lazy Pair(version, commitFile.readText().trim())
        }

        // Dev environment
        if (version == "\${version.get()}") {
            return@lazy Pair(noSavedVersion, "dev")
        }

        // Local build
        if (version.endsWith("-SNAPSHOT")) {
            return@lazy Pair(noSavedVersion, "SNAPSHOT")
        }

        // CI build
        val i = version.lastIndexOf("+g")
        if (i != -1) {
            return@lazy Pair(noSavedVersion, version.substring(i + 2))
        }

        return@lazy Pair(noSavedVersion, "unknown")
    }

    val essentialVersion: String
        get() = versionAndCommit.first
    val essentialCommit: String
        get() = versionAndCommit.second

    val essentialBranch: String = System.getProperty("essential.branch", "stable")
}
