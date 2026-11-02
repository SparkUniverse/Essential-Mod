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

import gg.essential.cosmetics.boxmask.ModelClipperImpl
import gg.essential.cosmetics.skinmask.SkinMask
import gg.essential.mod.Model
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticSetting
import gg.essential.mod.cosmetics.settings.side
import gg.essential.model.BedrockModel
import gg.essential.model.Box3
import gg.essential.model.EnumPart
import gg.essential.model.RenderGeometry
import gg.essential.model.Side
import gg.essential.model.Vector3
import gg.essential.model.backend.PlayerPose
import gg.essential.model.bones.BakedAnimations
import gg.essential.model.bones.BedrockModelState
import gg.essential.network.cosmetics.Cosmetic
import kotlin.jvm.JvmField

/**
 * Immutable container for all cosmetics state belonging to one entity (usually a player).
 *
 * Also contains various state derived for this specific combination of cosmetics (e.g. which skin layers are covered,
 * parts of cosmetics hidden by other cosmetics, etc.).
 *
 * All this information is immutable and computed purely based on the state passed via the constructor (which should
 * also be immutable).
 * When any of the underlying state needs to be changed (e.g. because we received an update to their equipped
 * cosmetics), a new instance is to be created. This guarantees that none of the computed state can ever be out of sync
 * with the state passed via the constructor, or put another way, any change to the cosmetics by necessity also always
 * updates everything that depends on it.
 */
