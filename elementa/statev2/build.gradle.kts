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

import essential.universalLibs
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm")
    id("gg.essential.defaults")
}

universalLibs()

dependencies {
    implementation(project(":feature-flags"))

    testImplementation(kotlin("test"))
    testImplementation(libs.elementa)
}

tasks.test {
    useJUnitPlatform()
}

kotlin.compilerOptions.jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)

kotlin.jvmToolchain(8)
kotlin.compilerOptions.moduleName.set("essential" + project.path.replace(':', '-').lowercase())
