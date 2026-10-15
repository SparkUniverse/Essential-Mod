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

import gg.essential.serialization.SnakeAsUpperCaseSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmStatic

/*
    The enum declaration order is in the order in which the sides are determined to be default.
    The reasoning behind the order is that if we do have all 4 sides, we probably want "FRONT" as the default.
    If we do not have "FRONT", we probably want one of the sides, which keep "LEFT" as a default from before.
    If nothing else, we use "BACK".
*/
@Serializable
enum class Side(val displayName: String) {
    @SerialName("front")
    FRONT("Front"),
    @SerialName("left")
    LEFT("Left"),
    @SerialName("right")
    RIGHT("Right"),
    @SerialName("back")
    BACK("Back"),
    ;

    object UpperCase : SnakeAsUpperCaseSerializer<Side>(Side.serializer())

    companion object {

        /*
            The mod technically supports all 4 sides to be present, but in most cases sides will be left/right or front/back only.
            That is why we cannot have a single default side, we must pick the default based on the sides available.
            The order in which a default side is picked is the order of declaration on the sides in the enum, see the comment above.
        */
        @JvmStatic
        fun getDefaultSideOrNull(availableSides: Set<Side>): Side? {
            if (availableSides.isEmpty()) return null
            for (side in values()) {
                if (availableSides.contains(side)) return side
            }
            return null // Impossible to reach, but kotlin doesn't know that
        }
    }
}
