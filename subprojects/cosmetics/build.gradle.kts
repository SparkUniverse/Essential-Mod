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
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("java-library")
    id("gg.essential.defaults")
}

kotlin {
    // Common dependencies
    dependencies {
        implementation(libs.kotlin.stdlib)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(project(":feature-flags"))
        api(project(":utils"))
    }

    // Common test dependencies
    dependencies {
        testImplementation(kotlin("test"))
    }

    // Minecraft dependencies
    dependencies {
        compileOnly("gg.essential:universalcraft-1.8.9-forge:228") { isTransitive = false }
    }
}

kotlin.jvmToolchain(8)
kotlin.compilerOptions.jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)

// Using src dirs matching what kotlin-multiplatform would do because we ideally want to be using that,
// but have moved away from it for the time being because it was too unreliable.
sourceSets.main {
    java.srcDir("src/commonMain/java")
    java.srcDir("src/minecraftMain/java")
    kotlin.srcDir("src/commonMain/kotlin")
    kotlin.srcDir("src/minecraftMain/kotlin")
}
sourceSets.test {
    java.srcDir("src/commonTest/java")
    java.srcDir("src/minecraftTest/java")
    kotlin.srcDir("src/commonTest/kotlin")
    kotlin.srcDir("src/minecraftTest/kotlin")
}
