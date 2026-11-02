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
package gg.essential.model

import gg.essential.cosmetics.events.AnimationEvent
import gg.essential.cosmetics.skinmask.SkinMask
import gg.essential.model.backend.PlayerPose
import gg.essential.model.backend.RenderBackend
import gg.essential.model.bones.BakedAnimations
import gg.essential.model.bones.BedrockModelState
import gg.essential.model.bones.retrievePose
import gg.essential.model.file.AnimationFile
import gg.essential.model.file.ModelFile
import gg.essential.model.file.ParticlesFile
import gg.essential.model.file.SoundDefinitionsFile
import gg.essential.model.util.UMatrixStack
import gg.essential.model.util.UVertexConsumer
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.network.cosmetics.Cosmetic.Diagnostic
import kotlin.jvm.JvmField

// TODO clean up
class BedrockModel(
    val cosmetic: Cosmetic,
    val variant: String,
    data: ModelFile?,
    val animationData: AnimationFile?,
    val particleData: Map<String, ParticlesFile>,
    val soundData: SoundDefinitionsFile?,
    var texture: RenderBackend.Texture?,
    var emissiveTexture: RenderBackend.Texture?,
    val skinMasks: Map<Side?, SkinMask>,
) {
    val diagnostics: List<Diagnostic>

    @JvmField
    var boundingBoxes: List<Pair<Box3, Side?>>
    val bones: Bones
    val defaultRenderGeometry: RenderGeometry
    var textureFrameCount = 1
    var translucent = false
    var animations: List<Animation>
    var animationEvents: List<AnimationEvent>

    // Stores all the different bone sides that are configured in this model
    val sideOptions: Set<Side>
    // Stores whether this model contains bones that hide on a specific side
    val isContainsSideOption: Boolean
        get() = sideOptions.isNotEmpty()

    init {
        val diagnostics = mutableListOf<Diagnostic>()

        val texture = texture
        if (data != null) {
            val parser = ModelParser(cosmetic, texture?.width ?: 64, texture?.height ?: 64)
            val result = parser.parse(data) ?: Pair(Bones(), listOf(emptyList()))
            bones = result.first
            defaultRenderGeometry = result.second
            boundingBoxes = parser.boundingBoxes
            textureFrameCount = parser.textureFrameCount
            translucent = parser.translucent
        } else {
            bones = Bones()
            defaultRenderGeometry = listOf(emptyList())
            boundingBoxes = emptyList()
        }

        if (translucent && emissiveTexture != null) {
            // This is unsupported right now because we can't combine emissive and non-emissive textures into a single
            // draw call, which is required for proper translucency sorting
            diagnostics.add(Diagnostic.fatal("Model cannot be both emissive and translucent at the same time."))
            translucent = false
        }

        val textureSize = texture?.let { it.width to it.height } ?: (64 to 64)
        val emissiveSize = emissiveTexture?.let { it.width to it.height }
        if (emissiveSize != null && textureSize != emissiveSize) {
            val expected = "${textureSize.first}x${textureSize.second}"
            val actual = "${emissiveSize.first}x${emissiveSize.second}"
            val msg = "Emissive texture must be same size as regular texture ($expected) but is $actual"
            diagnostics.add(Diagnostic.error(msg))
        }

        sideOptions = bones.mapNotNull { it.side }.toSet()

        val particleEffects = mutableMapOf<String, ParticleEffect>()
        val soundEffects = mutableMapOf<String, SoundEffect>()

        if (data != null) {
            for ((path, file) in particleData) {
                val config = file.particleEffect
                val identifier = config.description.identifier

                val existingParticle = particleEffects[identifier]
                if (existingParticle != null) {
                    val msg = "Particle with id `$identifier` is already defined in `${existingParticle.file}`."
                    diagnostics.add(Diagnostic.error(msg, file = path))
                    continue
                }

                val material = config.description.basicRenderParameters.material
                particleEffects[identifier] =
                    ParticleEffect(
                        path,
                        identifier,
                        material,
                        config.components,
                        config.curves,
                        config.events,
                    )
            }
        }

        val particleEffectRefs = particleEffects.mapValues {
            ParticleEffectWithReferencedEffects(it.key, particleEffects, soundEffects)
        }

        if (soundData != null) {
            for ((identifier, definition) in soundData.definitions) {
                val sounds = definition.sounds.mapNotNull { sound ->
                    val path = sound.name + ".ogg"
                    val asset = cosmetic.assets(variant).allFiles[path]
                        ?: return@mapNotNull null.also {
                            val msg = "File `$path` not found."
                            diagnostics.add(Diagnostic.error(msg, file = "sounds/sound_definitions.json"))
                        }
                    SoundEffect.Entry(
                        asset,
                        sound.stream,
                        sound.interruptible,
                        sound.volume,
                        sound.pitch,
                        sound.looping,
                        sound.directional,
                        sound.weight,
                    )
                }
                soundEffects[identifier] =
                    SoundEffect(
                        identifier,
                        definition.category,
                        definition.minDistance,
                        definition.maxDistance,
                        definition.fixedPosition,
                        sounds,
                    )
            }
        }

        if (animationData != null) {
            val referencedAnimations = animationData.triggers.flatMapTo(mutableSetOf()) { trigger ->
                generateSequence(trigger) { it.onComplete }.map { it.name }
            }

            animations = animationData.animations.map { Animation(it.key, it.value, bones, particleEffectRefs, soundEffects) }
                .filter { animation ->
                    when {
                        animation.name !in referencedAnimations -> false
                        animation.animationLength <= 0f -> {
                            val msg = "Animation `${animation.name}` has zero or negative duration."
                            diagnostics.add(Diagnostic.error(msg, file = "animations.json"))
                            false
                        }
                        else -> true
                    }
                }
            animationEvents = animationData.triggers
        } else {
            animations = emptyList()
            animationEvents = emptyList()
        }

        this.diagnostics = diagnostics
    }

    fun getAnimationByName(name: String): Animation? {
        for (animation in animations) {
            if (animation.name == name) {
                return animation
            }
        }
        return null
    }

    fun computePose(basePose: PlayerPose, animationState: ModelAnimationState): PlayerPose {
        if (animationState.active.none { it.animation.affectsPose }) {
            return basePose
        }
        val modelState = BedrockModelState(
            basePose,
            animationState.bake(bones),
            Vector3.ZERO,
            null,
            emptySet(),
            EnumPart.entriesSet,
        )
        modelState.apply(bones)
        val pose = retrievePose(bones, basePose)
        modelState.reset(bones)
        return pose
    }

    /**
     * Renders the model
     *
     * Note: [Bone.resetAnimationOffsets] or equivalent must be called before calling this method.
     *
     * @param matrixStack
     */
    fun render(
        matrixStack: UMatrixStack,
        queue: RenderBackend.CommandQueue,
        geometry: RenderGeometry,
        bakedAnimations: BakedAnimations,
        metadata: RenderMetadata,
        lifetime: Float,
    ) {
        val textureLocation = texture ?: metadata.skin

        val totalFrames = textureFrameCount.toFloat()
        val frame = (lifetime * TEXTURE_ANIMATION_FPS).toInt()
        val offset = frame % totalFrames / totalFrames

        val modelState = BedrockModelState(
            metadata.pose,
            bakedAnimations,
            metadata.positionAdjustment,
            metadata.side,
            metadata.hiddenBones,
            metadata.parts,
        )

        val matrix = matrixStack.peek().deepCopy()

        fun render(vertexConsumer: UVertexConsumer) {
            modelState.apply(bones)

            val matrixStack = UMatrixStack(mutableListOf(matrix.deepCopy()))
            matrixStack.scale(1f / 16f)
            bones.byPart.values.forEach { bone ->
                bone.resetAnimationOffsets(false) // animations will have been baked into the pose already
                bone.render(matrixStack, vertexConsumer, geometry, metadata.light, offset)
            }

            modelState.reset(bones)
        }

        queue.submit(textureLocation, translucent, false) { vertexConsumer ->
            render(vertexConsumer)
        }

        val emissiveTexture = emissiveTexture
        if (emissiveTexture != null) {
            queue.submit(emissiveTexture, translucent, true) { vertexConsumer ->
                render(vertexConsumer)
            }
        }
    }

    class Offset(
        val pivotX: Float,
        val pivotY: Float,
        val pivotZ: Float,
        val offsetX: Float,
        val offsetY: Float,
        val offsetZ: Float
    )

    companion object {
        private val BASE = Offset(0f, -24f, 0f, 0f, 0f, 0f)
        private val RIGHT_ARM = Offset(-5f, -22f, 0f, 5f, 2f, 0f)
        private val LEFT_ARM = Offset(5f, -22f, 0f, -5f, 2f, 0f)
        private val LEFT_LEG = Offset(1.9f, -12f, 0f, -1.9f, 12f, 0f)
        private val RIGHT_LEG = Offset(-1.9f, -12f, 0f, 1.9f, 12f, 0f)
        private val CAPE = Offset(0f, -24f, 2f, 0f, 0f, -2f)
        private val ZERO = Offset(0f, 0f, 0f, 0f, 0f, 0f)
        val OFFSETS =
            mapOf(
                EnumPart.ROOT to ZERO,
                EnumPart.HEAD to BASE,
                EnumPart.BODY to BASE,
                EnumPart.LEFT_ARM to LEFT_ARM,
                EnumPart.RIGHT_ARM to RIGHT_ARM,
                EnumPart.LEFT_LEG to LEFT_LEG,
                EnumPart.RIGHT_LEG to RIGHT_LEG,
                EnumPart.LEFT_SHOULDER_ENTITY to BASE,
                EnumPart.RIGHT_SHOULDER_ENTITY to BASE,
                EnumPart.LEFT_WING to Offset(5f, -24f, 2f, -5f, 0f, -2f),
                EnumPart.RIGHT_WING to Offset(-5f, -24f, 2f, 5f, 0f, -2f),
                EnumPart.CAPE to CAPE,
            )
        const val TEXTURE_ANIMATION_FPS = 7f
    }
}
