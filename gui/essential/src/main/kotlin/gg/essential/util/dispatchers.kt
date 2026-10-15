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

import gg.essential.util.GuiEssentialPlatform.Companion.platform
import kotlinx.coroutines.Dispatchers

@Suppress("unused") // receiver is specified to mirror builtin dispatchers
@Deprecated("Equivalent to `Dispatchers.Game.immediate` (except in case of nested calls, see the \"Event loop\" section of `Dispatchers.Unconfined` kdocs). " +
        "For use with `asExecutor`, beware that the new dispatcher will not catch uncaught exceptions and instead crash the game (coroutines exception handling is unaffected).")
val Dispatchers.Client
    get() = platform.clientThreadDispatcher
