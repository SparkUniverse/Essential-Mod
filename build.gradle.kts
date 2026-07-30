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
import gg.essential.gradle.util.*
import gg.essential.gradle.util.StripKotlinMetadataTransform.Companion.registerStripKotlinMetadataAttribute
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.objectweb.asm.TypePath

plugins {
    id("kotlin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("gg.essential.defaults")
    id("gg.essential.defaults.repo")
    id("gg.essential.multi-version")
    id("gg.essential.bundle")
    id("gg.essential.relocate")
    id("gg.essential.mixin")
    id("essential.embedded-loader")
    id("essential.pinned-jar")
}

val mcVersion: Int by project.extra
val mcVersionStr: String by project.extra
val mcPlatform: String by project.extra

repositories {
    mavenLocal()
}
base.archivesName.set("Essential " + project.name)

val stripKotlinMetadata = registerStripKotlinMetadataAttribute("strip-kotlin-metadata")

dependencies {
    implementation(bundle(project(":feature-flags"))!!)
    implementation(bundle(project(":libs"))!!)
    implementation(bundle(project(":infra"))!!)
    implementation(bundle(project(":vigilance2"))!!)
    implementation(bundle(project(":gui:elementa"))!!)
    implementation(bundle(project(":gui:essential"))!!)
    implementation(bundle(project(":gui:vigilance"))!!)
    implementation(project(":api:" + project.name, configuration = if (platform.isUnobfuscated) null else "namedElements"))
    bundle(project(":api:" + project.name))

    implementation(bundle("com.github.KevinPriv:keventbus:c52e0a2") {
        attributes { attribute(stripKotlinMetadata, true) }
    })
    implementation(bundle(project(":kdiscordipc"))!!)

    implementation(bundle(project(":cosmetics"))!!)

    implementation(bundle(project(":lwjgl3"))!!)
    runtimeOnly(bundle(project(":lwjgl3:impl"))!!)


    // In order to get proper IDE support, we want to use a non-relocated MixinExtras version in dev.
    // This gets transformed by `relocatedJar` to use our bundled relocated version for production.
    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:${libs.versions.mixinextras.get()}")!!)
    listOf(configurations.implementation, configurations.annotationProcessor).forEach {
        it.configure {
            exclude(group = "gg.essential.lib", module = "mixinextras")
        }
    }

    // Some of our dependencies rely on slf4j but that's not included in MC prior to 1.17, so we'll manually bundle a
    // log4j adapter for those versions
    // We also bundle it for version 1.17-1.19.2 because those ship slf4j 1.x and only 1.19.3+ starts shipping 2.x
    if (platform.mcVersion < 11903) {
        implementation(bundle(project(":slf4j-to-log4j"))!!)
    }
    implementation(bundle(project(":quic-connector"))!!)
    implementation(bundle(project(":pseudotcp"))!!)

    implementation(bundle(project(":clipboard"))!!)
    implementation(bundle(project(":utils"))!!)
    implementation(bundle(project(":plasmo"))!!)
    implementation(bundle(project(":minecraft-auth"))!!)
    if (platform.mcVersion >= 11800) {
        implementation(bundle(project(":immediatelyfast"))!!)
    }
    if (platform.mcVersion >= 12105 && (platform.isFabric || platform.isNeoForge)) {
        repositories.modrinth()
        if (platform.mcVersion >= 12106) {
            modCompileOnly("maven.modrinth:iris:1.9.1+1.21.7-${platform.loaderStr}")
        } else {
            modCompileOnly("maven.modrinth:iris:1.8.11+1.21.5-${platform.loaderStr}")
        }
    }

    testImplementation(kotlin("test"))

    if (platform.isFabric && mcVersion >= 11600) {
        repositories.modrinth()
        val modMenuDependency = "maven.modrinth:modmenu:${when {
            platform.mcVersion >= 26_01_00 -> "18.0.0-alpha.3"
            platform.mcVersion >= 11800 -> "3.0.0"
            platform.mcVersion <= 11700 -> "1.16.22"
            else -> "2.0.14"
        }}"
        val modMenuInDev = mcVersion < 11802 // included fabric-screen-api-v1 is incompatible with 1.18.2
        if (modMenuInDev) {
            modImplementation(modMenuDependency)
        } else {
            modCompileOnly(modMenuDependency)
        }
    }

    // for fancy menu v3 injecting
    if (mcVersion >= 1_20_00) {
        repositories.modrinth()
        when {
            platform.isFabric -> modCompileOnly("maven.modrinth:fancymenu:L92PEacB")
            platform.isForge -> modCompileOnly("maven.modrinth:fancymenu:MbyfTTsz")
            platform.isNeoForge && mcVersion >= 1_20_04 -> modCompileOnly("maven.modrinth:fancymenu:EX9HJeRD")
        }
    } else if (mcVersion >= 1_18_00) {
        repositories.modrinth()
        when {
            platform.isFabric -> modCompileOnly("maven.modrinth:fancymenu:MLK7D2vG")
            platform.isForge -> modCompileOnly("maven.modrinth:fancymenu:qwfP40Aa")
        }
    }

    // Want to test with Optifine in your development environment?
    // Set this to true, reload the Gradle project and add the Optifine jar into your mods folder.
    // Bonus: Run with -Doptifabric.extract=true to get extracted and remapped OF classes/patches in `run/.optifine`.
    val optifabricInDev = false
    if (optifabricInDev) {
        modImplementation("com.github.Chocohead:OptiFabric:e570a19") {
            exclude(group = "net.fabricmc")
            exclude(group = "net.fabricmc.fabric-api")
        }
        modImplementation("net.fabricmc.fabric-api:fabric-api:0.40.0+1.17")
    }

    if (platform.isFabric && platform.mcVersion >= 12006) {
        val fapiVersion = when (platform.mcVersion) {
            12006 -> "0.97.8+1.20.6"
            12101 -> "0.99.2+1.21"
            12103 -> "0.106.0+1.21.2"
            12104 -> "0.110.0+1.21.4"
            12105 -> "0.119.0+1.21.5"
            12106 -> "0.126.0+1.21.6"
            12107 -> "0.128.1+1.21.7"
            12109 -> "0.133.13+1.21.9"
            12111 -> "0.139.4+1.21.11"
            26_01_00 -> "0.143.14+26.1"
            26_02_00 -> "0.151.0+26.2"
            else -> error("No fabric API version configured!")
        }
        include(modImplementation(fabricApi.module("fabric-api-base", fapiVersion))!!)
        include(modImplementation(fabricApi.module("fabric-networking-api-v1", fapiVersion))!!)
    }

    // Dependencies for The Aether Mod gloves cosmetic hiding, @see [AetherGlovesCompat]
    val mc = platform.mcVersion
    when {
        mc >= 12100 && !platform.isForge -> "accessories:1.1.0-beta.52+1.21.1"
        mc == 12004 && platform.isNeoForge -> "curios:7.4.3+1.20.4"
        mc == 12001 && platform.isFabric -> "accessories:1.0.0-beta.48+1.20.1"
        mc == 12001 && platform.isForge -> "curios:1.19.2-5.1.6.4"
        mc >= 11902 && mc < 12001 && platform.isForge -> "curios:1.19.2-5.1.6.4"
        mc == 11202 -> "aether:1.12.2-v1.5.4.1"
        else -> null
    }?.let {
        repositories.modrinth()
        modCompileOnly("maven.modrinth:$it")
    }


    constraints {
        val kotlin = KotlinVersion.minimal
        val reason: DependencyConstraint.() -> Unit = {
            because("this is the most recent version supported by all platforms")
        }
        for (name in listOf("stdlib", "stdlib-common", "stdlib-jdk7", "stdlib-jdk8")) {
            implementation("org.jetbrains.kotlin:kotlin-$name:${kotlin.stdlib}!!", reason)
        }
        implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlin.stdlib}!!", reason)
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlin.coroutines}!!", reason)
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${kotlin.coroutines}!!", reason)
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${kotlin.serialization}!!", reason)
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${kotlin.serialization}!!", reason)
    }
}

