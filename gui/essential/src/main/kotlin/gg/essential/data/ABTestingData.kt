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

import gg.essential.util.GuiEssentialPlatform.Companion.platform
import java.io.File

object ABTestingData {
    private val abFile = File(platform.essentialBaseDir.toFile(), "expFeatures.json")
    private val abFeatures = mutableSetOf<String>()

    fun addData(name: String) {
        if (!hasData(name)) {
            abFeatures.add(name)
            abFile.appendText(name + System.lineSeparator())
        }
    }

    fun hasData(name: String): Boolean {
        return name in abFeatures
    }

    init {
        // Load initial data
        abFile.createNewFile()
        abFeatures.addAll(abFile.readLines())
    }
}
