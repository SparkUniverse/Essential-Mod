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
package gg.essential.cosmetics

import gg.essential.cosmetics.events.AnimationTarget
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.model.EnumPart
import gg.essential.model.ModelAnimationState
import gg.essential.model.ModelInstance
import gg.essential.model.RenderMetadata
import gg.essential.model.backend.PlayerPose
import gg.essential.model.backend.RenderBackend
import gg.essential.model.backend.atlas.TextureAtlas
import gg.essential.model.light.Light
import gg.essential.model.molang.MolangQueryEntity
import gg.essential.model.util.UMatrixStack
import gg.essential.model.util.UVertexConsumer
import gg.essential.network.cosmetics.Cosmetic

class WearablesManager(
    private val renderBackend: RenderBackend,
    private val entity: MolangQueryEntity,
    private val animationTargets: Set<AnimationTarget>,
    private val onAnimation: (Cosmetic, String) -> Unit,
) {
    var state: CosmeticsState = CosmeticsState.EMPTY
        private set

    var models: Map<Cosmetic, ModelInstance> = emptyMap()
        private set

    private var translucentTextureAtlas: TextureAtlas? = null

    fun updateState(newState: CosmeticsState) {
        val oldModels = models
        val oldTextures = oldModels.values.filter { it.model.translucent }.mapNotNull { it.model.texture }.distinct()

        val newModels =
            newState.bedrockModels
                .map { (cosmetic, bedrockModel) ->
                    val wearable = oldModels[cosmetic]
                    if (wearable == null) {
                        ModelInstance(bedrockModel, entity, animationTargets, newState) { onAnimation(cosmetic, it) }
                    } else {
                        wearable.switchModel(bedrockModel, newState)
                        wearable
                    }
                }
                .sortedBy { it.model.translucent } // render opaque models first
                .associateBy { it.cosmetic }

        // If there's more than one translucent model, we need to render them all in a single (sorted) pass
        val newTextures = newModels.values.filter { it.model.translucent }.mapNotNull { it.model.texture }.distinct()
        if (oldTextures != newTextures) {
            translucentTextureAtlas?.close()
            translucentTextureAtlas = null
        }
        if (translucentTextureAtlas == null && newTextures.size > 1) {
            translucentTextureAtlas = TextureAtlas.create(renderBackend, "cosmetics-${atlasCounter++}", newTextures)
        }

        for ((cosmetic, model) in models.entries) {
            if (newModels[cosmetic] != model) {
                model.locator.isValid = false
            }
        }
        state = newState
        models = newModels
    }

    fun resetModel(slot: CosmeticSlot) {
        updateState(state.copyWithout(slot))
    }

    private var lastUpdateTime = Float.NEGATIVE_INFINITY

    /**
     * Updates the state of the models for the current frame prior to rendering.
     *
     * Note that new animation events are emitted into [ModelAnimationState.pendingEvents] and the caller needs to
     * collect them from there and forward them to the actual particle/sound/etc system at the appropriate time.
     *
     * Also note that this does **not** yet update the locators bound to these model instances. For that one must call
     * [updateLocators] after rendering.
     * This is because the position and rotation of locators depends on the final rendered player pose, which is only
     * available after rendering.
     * Because particle events may depend on the position of locators, they should however ideally be updated before
     * particles are updated, rendered, and/or spawned.
     */
    fun update() {
        if (models.isEmpty()) return

        val now = entity.lifeTime
        if (lastUpdateTime == now) return // was already updated this frame
        lastUpdateTime = now

        val modelInstances = models.values

        // update animations
        modelInstances.forEach {
            it.essentialAnimationSystem.maybeFireTextureAnimationStartEvent()
            it.essentialAnimationSystem.updateAnimationState()
        }

        // trigger any animations that are supposed to be triggered in other models
        // run after all models have already updated without this interference
        modelInstances.forEach { it.essentialAnimationSystem.triggerPendingAnimationsForOtherModels(modelInstances) }

        // update effects after all animations have been updated
        modelInstances.forEach { it.animationState.updateEffects() }
    }

    /** @see ModelInstance.updateLocators */
    fun updateLocators(renderedPose: PlayerPose) {
        for ((_, model) in models) {
            model.updateLocators(renderedPose, state)
        }
    }

    fun render(
        matrixStack: UMatrixStack,
        queueProvider: CommandQueueProvider,
        light: Light,
        pose: PlayerPose,
        skin: RenderBackend.Texture,
        parts: Set<EnumPart> = EnumPart.entriesSet,
    ) {
        val atlas = translucentTextureAtlas
        for ((_, model) in models) {
            val queue = queueProvider.forCosmetic(model.cosmetic.id)
            val modelQueue = if (model.model.translucent && atlas != null) {
                object : RenderBackend.CommandQueue by queue {
                    override fun submit(texture: RenderBackend.Texture, translucent: Boolean, emissive: Boolean, render: (UVertexConsumer) -> Unit) {
                        queue.submit(atlas.atlasTexture, translucent, emissive) { vertexConsumer ->
                            render(atlas.offsetVertexConsumer(texture, vertexConsumer))
                        }
                    }
                }
            } else {
                queue
            }
            render(matrixStack, modelQueue, light, model, pose, skin, parts)
        }
    }

    fun render(
        matrixStack: UMatrixStack,
        queue: RenderBackend.CommandQueue,
        light: Light,
        model: ModelInstance,
        pose: PlayerPose,
        skin: RenderBackend.Texture,
        parts: Set<EnumPart> = EnumPart.entriesSet,
    ) {
        val cosmetic = model.cosmetic

        val renderMetadata = RenderMetadata(
            pose,
            skin,
            light,
            state.sides[cosmetic.id],
            state.hiddenBones[cosmetic.id] ?: emptySet(),
            state.getPositionAdjustment(cosmetic),
            parts - state.hiddenParts.getOrDefault(cosmetic.id, emptySet()),
        )
        model.render(matrixStack, queue, state.renderGeometries.getValue(cosmetic.id), renderMetadata)
    }

    fun collectEvents(consumer: (ModelAnimationState.Event) -> Unit) {
        for (model in models.values) {
            val pendingEvents = model.animationState.pendingEvents
            if (pendingEvents.isNotEmpty()) {
                for (event in pendingEvents) {
                    consumer(event)
                }
                pendingEvents.clear()
            }
        }
    }

    fun interface CommandQueueProvider {
        fun forCosmetic(cosmetic: CosmeticId): RenderBackend.CommandQueue
    }

    companion object {
        private var atlasCounter = 0
    }
}