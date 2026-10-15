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

val junixsocket by configurations.creating

configurations.compileOnly.configure {
    extendsFrom(junixsocket)
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(project(":classloaders"))

    junixsocket("com.kohlschutter.junixsocket:junixsocket-core:2.6.2")

    api("com.github.caoimhebyrne:KDiscordIPC:0.2.5") {
        exclude(group = "com.kohlschutter.junixsocket")
    }

    api(prebundle(junixsocket, jijName = "gg/essential/util/kdiscordipc/bundle.jar") {
        exclude("META-INF/INDEX.LIST") // list of packages, invalid for fat jar
        exclude("META-INF/*.SF", "META-INF/*.DSA") // signatures, broken for fat jar
    })
}
