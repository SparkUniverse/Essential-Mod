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
package essential

import gg.essential.gradle.multiversion.Platform
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
}

val platform: Platform by extensions

val dependency = when {
    platform.isFabric -> "gg.essential:loader-fabric"
    platform.isNeoForge && platform.mcVersion >= 1_21_11 -> "gg.essential:loader-fml10"
    platform.isModLauncher && platform.mcVersion >= 11700 -> "gg.essential:loader-modlauncher9"
    platform.isModLauncher -> "gg.essential:loader-modlauncher8"
    platform.isLegacyForge -> "gg.essential:loader-launchwrapper"
    else -> throw UnsupportedOperationException("No known loader variant for current platform.")
}

val loader: Configuration by configurations.creating

dependencies {
    loader(dependency)
}

tasks.jar {
    if (platform.isLegacyForge) {
        dependsOn(loader)
        from({ loader.map { zipTree(it) } })

        manifest {
            attributes(
                "FMLModType" to "LIBRARY",
                "TweakClass" to "gg.essential.loader.stage0.EssentialSetupTweaker",
                "TweakOrder" to "0",
            )
        }
    }

    if (platform.isNeoForge && platform.mcVersion >= 1_21_11) {
        dependsOn(loader)
        from({ loader.map { zipTree(it) } })
    }

    if (platform.isFabric) {
        // We include the jar ourselves (and configure our fabric.mod.json accordingly), so we have control over where
        // the embedded jars are located. This is important because the default location of `META-INF/jars` gets special
        // treatment by loader-stage2, and we don't want that for the embedded loader.
        from(loader) {
            rename { "essential-loader.jar" }
        }
    }
}

/**
 * Extracts the stage1 jar from the given input stage0 jar.
 * We do this so we don't need to do any double-unpacking at runtime when we upgrade the installed stage1 version, and
 * because the raw loader stored above will actually be stripped when the Essential jar is installed via stage2 (because
 * the regular upgrade path would break with relaunching; we instead need to use the `stage1.update.jar` path).
 */
abstract class ExtractStage1JarTask : DefaultTask() {
    @get:InputFile
    abstract val stage0: RegularFileProperty
    @get:OutputFile
    abstract val stage1: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = stage0.get().asFile.toPath()
        val output = stage1.get().asFile.toPath()
        FileSystems.newFileSystem(input, null as ClassLoader?).use { sourceFs ->
            val source = sourceFs.getPath("gg/essential/loader/stage0/stage1.jar")
            Files.createDirectories(output.parent)
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

val extractStage1Jar by tasks.registering(ExtractStage1JarTask::class) {
    stage0.fileProvider(loader.elements.map { it.single().asFile })
    stage1.set(layout.buildDirectory.file("loader-stage1.jar"))
}

tasks.named<Jar>("bundleJar") {
    from(extractStage1Jar.flatMap { it.stage1 }) {
        into("gg/essential/")
    }
}