if (platform.isFabric) {
    // Compile against the oldest fabric-loader version we support, so we don't accidentially use APIs available
    // only in newer versions
    configurations.compileClasspath {
        resolutionStrategy.force("net.fabricmc:fabric-loader:0.11.0")
    }
}

tasks.jar {
    manifest {
        attributes(
            "ModSide" to "CLIENT",
            "FMLCorePluginContainsFMLMod" to "Yes, yes it does",
            "Main-Class" to "gg.essential.main.Main",
        )
    }
    if (!platform.isFabric) {
        manifest {
            if (mcVersion >= 11400) {
                attributes("MixinConfigs" to "mixins.essential.json,mixins.essential.init.json,mixins.essential.modcompat.json,mixins.essential.tests.json")
                attributes("Requires-Essential-Stage2-Version" to "1.8.0")
            } else {
                attributes("Requires-Essential-Stage2-Version" to "1.7.0")
            }
        }
    }
}

// For legacy Forge, we need to use a custom tweaker to get mixin bootstrapped
if (platform.isLegacyForge) {
    loom.runs.named("client") {
        programArgs("--tweakClass", "gg.essential.dev.DevelopmentTweaker")
    }
}

// Essential is a client-side only mod
loom.noServerRunConfigs()

// Enable dev-only feature flag
loom.runs.named("client") {
    property("essential.feature.dev_only", "true")
}

