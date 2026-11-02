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

import kotlin.jvm.JvmStatic
import gg.essential.model.ModelParser.Companion.ROOT_BONE_NAME
import java.util.Collections
import java.util.EnumSet

/** Different bones cosmetics can be bound to */
enum class EnumPart(

     /** represents the armor slot IDs that the part covers @see [ArmorSlots] */
    val armorSlotIds: Set<Int>,
) {
    // Represents the model root, also used to differentiate geometry or emitters that are otherwise disconnected from any other EnumParts
    ROOT(emptySet()),
    // Body parts
    HEAD(setOf(3)),
    BODY(setOf(2)),
    RIGHT_ARM(setOf(2, 4)),
    LEFT_ARM(setOf(2, 4)),
    LEFT_LEG(setOf(0, 1)),
    RIGHT_LEG(setOf(0, 1)),
    // Parrots
    RIGHT_SHOULDER_ENTITY(emptySet()),
    LEFT_SHOULDER_ENTITY(emptySet()),
    // Elytra
    RIGHT_WING(emptySet()),
    LEFT_WING(emptySet()),
    // Misc
    CAPE(emptySet()),
    ;

    companion object {
        private val SINGLE_ELEMENT_SETS: List<Set<EnumPart>> = entries.map { Collections.unmodifiableSet(EnumSet.of(it)) }

        @JvmStatic
        val entriesSet: Set<EnumPart> = Collections.unmodifiableSet(EnumSet.allOf(EnumPart::class.java))

        @JvmStatic
        fun singleElementSet(part: EnumPart): Set<EnumPart> {
            return SINGLE_ELEMENT_SETS[part.ordinal]
        }

        @JvmStatic
        fun fromBoneName(name: String): EnumPart? {
            // Don't use .toLowercase since it will create a ton more objects
            return when (name) {
                ROOT_BONE_NAME -> ROOT
                "rightArm", "arm_right", "right_arm", "RightArm",
                "__arm_right__" -> RIGHT_ARM
                "leftArm", "arm_left", "left_arm", "LeftArm",
                "__arm_left__" -> LEFT_ARM
                "body", "Body",
                "__body__" -> BODY
                "leftLeg", "leg_left", "left_leg", "LeftLeg",
                "__leg_left__"-> LEFT_LEG
                "rightLeg", "leg_right", "right_leg", "RightLeg",
                "__leg_right__"-> RIGHT_LEG
                "Head", "head",
                "__head__" -> HEAD
                "right_shoulder_entity",
                "__shoulder_entity_right__" -> RIGHT_SHOULDER_ENTITY
                "left_shoulder_entity",
                "__shoulder_entity_left__" -> LEFT_SHOULDER_ENTITY
                "right_wing",
                "__wing_right__" -> RIGHT_WING
                "left_wing",
                "__wing_left__" -> LEFT_WING
                "cape",
                "__cape__" -> CAPE
                else -> null
            }
        }
    }
}