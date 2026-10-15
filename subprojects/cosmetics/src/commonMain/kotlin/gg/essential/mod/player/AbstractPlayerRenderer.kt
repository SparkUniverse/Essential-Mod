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
package gg.essential.mod.player

import gg.essential.cosmetics.CosmeticsState
import gg.essential.cosmetics.EquippedCosmetic
import gg.essential.cosmetics.WearablesManager
import gg.essential.cosmetics.events.AnimationEventType
import gg.essential.mod.Model
import gg.essential.mod.cosmetics.CAPE_DISABLED_COSMETIC_ID
import gg.essential.mod.cosmetics.CapeModel
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.CosmeticsSubject
import gg.essential.mod.cosmetics.PlayerModel
import gg.essential.model.BedrockModel
import gg.essential.model.EnumPart
import gg.essential.model.ModelAnimationState
import gg.essential.model.ModelInstance
import gg.essential.model.ParticleSystem
import gg.essential.model.RenderMetadata
import gg.essential.model.Vector3
import gg.essential.model.backend.PlayerPose
import gg.essential.model.backend.RenderBackend
import gg.essential.model.bones.BakedAnimations
import gg.essential.model.collision.PlaneCollisionProvider
import gg.essential.model.light.Light
import gg.essential.model.light.LightProvider
import gg.essential.model.molang.MolangQueryEntity
import gg.essential.model.util.PlayerPoseManager
import gg.essential.model.util.UMatrixStack
import gg.essential.network.cosmetics.Cosmetic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class) // TODO: can be removed when we migrate to Kotlin 2.4
abstract class AbstractPlayerRenderer(
    renderBackend: RenderBackend,
    private val scope: CoroutineScope,
) : AutoCloseable {
    protected var cosmetics: Map<CosmeticSlot, EquippedCosmetic> = emptyMap()
        private set

    private var currentOutfitUpdate: Job? = null

    protected val entity: MolangQueryEntityImpl = MolangQueryEntityImpl(0f, 0f, 0f, null)

    protected abstract val light: Light

    private var subject = CosmeticsSubject(entity)

    private var playerModel: ModelInstance =
        ModelInstance(PlayerModel.steveBedrockModel, entity, subject.animationTargets, CosmeticsState.EMPTY) {}

    private val poseManager = PlayerPoseManager(entity)

    val particleSystem: ParticleSystem =
        ParticleSystem(Random(0), PlaneCollisionProvider.PlaneXZ, LightProvider.FullBright, ::playSound)

    val wearablesManager: WearablesManager =
        WearablesManager(renderBackend, entity, subject.animationTargets) { _, _ -> }

    protected abstract fun getCurrentSkin(): RenderBackend.Texture
    protected abstract fun getThirdPartyCape(): RenderBackend.Texture?
    protected abstract fun playSound(event: ModelAnimationState.SoundEvent)
    protected abstract fun loadModel(cosmetic: EquippedCosmetic, skinType: Model): Deferred<BedrockModel>

    protected open fun beforeRender() {}

    fun updateCosmetics(value: Map<CosmeticSlot, EquippedCosmetic>) {
        if (cosmetics == value) return
        cosmetics = value
        updateCosmeticsState()
    }

    private fun updateCosmeticsState() {
        currentOutfitUpdate?.cancel()
        currentOutfitUpdate = scope.launch {
            val subject = subject
            val cosmetics = cosmetics
            val bedrockModels =
                cosmetics
                    .values
                    .map { loadModel(it, subject.skinType) }
                    .map { it.await() }
                    .associateBy { it.cosmetic }

            val newState = CosmeticsState(
                skinType = subject.skinType,
                cosmetics = cosmetics,
                bedrockModels = bedrockModels,
                armor = subject.armor,
            )
            wearablesManager.updateState(newState)
        }
    }

    protected fun updateSkinType(skinType: Model) {
        if (skinType == subject.skinType) return

        subject = CosmeticsSubject(entity, skinType)
        val model = when (skinType) {
            Model.ALEX -> PlayerModel.alexBedrockModel
            Model.STEVE -> PlayerModel.steveBedrockModel
        }
        playerModel = ModelInstance(model, entity, subject.animationTargets, CosmeticsState.EMPTY) {}

        updateCosmeticsState()
    }

    fun update(dt: Float) {
        val newLifeTime = entity.lifeTime + dt.coerceAtMost(1f)

        val ticksOld = (entity.lifeTime * 20).toInt()
        val ticksNew = (newLifeTime * 20).toInt()
        repeat(ticksNew - ticksOld) {
            for (model in wearablesManager.models.values) {
                model.essentialAnimationSystem.processEvent(AnimationEventType.TICK)
            }
        }

        entity.lifeTime = newLifeTime
    }

    fun render(
        stack: UMatrixStack,
        playerQueue: RenderBackend.CommandQueue,
        cosmeticQueues: WearablesManager.CommandQueueProvider,
    ) {
        beforeRender()

        val skin = getCurrentSkin()

        val selectedCape = cosmetics[CosmeticSlot.CAPE]?.cosmetic
        val (cape, capeEmissive) = when {
            selectedCape?.id == CAPE_DISABLED_COSMETIC_ID -> Pair(null, null)
            selectedCape != null -> wearablesManager.models[selectedCape]?.model.let { Pair(it?.texture, it?.emissiveTexture) }
            else -> Pair(getThirdPartyCape(), null)
        }

        playerModel.essentialAnimationSystem.updateAnimationState() // processes arm swing
        wearablesManager.update()
        poseManager.update(wearablesManager)

        var pose = PlayerPose.neutral()

        pose = playerModel.computePose(pose)
        pose = poseManager.computePose(wearablesManager, pose)

        // Don't have any of these, so we'll move them far away so any events they spawn won't be visible.
        pose = pose.copy(
            rightShoulderEntity = PlayerPose.Part.MISSING,
            leftShoulderEntity = PlayerPose.Part.MISSING,
            rightWing = PlayerPose.Part.MISSING,
            leftWing = PlayerPose.Part.MISSING,
        )
        // Similar to above, if we don't have a cape, hide its part
        if (cape == null) {
            pose = pose.copy(cape = PlayerPose.Part.MISSING)
        }

        val renderMetadata =
            RenderMetadata(
                pose,
                skin,
                light,
                null,
                emptySet(),
                Vector3(),
                EnumPart.values().toSet(),
            )

        playerModel.render(stack, playerQueue, playerModel.model.defaultRenderGeometry, renderMetadata)
        if (cape != null) {
            renderCape(stack, cosmeticQueues, renderMetadata, selectedCape, cape, capeEmissive)
        }

        wearablesManager.render(stack, cosmeticQueues, light, pose, skin)

        wearablesManager.updateLocators(pose)
        wearablesManager.collectEvents { event ->
            when (event) {
                is ModelAnimationState.ParticleEvent -> particleSystem.spawn(event)
                is ModelAnimationState.SoundEvent -> playSound(event)
            }
        }

        particleSystem.update()
    }

    override fun close() {
        scope.cancel()
        entity.isValid = false
    }

    private fun renderCape(
        stack: UMatrixStack,
        queues: WearablesManager.CommandQueueProvider,
        renderMetadata: RenderMetadata,
        cape: Cosmetic?, // may be null in case of third-party capes
        texture: RenderBackend.Texture,
        emissiveTexture: RenderBackend.Texture?,
    ) {
        val queue = queues.forCosmetic(cape?.id ?: CAPE_DISABLED_COSMETIC_ID)
        val model = CapeModel.get(texture.height)
        model.texture = texture
        model.emissiveTexture = emissiveTexture
        model.render(stack, queue, model.defaultRenderGeometry, BakedAnimations.EMPTY, renderMetadata, entity.lifeTime)
        model.texture = null
        model.emissiveTexture = null
    }

    class MolangQueryEntityImpl(
        override var lifeTime: Float,
        override var modifiedDistanceMoved: Float,
        override var modifiedMoveSpeed: Float,
        override var uuid: Uuid?,
    ) : MolangQueryEntity, ParticleSystem.Locator by ParticleSystem.Locator.Zero {
        override val locator: ParticleSystem.Locator = this
        override var isValid: Boolean = true
    }
}