// We need to use the compatibility mode on old versions because we used to use the old Kotlin defaults for those, and
// we need to match the API to be able to override its methods.
kotlin.compilerOptions.jvmDefault.set(if (platform.mcVersion >= 11400) JvmDefaultMode.NO_COMPATIBILITY else JvmDefaultMode.ENABLE)

tasks.relocatedJar {
    //Discord
    relocate("dev.cbyrne.kdiscordipc", "gg.essential.lib.kdiscordipc")

    // MojangAPI & keventbus
    relocate("me.kbrewster", "gg.essential.lib.kbrewster")
    relocate("okhttp3", "gg.essential.lib.okhttp3")
    relocate("okio", "gg.essential.lib.okio")

    // pseudotcp
    relocate("org.ice4j", "gg.essential.lib.ice4j")

    // EssentialMarkdown
    relocate("org.commonmark", "gg.essential.lib.commonmark")

    // connection-manager
    relocate("org.java_websocket", "gg.essential.lib.websocket")

    // cosmetics
    relocate("dev.folomeev.kotgl", "gg.essential.lib.kotgl")

    if (mcVersion < 11903) {
        // Slf4j
        relocate("org.slf4j", "gg.essential.lib.slf4j")
    }

    // MixinExtras
    relocate("com.llamalad7.mixinextras", "gg.essential.lib.mixinextras")
}

tasks.processResources {
    val version = project.provider { project.version }
    inputs.property("project_version", version)
    filesMatching("assets/essential/version.txt") {
        expand(mapOf("version" to version))
    }
    if (platform.isNeoForge && platform.mcVersion < 12005) {
        // NeoForge still uses the old mods.toml name until 1.20.5
        filesMatching("META-INF/neoforge.mods.toml") {
            name = "mods.toml"
        }
        exclude("META-INF/mods.toml")
    }
}

tasks.test {
    useJUnitPlatform()
}

if (platform.isUnobfuscated) {
    val nullableAnnotationsSpec = NullableAnnotationProcessor.spec {
        "net.minecraft.world.entity.Entity" {
            method("equals", "obj")
        }
        "net.minecraft.client.renderer.entity.EntityRenderDispatcher" {
            method("prepare", "crosshairPickEntity")
        }
        "com.mojang.blaze3d.systems.RenderSystem" {
            method("setProjectionMatrix", "projectionMatrixBuffer")
            method("setShaderFog", "fog")
            method("setShaderLights", "buffer")
        }
        "net.minecraft.server.packs.resources.PreparableReloadListener" {
            method("reload") {
                returnType(TypePath.fromString("0")) // CompletableFuture<Void?>
            }
        }
        "net.minecraft.server.packs.resources.PreparableReloadListener\$PreparationBarrier" {
            method("wait") {
                generic("T")
            }
        }
    }
    loom {
        addMinecraftJarProcessor(NullableAnnotationProcessor::class.java, "essential:nullable_annotations", nullableAnnotationsSpec)
    }
}

