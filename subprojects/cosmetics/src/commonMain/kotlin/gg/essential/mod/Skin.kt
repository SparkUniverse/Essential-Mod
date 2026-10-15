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
package gg.essential.mod

import kotlinx.serialization.Serializable
import java.util.*
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Skin(
    val hash: String,
    val model: Model,
) {

    val url = String.format(Locale.ROOT, SKIN_URL, hash)

    companion object {

        // EM-2483: Minecraft uses http for skin textures and using https causes issues due to missing CA certs on outdated java versions.
        @Suppress("HttpUrlsUsage")
        const val SKIN_URL = "http://textures.minecraft.net/texture/%s"

        @JvmStatic
        fun fromUrl(url: String, model: Model) =
            Skin(hashFromUrl(url), model)

        @JvmStatic
        fun hashFromUrl(url: String): String =
            url.split("/").lastOrNull() ?: ""

        fun fromInfra(string: String): Skin {
            val parts = string.split(";")
            if (parts.size < 2) return Skin("invalid", Model.STEVE)
            return Skin(parts[1], if (parts[0] == "1") Model.ALEX else Model.STEVE)
        }

        val ALEX_SLIM = Skin("46acd06e8483b176e8ea39fc12fe105eb3a2a4970f5100057e9d84d4b60bdfa7", Model.ALEX)
        val ARI_SLIM = Skin("6ac6ca262d67bcfb3dbc924ba8215a18195497c780058a5749de674217721892", Model.ALEX)
        val EFE_SLIM = Skin("fece7017b1bb13926d1158864b283b8b930271f80a90482f174cca6a17e88236", Model.ALEX)
        val KAI_SLIM = Skin("226c617fde5b1ba569aa08bd2cb6fd84c93337532a872b3eb7bf66bdd5b395f8", Model.ALEX)
        val MAKENA_SLIM = Skin("7cb3ba52ddd5cc82c0b050c3f920f87da36add80165846f479079663805433db", Model.ALEX)
        val NOOR_SLIM = Skin("6c160fbd16adbc4bff2409e70180d911002aebcfa811eb6ec3d1040761aea6dd", Model.ALEX)
        val STEVE_SLIM = Skin("d5c4ee5ce20aed9e33e866c66caa37178606234b3721084bf01d13320fb2eb3f", Model.ALEX)
        val SUNNY_SLIM = Skin("b66bc80f002b10371e2fa23de6f230dd5e2f3affc2e15786f65bc9be4c6eb71a", Model.ALEX)
        val ZURI_SLIM = Skin("eee522611005acf256dbd152e992c60c0bb7978cb0f3127807700e478ad97664", Model.ALEX)

        val ALEX_WIDE = Skin("1abc803022d8300ab7578b189294cce39622d9a404cdc00d3feacfdf45be6981", Model.STEVE)
        val ARI_WIDE = Skin("4c05ab9e07b3505dc3ec11370c3bdce5570ad2fb2b562e9b9dd9cf271f81aa44", Model.STEVE)
        val EFE_WIDE = Skin("daf3d88ccb38f11f74814e92053d92f7728ddb1a7955652a60e30cb27ae6659f", Model.STEVE)
        val KAI_WIDE = Skin("e5cdc3243b2153ab28a159861be643a4fc1e3c17d291cdd3e57a7f370ad676f3", Model.STEVE)
        val MAKENA_WIDE = Skin("dc0fcfaf2aa040a83dc0de4e56058d1bbb2ea40157501f3e7d15dc245e493095", Model.STEVE)
        val NOOR_WIDE = Skin("90e75cd429ba6331cd210b9bd19399527ee3bab467b5a9f61cb8a27b177f6789", Model.STEVE)
        val STEVE_WIDE = Skin("31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb", Model.STEVE)
        val SUNNY_WIDE = Skin("a3bd16079f764cd541e072e888fe43885e711f98658323db0f9a6045da91ee7a", Model.STEVE)
        val ZURI_WIDE = Skin("f5dddb41dcafef616e959c2817808e0be741c89ffbfed39134a13e75b811863d", Model.STEVE)

        // See Minecraft's `DefaultSkinHelper` class
        val DEFAULT_SKINS = listOf<Skin>(
            ALEX_SLIM,
            ARI_SLIM,
            EFE_SLIM,
            KAI_SLIM,
            MAKENA_SLIM,
            NOOR_SLIM,
            STEVE_SLIM,
            SUNNY_SLIM,
            ZURI_SLIM,

            ALEX_WIDE,
            ARI_WIDE,
            EFE_WIDE,
            KAI_WIDE,
            MAKENA_WIDE,
            NOOR_WIDE,
            STEVE_WIDE,
            SUNNY_WIDE,
            ZURI_WIDE,
        )
        @JvmName("defaultFor")
        @OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
        fun default(uuid: Uuid) = DEFAULT_SKINS[uuid.hashCode().mod(DEFAULT_SKINS.size)]

        @JvmName("defaultPre1_19_3For")
        @OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
        fun defaultPre1_19_3(uuid: Uuid) =
            if ((uuid.hashCode() and 1) == 1) ALEX_SLIM else STEVE_WIDE
    }
}
