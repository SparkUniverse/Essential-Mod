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

import gg.essential.cosmetics.CosmeticId
import gg.essential.cosmetics.CosmeticsState
import gg.essential.cosmetics.events.AnimationTarget
import gg.essential.cosmetics.state.EssentialAnimationSystem
import gg.essential.cosmetics.state.TextureAnimationSync
import gg.essential.cosmetics.state.WearableLocator
import gg.essential.mod.cosmetics.settings.CosmeticSetting
import gg.essential.mod.cosmetics.settings.setting
import gg.essential.model.backend.PlayerPose
import gg.essential.model.backend.RenderBackend
import gg.essential.model.bones.BedrockModelState
import gg.essential.model.molang.MolangQueryEntity
import gg.essential.model.util.UMatrixStack
import gg.essential.network.cosmetics.Cosmetic

class ModelInstance(
    var model: BedrockModel,
    val entity: MolangQueryEntity,
    val animationTargets: Set<AnimationTarget>,
    state: CosmeticsState,
    val onAnimation: (String) -> Unit,
) {
    var locator = WearableLocator(entity.locator, wearableVisible = !state.propertyHidesEntireCosmetic(model.cosmetic.id))
    // allows new particles to reflect modelInstance texture changes even when the animationState doesn't update
    private val textureSource = { this.model.texture }
    var animationState = ModelAnimationState(entity, locator, textureSource)
    var textureAnimationSync = TextureAnimationSync(model.textureFrameCount)
    private var animationVariantSetting: CosmeticSetting.AnimationVariant? = state.getAnimationVariantSettingOf(model.cosmetic.id)
    var essentialAnimationSystem = EssentialAnimationSystem(model, entity, animationState, textureAnimationSync, animationTargets, animationVariantSetting, onAnimation)


    private fun CosmeticsState.getAnimationVariantSettingOf(cosmeticId: CosmeticId): CosmeticSetting.AnimationVariant? {
        return cosmetics.values.firstOrNull { it.id == cosmeticId }?.settings?.setting<CosmeticSetting.AnimationVariant>()
    }

    fun switchModel(newModel: BedrockModel, newState: CosmeticsState) {
        locator.wearableVisible = !newState.propertyHidesEntireCosmetic(newModel.cosmetic.id)

        val newTextureAnimation = model.textureFrameCount != newModel.textureFrameCount
        val newAnimations = model.animations != newModel.animations || model.animationEvents != newModel.animationEvents

        val newAnimationVariantSetting = newState.getAnimationVariantSettingOf(newModel.cosmetic.id)
        val newAnimationVariant = animationVariantSetting != newAnimationVariantSetting

        model = newModel

        if (newTextureAnimation) {
            textureAnimationSync = TextureAnimationSync(model.textureFrameCount)
        }
        if (newAnimations) {
            locator.isValid = false
            locator = WearableLocator(entity.locator, locator.wearableVisible)
            animationState = ModelAnimationState(entity, locator, textureSource)
        }
        if (newAnimationVariant) {
            animationVariantSetting = newAnimationVariantSetting
        }

        if (newAnimations || newTextureAnimation || newAnimationVariant) {
            essentialAnimationSystem = EssentialAnimationSystem(model, entity, animationState, textureAnimationSync, animationTargets, animationVariantSetting, onAnimation)
        }
    }

    val cosmetic: Cosmetic
        get() = model.cosmetic

    fun computePose(basePose: PlayerPose): PlayerPose {
        return model.computePose(basePose, animationState)
    }

    /**
     * Updates all locators bound to this model instance.
     *
     * Note that this method must called for all models, even those that were not actually rendered (e.g. because
     * the corresponding player was frustum culled), so that particles bound to locators (which may be visible even
     * when the player entity that spawned them is not) are update correctly.
     */
    fun updateLocators(renderedPose: PlayerPose, state: CosmeticsState) {
        // Locators are fairly expensive to update, so only do it if we need to
        if (animationState.locatorsNeedUpdating()) {
            val modelState = BedrockModelState(
                renderedPose,
                animationState.bake(model.bones),
                state.getPositionAdjustment(cosmetic),
                state.sides[cosmetic.id],
                state.hiddenBones[cosmetic.id] ?: emptySet(),
                EnumPart.entriesSet - state.hiddenParts.getOrDefault(cosmetic.id, emptySet()),
            )
            modelState.apply(model.bones)

            animationState.updateLocators(model.bones, 1 / 16f)

            modelState.reset(model.bones)
        }
    }

    fun render(
        matrixStack: UMatrixStack,
        queue: RenderBackend.CommandQueue,
        geometry: RenderGeometry,
        renderMetadata: RenderMetadata,
    ) {
        val bakedAnimations = animationState.bake(model.bones)

        model.render(
            matrixStack,
            queue,
            geometry,
            bakedAnimations,
            renderMetadata,
            textureAnimationSync.getAdjustedLifetime(entity.lifeTime),
        )
    }
}
