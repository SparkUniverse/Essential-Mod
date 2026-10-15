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
package gg.essential.sps

import gg.essential.mixins.ext.client.resource.ResourcePackWithPath
import gg.essential.universal.UMinecraft.getMinecraft
import java.nio.file.Path

//#if MC >= 26.3
//$$ import kotlin.jvm.optionals.getOrNull
//#endif

class McLocalResourcePackIndex(essentialBaseDirectory: Path): LocalResourcePackIndex(essentialBaseDirectory) {

    override fun updatePaths(updateMinecraftPackRepo: Boolean) {
        val resourcePackRepository = getMinecraft().resourcePackRepository
        if (updateMinecraftPackRepo) {
            resourcePackRepository.updateRepositoryEntriesAll()
        }
        loadedResourcePacks.set(resourcePackRepository.repositoryEntriesAll
            //#if MC < 1.16
            .sortedBy { it.resourcePackName }
            //#endif
            //#if NEOFORGE
            //$$ // Undoes https://github.com/neoforged/NeoForge/commit/3b3223b6e5e1fe372d2b24713c0d96366ee3e86d#diff-fd8521cdc5d9e4fefca7c4e8dae5c1e33d1ce05ce6baff52fd08b5fc370d9588
            //$$ // for consistency across platforms and because having the vanilla packs always first doesn't seem all
            //$$ // too useful for our purposes.
            //$$ .sortedBy { it.id }
            //#endif
            .mapNotNull { pack ->
                //#if FORGELIKE && MC >= 1.16
                //$$ if (pack.isHidden) return@mapNotNull null
                //#endif

                //#if MC >= 26.3
                //$$ (pack.open().findFirst().getOrNull() as? ResourcePackWithPath)?.`essential$path`?.let { path ->
                //#else
                (pack.resourcePack as? ResourcePackWithPath)?.`essential$path`?.let { path ->
                //#endif
                    //#if MC>=11903
                    //$$ val resourcePackName = pack.displayName.string
                    //#else
                    val resourcePackName = pack.resourcePack.packName
                    //#endif
                    path.toAbsolutePath() to resourcePackName
                }
            }.toMap())
    }

}