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

import gg.essential.gradle.multiversion.StripReferencesTransform
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

fun Project.universalLibs() {
    val versionCatalogs = extensions.getByType<VersionCatalogsExtension>()
    val catalog = versionCatalogs.named("libs")

    fun getVersion(name: String) = catalog.findVersion(name).orElseThrow()

    dependencies {
        val compileOnly = "compileOnly"

        val universalAttr = Attribute.of("universal", Boolean::class.javaObjectType)

        registerTransform(StripReferencesTransform::class.java) {
            from.attribute(universalAttr, false)
            to.attribute(universalAttr, true)
            parameters {
                excludes.add("net.minecraft")
            }
        }

        artifactTypes.all {
            attributes.attribute(universalAttr, false)
        }

        compileOnly(catalog.findBundle("kotlin").orElseThrow())

        // Provided by MC on modern versions, and by `:slf4j-to-log4j` on older versions
        compileOnly(catalog.findLibrary("slf4j-api").orElseThrow())
        // Provided by MC (should ideally migrate away from this as MC itself is migrating to slf4j)
        compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
        // Depending on LWJGL3 instead of 2 so we can choose opengl bindings only
        // Note that this will still include some methods that are not available on LWJGL2. If these are used by mistake,
        // it should be obvious when the code is tested as long as our main version is still 1.12.2. If we change that,
        // we should maybe considering changing this to lwjgl2 or building a small transformer that produces a jar
        // containing only methods and classes that are in both versions.
        compileOnly("org.lwjgl:lwjgl-opengl:3.3.1")
        // Depending on 1.8.9 for all of these because that's the oldest version we support
        compileOnly("com.google.code.gson:gson:2.2.4")
        compileOnly("com.google.guava:guava:17.0")
        compileOnly("commons-codec:commons-codec:1.9")
        compileOnly("io.netty:netty-all:4.0.23.Final")
        // These versions are configured in gradle/libs.versions.toml
        compileOnly("gg.essential:universalcraft-1.8.9-forge:${getVersion("universalcraft")}") {
            attributes { attribute(universalAttr, true) }
            isTransitive = false
        }
        compileOnly(catalog.findLibrary("elementa").orElseThrow())
        compileOnly(catalog.findLibrary("vigilance").orElseThrow())
    }
}
