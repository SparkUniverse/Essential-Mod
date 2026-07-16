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

import com.replaymod.gradle.preprocess.RootPreprocessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import java.io.File

fun Project.configurePreprocessTree(versions: File) {
    configure<RootPreprocessExtension> {
        strictExtraMappings.set(true)

        val fabric260200 = createNode("26.2-fabric", 260200, null)
        val fabric260100 = createNode("26.1-fabric", 260100, null)
        val fabric12111 = createNode("1.21.11-fabric", 12111, "yarn")
        val fabric12109 = createNode("1.21.9-fabric", 12109, "yarn")
        val neoForge12107 = createNode("1.21.7-neoforge", 12107, "srg")
        val forge12107 = createNode("1.21.7-forge", 12107, "srg")
        val fabric12107 = createNode("1.21.7-fabric", 12107, "yarn")
        val fabric12106 = createNode("1.21.6-fabric", 12106, "yarn")
        val neoForge12105 = createNode("1.21.5-neoforge", 12105, "srg")
        val forge12105 = createNode("1.21.5-forge", 12105, "srg")
        val fabric12105 = createNode("1.21.5-fabric", 12105, "yarn")
        val neoForge12104 = createNode("1.21.4-neoforge", 12104, "srg")
        val forge12104 = createNode("1.21.4-forge", 12104, "srg")
        val fabric12104 = createNode("1.21.4-fabric", 12104, "yarn")
        val neoForge12103 = createNode("1.21.3-neoforge", 12103, "srg")
        val forge12103 = createNode("1.21.3-forge", 12103, "srg")
        val fabric12103 = createNode("1.21.3-fabric", 12103, "yarn")
        val neoForge12101 = createNode("1.21.1-neoforge", 12101, "srg")
        val forge12101 = createNode("1.21.1-forge", 12101, "srg")
        val fabric12101 = createNode("1.21.1-fabric", 12101, "yarn")
        val neoForge12006 = createNode("1.20.6-neoforge", 12006, "srg")
        val forge12006 = createNode("1.20.6-forge", 12006, "srg")
        val fabric12006 = createNode("1.20.6-fabric", 12006, "yarn")
        val neoForge12004 = createNode("1.20.4-neoforge", 12004, "srg")
        val forge12004 = createNode("1.20.4-forge", 12004, "srg")
        val fabric12004 = createNode("1.20.4-fabric", 12004, "yarn")
        val forge12002 = createNode("1.20.2-forge", 12002, "srg")
        val fabric12002 = createNode("1.20.2-fabric", 12002, "yarn")
        val forge12001 = createNode("1.20.1-forge", 12001, "srg")
        val fabric12001 = createNode("1.20.1-fabric", 12001, "yarn")
        val fabric12000 = createNode("1.20-fabric", 12000, "yarn")
        val forge11904 = createNode("1.19.4-forge", 11904, "srg")
        val fabric11904 = createNode("1.19.4-fabric", 11904, "yarn")
        val forge11903 = createNode("1.19.3-forge", 11903, "srg")
        val fabric11903 = createNode("1.19.3-fabric", 11903, "yarn")
        val forge11902 = createNode("1.19.2-forge", 11902, "srg")
        val fabric11902 = createNode("1.19.2-fabric", 11902, "yarn")
        val fabric11900 = createNode("1.19-fabric", 11900, "yarn")
        val forge11802 = createNode("1.18.2-forge", 11802, "srg")
        val fabric11802 = createNode("1.18.2-fabric", 11802, "yarn")
        val fabric11801 = createNode("1.18.1-fabric", 11801, "yarn")
        val forge11701 = createNode("1.17.1-forge", 11701, "srg")
        val fabric11701 = createNode("1.17.1-fabric", 11701, "yarn")
        val fabric11605 = createNode("1.16.5-fabric", 11605, "yarn")
        val forge11605 = createNode("1.16.5-forge", 11605, "srg")
        val forge11202 = createNode("1.12.2-forge", 11202, "srg")
        val forge10809 = createNode("1.8.9-forge", 10809, "srg")

        fabric260200.link(fabric260100, versions.resolve("26.2-26.1.txt"))
        fabric260100.link(fabric12111, versions.resolve("26.1-1.21.11.txt"))
        fabric12111.link(fabric12109, versions.resolve("1.21.11-1.21.9.txt"))
        fabric12109.link(fabric12107, versions.resolve("1.21.9-1.21.7.txt"))
        neoForge12107.link(fabric12107)
        forge12107.link(fabric12107)
        fabric12107.link(fabric12106)
        fabric12106.link(fabric12105, versions.resolve("1.21.6-1.21.5.txt"))
        neoForge12105.link(fabric12105)
        forge12105.link(fabric12105)
        fabric12105.link(fabric12104, versions.resolve("1.21.5-1.21.4.txt"))
        neoForge12104.link(fabric12104)
        forge12104.link(fabric12104)
        fabric12104.link(fabric12103, versions.resolve("1.21.4-1.21.3.txt"))
        neoForge12103.link(fabric12103)
        forge12103.link(fabric12103)
        fabric12103.link(fabric12101)
        neoForge12101.link(fabric12101)
        forge12101.link(fabric12101)
        fabric12101.link(fabric12006)
        neoForge12006.link(fabric12006)
        forge12006.link(fabric12006)
        fabric12006.link(fabric12004)
        neoForge12004.link(fabric12004)
        forge12004.link(fabric12004)
        fabric12004.link(fabric12002, versions.resolve("1.20.4-1.20.2.txt"))
        forge12002.link(fabric12002)
        fabric12002.link(fabric12001, versions.resolve("1.20.2-1.20.1.txt"))
        forge12001.link(fabric12001)
        fabric12001.link(fabric12000)
        fabric12000.link(fabric11904)
        forge11904.link(fabric11904)
        fabric11904.link(fabric11903, versions.resolve("1.19.4-1.19.3.txt"))
        forge11903.link(fabric11903)
        fabric11903.link(fabric11902, versions.resolve("1.19.3-1.19.2.txt"))
        forge11902.link(fabric11902)
        fabric11902.link(fabric11900)
        fabric11900.link(fabric11802)
        forge11802.link(fabric11802)
        fabric11802.link(fabric11801)
        fabric11801.link(fabric11701)
        forge11701.link(fabric11701)
        fabric11701.link(fabric11605, versions.resolve("1.17.1-1.16.5.txt"))
        fabric11605.link(forge11605)
        forge11605.link(forge11202, versions.resolve("1.16.5-1.12.2.txt"))
        forge11202.link(forge10809, versions.resolve("1.12.2-1.8.9.txt"))
    }
}
