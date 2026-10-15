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
plugins {
    kotlin("jvm")
    id("java-library")
}

kotlin.jvmToolchain(8)

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(project(":classloaders"))
    compileOnly("com.google.guava:guava:17.0") // will at runtime be provided by MC
    compileOnly("org.ow2.asm:asm-debug-all:5.2") // will at runtime be provided by MC
    compileOnly("io.netty:netty-all:4.0.23.Final") // will at runtime be provided by MC
}
