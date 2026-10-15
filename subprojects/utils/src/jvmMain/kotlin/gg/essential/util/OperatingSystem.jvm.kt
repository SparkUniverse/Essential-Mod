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

/* actual */ val os: OperatingSystem by lazy {
    val os = System.getProperty("os.name").lowercase()
    when {
        "windows" in os -> OperatingSystem.WINDOWS
        "mac" in os || "darwin" in os -> OperatingSystem.MACOS
        "linux" in os -> OperatingSystem.LINUX
        else -> OperatingSystem.OTHERS
    }
}
