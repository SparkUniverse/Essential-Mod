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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package gg.essential.mixins.transformers.compatibility.librarianlib

import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Redirect

@Pseudo
@Mixin(targets = ["games.thecodewarrior.bitfont.data.BitGrid"])
class BitGridMixin {
    @Redirect(
        method = ["*"],
        at =
            //#if MC >= 26.3 && !FORGE
            //$$ [
            //#endif
            At("INVOKE", target = "owner=/^kotlin\\/UByteArray$/ name=/^get-impl\$/")
            //#if MC >= 26.3 && !FORGE
            //$$ ]
            //#endif
    )
    private fun fixUByteArrayGetImpl(bytes: UByteArray, index: Int): UByte {
        return bytes[index]
    }
}