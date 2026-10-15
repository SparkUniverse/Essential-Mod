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
import gg.essential.gradle.util.prebundle

plugins {
    kotlin("jvm")
    id("java-library")
}

kotlin.jvmToolchain(8)

repositories {
    mavenCentral()
}

//
// We bundle all the lwjgl jars into a nested jar, so they aren't put on the classpath by default (otherwise they would
// conflict with the native lwjgl).
// We then create a dedicated class loader that explicitly reads from the nested jar.
//
val lwjgl3Bundle by configurations.creating
dependencies {
    val lwjglVersion = "3.3.3"
    for (module in listOf("", "-tinyfd", "-stb")) {
        lwjgl3Bundle("org.lwjgl:lwjgl$module:$lwjglVersion")
        for (platform in listOf("linux", "macos", "macos-arm64", "windows", "windows-x86")) {
            lwjgl3Bundle("org.lwjgl:lwjgl$module:$lwjglVersion:natives-$platform")
        }
    }
}
val bundledLwjgl3 = prebundle(lwjgl3Bundle, jijName = "gg/essential/util/lwjgl3/bundle.jar") {
    exclude("META-INF/INDEX.LIST") // list of packages, invalid for fat jar
    exclude("META-INF/*.SF", "META-INF/*.DSA") // signatures, broken for fat jar
}

dependencies {
    api(bundledLwjgl3) // externally expose only the bundled LWJGL3, so we don't pollute the classpath
    // this will at runtime be unpacked from above bundle, we want to compile against it
    configurations.compileOnly.configure { extendsFrom(lwjgl3Bundle) }

    api(project(":lwjgl3"))

    compileOnly("io.netty:netty-all:4.0.23.Final") // will at runtime be provided by MC
    compileOnly(project(":feature-flags"))
    compileOnly(libs.kotlin.stdlib.jdk8)
}