class CosmeticsState(
    /**
     * The type of model in use for this player.
     */
    val skinType: Model,

    /**
     * Cosmetics which this player has equipped.
     */
    val cosmetics: Map<CosmeticSlot, EquippedCosmetic>,

    /**
     * Model instances. Should contain one entry for each equipped cosmetic.
     */
    val bedrockModels: Map<Cosmetic, BedrockModel>,

    /**
     * All armor slot IDs which currently have armor equipped. Cosmetics which conflict with any of these will not be
     * rendered.
     */
    val armor: ArmorSlots,
) {
    /**
     * For each cosmetic, contains a set of body parts on which it should not be rendered because another equipped
     * cosmetic has explicitly disabled it to avoid conflict (e.g. hats tend to disable the head part of full body
     * suits).
     */
    private val partsHiddenDueToProperty: Map<CosmeticId, Set<EnumPart>> = cosmetics.values
        .flatMap { it.cosmetic.properties.filterIsInstance<CosmeticProperty.CosmeticBoneHiding>() }
        .groupByPropertyTargetId { setting ->
            val data = setting.data
            buildSet {
                if (data.head) { add(EnumPart.HEAD) }
                if (data.body) { add(EnumPart.BODY) }
                if (data.arms) { add(EnumPart.LEFT_ARM); add(EnumPart.RIGHT_ARM) }
                if (data.legs) { add(EnumPart.LEFT_LEG); add(EnumPart.RIGHT_LEG) }
            }
        }


    private val hiddenPropertyImmuneCosmetics: MutableList<CosmeticId> = mutableListOf()
    /**
     * Fold all instances of CosmeticProperty.HidesCosmeticsOrItems.Data
     */
    private val hiddenPropertySettingsCombined: CosmeticProperty.HidesAllOtherCosmeticsOrItems.Data = cosmetics.values
        .fold(CosmeticProperty.HidesAllOtherCosmeticsOrItems.Data()) { acc, equipped ->
            val property = equipped.cosmetic.property<CosmeticProperty.HidesAllOtherCosmeticsOrItems>()
                ?.data
                ?: return@fold acc

            if (property.hidesAnyCosmetics()) hiddenPropertyImmuneCosmetics.add(equipped.cosmetic.id)

            acc.copy(
                hideAllCosmetics = acc.hideAllCosmetics || property.hideAllCosmetics,
                hideHeadCosmetics = acc.hideHeadCosmetics || property.hideHeadCosmetics,
                hideBodyCosmetics = acc.hideBodyCosmetics || property.hideBodyCosmetics,
                hideArmCosmetics = acc.hideArmCosmetics || property.hideArmCosmetics,
                hideLegCosmetics = acc.hideLegCosmetics || property.hideLegCosmetics,
                hideItems = acc.hideItems || property.hideItems,
        )
    }

    /**
     * Check if the property has been set to hide cosmetics (typical use is for emotes e.g. creeper emote).
     * This creates a map of all cosmetic id's other than the one setting this property to the parts that should be hidden.
     */
    private val partsHiddenByHidingProperty: Map<CosmeticId, Set<EnumPart>> = if (hiddenPropertyImmuneCosmetics.isEmpty()) mapOf() else
        hiddenPropertySettingsCombined.let { property ->
            val partsToHide: Set<EnumPart>
            if (property.hideAllCosmetics) {
                // notably EnumPart.ROOT, added here, is essential for use with the propertyHidesEntireCosmetic() function
                partsToHide = EnumPart.entriesSet
            } else {
                partsToHide = mutableSetOf()
                if (property.hideHeadCosmetics) partsToHide.add(EnumPart.HEAD)
                if (property.hideBodyCosmetics) partsToHide.add(EnumPart.BODY)
                if (property.hideArmCosmetics) { partsToHide.add(EnumPart.LEFT_ARM); partsToHide.add(EnumPart.RIGHT_ARM) }
                if (property.hideLegCosmetics) { partsToHide.add(EnumPart.LEFT_LEG); partsToHide.add(EnumPart.RIGHT_LEG) }
            }
            return@let cosmetics.values
                .filter { it.cosmetic.id !in hiddenPropertyImmuneCosmetics }
                .associate { it.cosmetic.id to partsToHide }
        }

    /**
     * same as above but only for the hideHeldItems flag
     */
    val hidesHeldItems: Boolean = hiddenPropertySettingsCombined.hideItems

    /**
     * Check if this cosmetic has been set to hide everything
     * This is used to set visibility of particles and sounds that are not bound to any parts of the model
     */
    fun propertyHidesEntireCosmetic(cosmeticId: CosmeticId): Boolean {
        return partsHiddenByHidingProperty[cosmeticId].let { it != null && EnumPart.ROOT in it }
    }

    /**
     * Whether any of the cosmetics are set to lock the player's rotation.
     * Typically, this would be an emote.
     */
    val locksPlayerRotation : Boolean = cosmetics.values.any {
        it.cosmetic.property<CosmeticProperty.LocksPlayerRotation>()?.data?.rotationLock ?: false}

    /**
     * For each cosmetic, contains a set of body parts on which it should not be rendered because they player has armor
     * equipped in a slot which would conflict with those parts (we generally want the armor to be clearly visible as to
     * not give any advantage in PvP scenarios).
     */
    private val partsHiddenDueToArmor: Map<CosmeticId, Set<EnumPart>> = cosmetics.values
        .flatMap {
            // Ignore old property if newer v2 is present
            if (it.cosmetic.property<CosmeticProperty.ArmorHandlingV2>() == null) {
                it.cosmetic.properties.filterIsInstance<CosmeticProperty.ArmorHandling>()
            } else {
                emptyList()
            }
        }
        .groupByPropertyTargetId { property ->
            val data = property.data
            buildSet {
                if (data.head) { add(EnumPart.HEAD) }
                if (data.body) { add(EnumPart.BODY) }
                if (data.arms) { add(EnumPart.LEFT_ARM); add(EnumPart.RIGHT_ARM) }
                if (data.legs) { add(EnumPart.LEFT_LEG); add(EnumPart.RIGHT_LEG) }
            }.filter { part -> part.armorSlotIds.any { armor[it] } }

        }

    /**
     * For each cosmetic, contains a set of body parts on which it should not be rendered because of one of the above
     * reasons.
     */
    val hiddenParts: Map<CosmeticId, Set<EnumPart>> =
        (partsHiddenDueToProperty.asSequence() + partsHiddenDueToArmor.asSequence() + partsHiddenByHidingProperty.asSequence())
                .groupBy({ it.key }) { it.value }
                .mapValues { it.value.flatten().toSet() }


    /**
     * For each cosmetic, contains a set of bone ids which should not be rendered because another cosmetic has
     * explicitly them to avoid conflicts.
     * This is similar to [partsHiddenDueToProperty] except that it targets specific bones, rather than whole body parts.
     * E.g. the backpacks remove the built-in backpack of the space suit
     */
    private val hiddenBonesDueToOtherCosmetics: Map<CosmeticId, Set<BoneId>> = cosmetics.values
        .flatMap { it.cosmetic.properties.filterIsInstance<CosmeticProperty.ExternalHiddenBone>() }
        .groupByPropertyTargetId { it.data.hiddenBones }

    private val hiddenBonesDueArmor: Map<CosmeticId, Set<BoneId>> = cosmetics.values
        .flatMap { it.cosmetic.properties.filterIsInstance<CosmeticProperty.ArmorHandlingV2>() }
        .groupByPropertyTargetId { prop -> prop.data.conflicts.mapNotNull { (boneId, slots) -> if (slots.any { armor[it] }) boneId else null } }

    val hiddenBones: Map<CosmeticId, Set<BoneId>> =
        (hiddenBonesDueToOtherCosmetics.asSequence() + hiddenBonesDueArmor.asSequence())
            .groupBy({ it.key }) { it.value }
            .mapValues { it.value.flatten().toSet() }

    private val hiddenSpecialBones: Map<CosmeticId, Set<EnumPart>> =
        hiddenBones.mapValues { (_, bones) -> bones.mapNotNullTo(mutableSetOf()) { EnumPart.fromBoneName(it) } }

    /**
     * For each cosmetic, contains the user-configured positional offset (e.g. glasses can be adjusted to your skin).
     */
    val positionAdjustments: Map<CosmeticId, Vector3> = cosmetics.values.mapNotNull { cosmetic ->
        cosmetic.setting<CosmeticSetting.PlayerPositionAdjustment>()
            ?.data?.let {
                cosmetic.id to Vector3(it.x, it.y, it.z)
            }
    }.toMap()

    /**
     * For each cosmetic, contains the side on which it should show. Most asymmetrical cosmetics allow
     * the user to flip them to the other side to match their preference.
     * If the user has not configured a side, a default side is chosen automatically.
     */
    val sides: Map<CosmeticId, Side> = cosmetics.values.mapNotNull { cosmetic ->
        val side = cosmetic.settings.side
            ?: cosmetic.cosmetic.defaultSide
            ?: bedrockModels[cosmetic.cosmetic]?.let { Side.getDefaultSideOrNull(it.sideOptions) }
        side?.let { cosmetic.id to it }
    }.toMap()

    /**
     * [RenderGeometry] for each cosmetic with exclusions applied.
     */
    val renderGeometries: Map<CosmeticId, RenderGeometry> = bedrockModels.values.associate { model ->
        val cosmetic = model.cosmetic
        val slot = cosmetic.slot
        val renderExclusions = bedrockModels.filter { (otherCosmetic, _) ->
            val otherSlot = otherCosmetic.slot
            exclusionsAffectSlots[otherSlot]?.contains(slot) ?: false
        }.flatMap { getSidedRenderExclusions(it.value) }
        val modelClipper = ModelClipperImpl()
        model.cosmetic.id to modelClipper.compute(model.defaultRenderGeometry, renderExclusions)
    }

    /**
     * Cosmetics may provide masks (black-white image files) to be applied to the player's skin while equipped, so the
     * skin doesn't render on top of the cosmetic.
     * This contains the final merged and offset mask to be applied directly to the skin.
     */
    val skinMask: SkinMask = bedrockModels.mapNotNull { (cosmetic, model) ->
        val settings = cosmetics[cosmetic.slot]?.settings ?: emptyList()
        val side = settings.side
            ?: cosmetic.defaultSide
            ?: Side.getDefaultSideOrNull(model.sideOptions)

        var mask = model.skinMasks[side] ?: model.skinMasks[null] ?: return@mapNotNull null

        val hiddenParts = hiddenParts[cosmetic.id] ?: emptyList()
        if (hiddenParts.any { it in mask.parts }) {
            mask = SkinMask(mask.parts.filterKeys { it !in hiddenParts })
        }
        val hiddenSpecialBones = hiddenSpecialBones[cosmetic.id] ?: emptySet()
        if (hiddenSpecialBones.any { it in mask.parts }) {
            mask = SkinMask(mask.parts.filterKeys { it !in hiddenSpecialBones })
        }

        val positionAdjustment = positionAdjustments[cosmetic.id]?.takeUnless { it == Vector3() }
        if (positionAdjustment != null) {
            mask = mask.offset(-positionAdjustment.x.toInt(), -positionAdjustment.y.toInt(), -positionAdjustment.z.toInt())
        }

        mask
    }.let { masks -> SkinMask.merge(masks) }

    /**
     * Set of armor slot ids that currently have cosmetics occupying
     */
    val partsEquipped: Set<Int> = bedrockModels.flatMap { (cosmetic, model) ->
        val renderGeometry = renderGeometries.getValue(model.cosmetic.id)
        val modelState = BedrockModelState(
            PlayerPose.neutral(),
            BakedAnimations.EMPTY,
            Vector3.ZERO,
            sides[model.cosmetic.id],
            hiddenBones[model.cosmetic.id] ?: emptySet(),
            EnumPart.entriesSet,
        )
        modelState.apply(model.bones)
        val boneToSlots = cosmetic.property<CosmeticProperty.ArmorHandlingV2>()?.data?.conflicts
        val slots = if (boneToSlots != null) {
            boneToSlots.asSequence()
                .filter { model.bones[it.key]?.containsVisibleBoxes(renderGeometry) ?: false }
                .flatMap { it.value }
        } else {
            model.bones.byPart
                .asSequence()
                .filter { it.value.containsVisibleBoxes(renderGeometry) }
                .flatMap {
                    it.key.armorSlotIds
                }
        }
        modelState.reset(model.bones)
        slots
    }.toSet()

    val usesCapePose: Boolean = bedrockModels.values.any { EnumPart.CAPE in it.bones.byPart }
    val usesElytraPose: Boolean = bedrockModels.values.any { EnumPart.LEFT_WING in it.bones.byPart || EnumPart.RIGHT_WING in it.bones.byPart }

    fun getPositionAdjustment(cosmetic: Cosmetic) = positionAdjustments[cosmetic.id] ?: Vector3()

    /**
     * Calls [valueSelector] on each setting to extract the relevant data and then groups all these values by the
     * target of the setting which they were extracted from.
     */
    @Suppress("DEPRECATION")
    private fun <T, E> List<E>.groupByPropertyTargetId(valueSelector: (E) -> Iterable<T>): Map<CosmeticId, Set<T>>
            where E: CosmeticProperty,
                  E: CosmeticProperty.UsesId =
        this
            .flatMap { setting -> valueSelector(setting).map { setting.id!! to it } }
            .groupBy({ it.first }) { it.second }
            .mapValues { it.value.toSet() }

    private fun getSidedRenderExclusions(model: BedrockModel): Collection<Box3> {
        return model.boundingBoxes.let { map ->
            val side = sides[model.cosmetic.id]
            if (side != null) {
                map.filter { it.second == null || it.second == side }
            } else {
                map
            }
        }.map { it.first }
    }

    fun copyWithout(slot: CosmeticSlot): CosmeticsState {
        val cosmetics = cosmetics.filterKeys { it != slot }
        val bedrockModels = bedrockModels.filterKeys { it.slot != slot }
        return CosmeticsState(skinType, cosmetics, bedrockModels, armor)
    }

    companion object {

        private val exclusionsAffectSlots = mapOf(
            CosmeticSlot.SHOES to setOf(CosmeticSlot.FULL_BODY, CosmeticSlot.PANTS),
            CosmeticSlot.TOP to setOf(CosmeticSlot.PANTS),
            CosmeticSlot.ARMS to setOf(CosmeticSlot.FULL_BODY, CosmeticSlot.TOP),
            CosmeticSlot.FULL_BODY to setOf(CosmeticSlot.TOP, CosmeticSlot.PANTS),
        )

        @JvmField
        val EMPTY = CosmeticsState(Model.STEVE, emptyMap(), emptyMap(), ArmorSlots(0))
    }
}
