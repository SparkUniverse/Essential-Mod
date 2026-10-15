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
import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("base")
    id("essential.utils")
    id("gg.essential.multi-version.root") version "0.2.2"
}

if (version == "unspecified") {
    version = versionFromBuildIdAndBranch()
    if ("-SNAPSHOT" !in version.toString()) version = version.toString() + "+g" + commit()
}

fun commit(): String =
    providers.exec {
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.map { it.trim().slice(0 until 10) }
        // FIXME should ideally not read this here because it makes the configuration cache less effective
        //  but `version` doesn't yet support `Provider`: https://github.com/gradle/gradle/issues/13672
        .get()

configurePreprocessTree(file("versions"))


// Workaround for https://github.com/gradle/gradle/issues/4823
project(":api").subprojects.forEach { it.evaluationDependsOn(":api") }

// remapJar tasks can eat quite a lot of RAM if many are ran in parallel.
// TODO: This has only become an issue with Loom 1.2, maybe it's a bug?
// This shared service serves as a way to limit that.
abstract class LimitedConcurrentRemapService : BuildService<BuildServiceParameters.None>
val concurrentRemapLimit = gradle.sharedServices.registerIfAbsent("concurrentRemapLimit", LimitedConcurrentRemapService::class.java) {
    maxParallelUsages.set(4)
}
allprojects {
    tasks.withType<RemapJarTask> {
        usesService(concurrentRemapLimit)
    }
}

allprojects {
    tasks.withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

allprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.apiVersion.set(libs.versions.kotlin.map { dependencyVersion ->
                val (major, minor, patch) = dependencyVersion.toString().split(".")
                org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$major.$minor")
            })
        }
    }
}
