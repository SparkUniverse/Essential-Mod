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
package gg.essential.cosmetics.state

import gg.essential.cosmetics.CosmeticId
import gg.essential.cosmetics.events.AnimationEvent
import gg.essential.cosmetics.events.AnimationEventType
import gg.essential.cosmetics.events.AnimationTarget
import gg.essential.mod.cosmetics.settings.CosmeticSetting
import gg.essential.model.BedrockModel
import gg.essential.model.ModelAnimationState
import gg.essential.model.ModelInstance
import gg.essential.model.molang.MolangQueryEntity
import kotlin.collections.HashMap
import kotlin.random.Random

class EssentialAnimationSystem(
    private val bedrockModel: BedrockModel,
    private val entity: MolangQueryEntity,
    private val animationState: ModelAnimationState,
    private val textureAnimationSync: TextureAnimationSync,
    private val animationTargets: Set<AnimationTarget>,
    private val animationVariantSetting: CosmeticSetting.AnimationVariant?,
    private val onAnimation: (String) -> Unit,
) {
    private val ongoingAnimations = mutableSetOf<AnimationEvent>()
    private val animationStates = AnimationEffectStates()

    private var lastActiveAnimationState: ModelAnimationState.AnimationState? = null
    private var lastActiveAnimationStateLoop = 0

    private val pendingAnimationsForOtherModels = mutableSetOf<String>()

    fun triggerPendingAnimationsForOtherModels(models : Collection<ModelInstance>) {
        if (pendingAnimationsForOtherModels.isEmpty()) return

        pendingAnimationsForOtherModels.forEach { pending ->
            models.forEach{ it.essentialAnimationSystem.fireTriggerFromAnimation(pending, AnimationEventType.BY_OTHER) }
        }
        pendingAnimationsForOtherModels.clear()
    }

    private class AnimationEffectStates {
        var skips = HashMap<AnimationEvent, Int>()
    }

    private var lastFrame = 0f

    init {
        processEvent(AnimationEventType.IDLE)
        processEvent(AnimationEventType.EQUIP)
        processEmoteEvent()
    }

    fun updateAnimationState() {
        val onComplete = mutableListOf<AnimationEvent>()
        ongoingAnimations.removeAll { ongoingAnimation: AnimationEvent ->
            for (animationState in animationState.active) {
                if (animationState.animation.name == ongoingAnimation.name && ongoingAnimation.loops > 0) {
                    val remove = animationState.animTime >= animationState.animation.animationLength * ongoingAnimation.loops
                    if (remove) {
                        this.animationState.updateEffects()
                    }
                    if (remove && ongoingAnimation.onComplete != null) {
                        onComplete.add(ongoingAnimation.onComplete)
                    }
                    return@removeAll remove
                }
            }
            false
        }
        ongoingAnimations.addAll(onComplete)

        val highestPriority = highestPriority
        animationState.active.removeAll { animationState: ModelAnimationState.AnimationState ->
            val animationByName = getAnimationByName(animationState.animation.name)
            animationByName == null || animationByName !== highestPriority
        }
        if (highestPriority != null) {
            val animation = bedrockModel.getAnimationByName(highestPriority.name)
            if (animation != null) {
                if (animationState.active.isEmpty()) {
                    animationState.startAnimation(animation, highestPriority.loopsOrMaxInt)
                }

                val state = animationState.active.find { it.animation == animation }
                if (state != null && (state != lastActiveAnimationState || state.loop != lastActiveAnimationStateLoop)) {
                    lastActiveAnimationState = state
                    lastActiveAnimationStateLoop = state.loop
                    pendingAnimationsForOtherModels.addAll(highestPriority.triggerInOtherCosmetic)
                }
            }
        }
    }

    private val highestPriority: AnimationEvent?
        get() = ongoingAnimations.maxByOrNull { obj: AnimationEvent -> obj.priority }

    private fun getAnimationByName(name: String): AnimationEvent? {
        for (ongoingAnimation in ongoingAnimations) {
            if (ongoingAnimation.name == name) {
                return ongoingAnimation
            }
        }
        return null
    }

    private fun processEmoteEvent(){
        // Emotes are a special case and should always play one event if present, this requires different probability logic for multiples
        // Priority is still considered and the final choice will be made only between events with the highest priority
        // This logic is set explicitly in EmoteWheel for regular players and is saved to the CosmeticSetting ANIMATION_VARIANT
        // Which is used here to ensure that different users see the same synced emote event variant on their end

        // Get explicit event set by cosmetic settings
        val event = animationVariantSetting?.data?.animationVariant
            ?.let { chosenEvent -> bedrockModel.animationEvents.firstOrNull { it.name == chosenEvent } }
            // Else, for fake UI players, get a random emote event using a local client-side map to prevent animation event repeats
            ?: getRandomEmoteAnimationEventOrNull(bedrockModel, lastAnimationVariantPlayedByFakeUIPlayers)
            ?: return


        // Process the event
        if (event.target != AnimationTarget.SELF) {
            onAnimation(event.name)
        }
        ongoingAnimations.add(event)
        updateAnimationState()
    }

    fun processEvent(type: AnimationEventType) {
        val animationEvents = bedrockModel.animationEvents
        val highestPriority = highestPriority
        val priority = highestPriority?.priority ?: 0
        var needsUpdate = false
        for (event in animationEvents) {
            if (priority > event.priority || event.type != type) continue
            if (event.target != AnimationTarget.ALL && event.target !in animationTargets) {
                continue
            }
            if (event.skips != 0) {
                val i = (animationStates.skips[event] ?: 0) + 1
                animationStates.skips[event] = i
                if (i % event.skips != 0) {
                    continue
                }
            }
            if (!handleProbability(event)) continue

            if (event.target != AnimationTarget.SELF) {
                onAnimation(event.name)
            }
            ongoingAnimations.add(event)
            needsUpdate = true
        }
        if (needsUpdate) {
            updateAnimationState()
        }
    }

    fun fireTriggerFromAnimation(animationName: String, requiredType : AnimationEventType? = null) {
        if (animationName == "texture_start") {
            textureAnimationSync.syncTextureStart()
            return
        }
        for (animationEvent in bedrockModel.animationEvents) {
            if (animationEvent.name == animationName && (requiredType == null || animationEvent.type == requiredType)) {
                ongoingAnimations.add(animationEvent)
                updateAnimationState()
                break
            }
        }
    }

    private fun handleProbability(event: AnimationEvent): Boolean {
        return event.probability > Random.nextDouble()
    }

    fun maybeFireTextureAnimationStartEvent() {
        val totalFrames = bedrockModel.textureFrameCount
        val frame: Int = (entity.lifeTime * BedrockModel.TEXTURE_ANIMATION_FPS).toInt()
        if (frame % totalFrames < lastFrame) {
            onAnimation("texture_start")
            processEvent(AnimationEventType.TEXTURE_ANIMATION_START)
        }
        lastFrame = (frame % totalFrames).toFloat()
    }

    companion object {
        // store the last animation variant played by fake UI players, read later to prevent repeats
        private val lastAnimationVariantPlayedByFakeUIPlayers: MutableMap<CosmeticId, /* AnimationEvent.name */ String> = mutableMapOf()

        // see processEmoteEvent() for more details
        fun getRandomEmoteAnimationEventOrNull(emote: BedrockModel, cacheOfLastVariant: MutableMap<CosmeticId, /* AnimationEvent.name */ String>): AnimationEvent? {
            val emoteEvents = emote.animationEvents.filter { it.type == AnimationEventType.EMOTE }

            if (emoteEvents.size < 2) return emoteEvents.firstOrNull() // only 1 or empty

            val highestPriority = emoteEvents.maxOfOrNull { it.priority }
            val emoteEventsPriority = emoteEvents.filter { it.priority == highestPriority }

            if (emoteEventsPriority.size < 2) return emoteEventsPriority.firstOrNull() // only 1 with highest priority

            // there are more than 1 event with the highest priority so we need to randomly select one

            // remove the last event variant that was played for this emote, no repeats
            val lastToSkip = cacheOfLastVariant[emote.cosmetic.id]
            val eventsMinusLast = emoteEventsPriority.filter { it.name != lastToSkip }

            var randomEvent: AnimationEvent? = null
            if (eventsMinusLast.size == 1) {
                // there were only 2 total before removing the last variant used
                randomEvent = eventsMinusLast.first()
            } else {
                // pick random event based on probability as weights
                var sum = 0f
                for (element in emoteEventsPriority) {
                    sum += element.probability
                }

                var target = Random.nextFloat() * sum
                for (event in eventsMinusLast) {
                    target -= event.probability
                    if (target <= 0) {
                        randomEvent = event
                        break
                    }
                }
            }

            // There are more than 1 event with the highest priority, so we need to track this to skip next time
            if (randomEvent != null) cacheOfLastVariant[emote.cosmetic.id] = randomEvent.name

            return randomEvent
        }
    }
}