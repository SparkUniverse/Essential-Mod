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
import essential.*
import gg.essential.gradle.util.prebundle

plugins {
    kotlin("jvm")
    `java-library`
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

repositories {
    mavenCentral()
    jitpack()
}

val jnapple by configurations.creating

configurations.compileOnly.configure {
    extendsFrom(jnapple)
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)

    // We could use JNA 3.x from 1.8.9, but `Native.load` is `Native.loadLibrary` in that version.
    // `Native.loadLibrary` is deprecated in 5.x, and is only provided for compatibility with older versions.
    // It's safer to include the version provided by JNApple to avoid any unknown behavior.
    jnapple("com.github.caoimhebyrne:JNApple:0211173")

    // This is included in the main Essential JAR
    compileOnly(project(":utils"))

    api(prebundle(jnapple, jijName = "gg/essential/gui/screenshot/image/clipboard.jar") {
        exclude("META-INF/INDEX.LIST") // list of packages, invalid for fat jar
        exclude("META-INF/*.SF", "META-INF/*.DSA") // signatures, broken for fat jar
    })
}
