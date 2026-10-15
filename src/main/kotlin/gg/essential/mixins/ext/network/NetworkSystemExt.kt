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
package gg.essential.mixins.ext.network

import net.minecraft.network.NetworkSystem
import java.net.SocketAddress

interface NetworkSystemExt {
    fun `essential$getIceEndpoint`(): SocketAddress
    //#if MC < 26.2
    fun `essential$removeLanEndpoint`()
    //#endif
}

val NetworkSystem.ext get() = this as NetworkSystemExt

fun NetworkSystem.getIceEndpoint() = ext.`essential$getIceEndpoint`()
//#if MC < 26.2
fun NetworkSystem.removeLanEndpoint() = ext.`essential$removeLanEndpoint`()
//#endif
