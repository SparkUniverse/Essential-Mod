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
@file:JvmName("ParticleSystemJvmKt")

package gg.essential.model.util

import dev.folomeev.kotgl.matrix.vectors.Vec3
import gg.essential.model.ParticleSystem
import gg.essential.model.backend.RenderBackend
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

// Needed for java call in Mixin_RenderParticleSystemOfClientWorld & others, due to value class parameter 'lightOverride'.
// Also consumes a Java UUID instead of a Kotlin UUID for a similar reason, avoids having to call `toKotlinUuid` from
// Java.
@OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
fun ParticleSystem.render(
    matrixStack: UMatrixStack,
    cameraPos: Vec3,
    cameraRot: Quaternion,
    commandQueue: RenderBackend.CommandQueue,
    cameraUuid: UUID,
    cameraFirstPerson: Boolean,
    hideParticlesInFirstPerson: Boolean,
    onlyRenderFromSource: UUID? = null,
) = render(
    matrixStack,
    cameraPos,
    cameraRot,
    commandQueue,
    cameraUuid.toKotlinUuid(),
    cameraFirstPerson,
    hideParticlesInFirstPerson,
    onlyRenderFromSource?.toKotlinUuid(),
    lightOverride = null,
)
