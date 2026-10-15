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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import gg.essential.lib.gson.Gson
import gg.essential.util.httpGetToStringBlocking
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object MenuData {

    internal val BASE_URL = System.getProperty(
        "essential.api.url",
        System.getenv().getOrDefault("ESSENTIAL_API_URL", "https://api.essential.gg")
    )

    val CHANGELOGS: LoadingCache<String, CompletableFuture<Pair<String?, Changelog>>> = CacheBuilder.newBuilder()
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .build(CacheLoader.from { version -> CompletableFuture.supplyAsync { fetchChangelogs(version!!) } })

    // Fetch five changelogs before provided version and populate CHANGELOGS cache, then return current changelog
    private fun fetchChangelogs(version: String): Pair<String?, Changelog> {
        val encodedVersion = URLEncoder.encode(version, StandardCharsets.UTF_8.toString()).replace("+", "%20").replace("#", "%23")
        val dataString = httpGetToStringBlocking("$BASE_URL/mods/v1/essential:essential/changelogs?branch=${VersionData.essentialBranch}&before=$encodedVersion")
        val data = Gson().fromJson(dataString, Array<Changelog>::class.java).toList()

        var nextVersion: String? = null
        var previousVersion = version
        var previousLog: Changelog? = null

        for (log in data) {
            if (previousLog != null) {
                CHANGELOGS.put(previousVersion, CompletableFuture.completedFuture(Pair(log.version, previousLog)))
            } else {
                nextVersion = log.version
            }
            previousLog = log
            previousVersion = log.version
        }

        val versionResponse = httpGetToStringBlocking("$BASE_URL/mods/v1/essential:essential/versions/$encodedVersion/changelog")
        return Pair(nextVersion, Gson().fromJson(versionResponse, Changelog::class.java))
    }
}
