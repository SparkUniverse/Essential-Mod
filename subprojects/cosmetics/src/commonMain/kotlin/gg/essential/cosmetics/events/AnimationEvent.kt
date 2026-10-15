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
package gg.essential.cosmetics.events

import gg.essential.model.BedrockModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimationEvent(
    val type: AnimationEventType? = null,
    val target: AnimationTarget? = null,
    val name: String,
    @SerialName("on_complete")
    val onComplete: AnimationEvent? = null,
    @SerialName("trigger_in_other_cosmetic")
    val triggerInOtherCosmetic: Set<String> = setOf(), // strings only as they are references to another already setup animation event in a different cosmetic
    val probability: Float = 1f,
    val skips: Int = 0,
    val loops: Int = 0,
    val priority: Int = 0,
) {
    /** Returns [loops], or [Int.MAX_VALUE] when looping indefinitely. */
    val loopsOrMaxInt: Int
        get() = if (loops == 0) Int.MAX_VALUE else loops

    fun getTotalTime(model: BedrockModel, whenLooping: Float = Float.POSITIVE_INFINITY): Float {
        return if (loops == 0) {
            whenLooping
        } else {
            val animation = model.getAnimationByName(name) ?: return 0f
            animation.animationLength * loops + (onComplete?.getTotalTime(model, whenLooping) ?: 0f)
        }
    }
}