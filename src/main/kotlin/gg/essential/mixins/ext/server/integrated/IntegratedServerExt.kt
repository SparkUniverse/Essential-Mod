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
package gg.essential.mixins.ext.server.integrated

import gg.essential.sps.McIntegratedServerManager
import net.minecraft.server.integrated.IntegratedServer
import java.util.UUID

interface IntegratedServerExt {
    val `essential$manager`: McIntegratedServerManager
    //#if MC < 26.2
    fun `essential$undoLan`(host: UUID) // Replaced with IntegratedServer unpublishServer method in 26.2
    //#endif
}

val IntegratedServer.ext get() = this as IntegratedServerExt
val IntegratedServer.manager get() = ext.`essential$manager`

//#if MC < 26.2
fun IntegratedServer.undoLan(host: UUID) = ext.`essential$undoLan`(host)
//#endif
