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
import gg.essential.gradle.util.*
import gg.essential.gradle.util.RelocationTransform.Companion.registerRelocationAttribute

plugins {
    id("java-library")
    id("gg.essential.defaults.repo")
    id("essential.utils")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

val relocated by configurations.creating {
    attributes {
        attribute(registerRelocationAttribute("relocated") {
            // Minecraft ships a Gson version which may be too old for some of our use cases, so we bundle our own
            relocate("com.google.gson", "gg.essential.lib.gson")
        }, true)
    }
}

dependencies {
    relocated("com.google.code.gson:gson:2.9.0")

    api(prebundle(relocated))

    api("com.squareup.okhttp3:okhttp:3.9.0")
}
