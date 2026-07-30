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
package gg.essential.gui.wardrobe.configuration

import gg.essential.cosmetics.ImplicitOwnership
import gg.essential.cosmetics.ImplicitOwnershipId
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.WardrobeState
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.addAutoCompleteMenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.database.GitRepoCosmeticsDatabase.ImplicitOwnershipCriterion.Everyone
import gg.essential.mod.cosmetics.database.GitRepoCosmeticsDatabase.ImplicitOwnershipCriterion.OwnedCosmetic
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.universal.USound
import gg.essential.vigilance.utils.onLeftClick

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class ImplicitOwnershipConfiguration(
    state: WardrobeState,
) : AbstractConfiguration<ImplicitOwnershipId, ImplicitOwnership>(
    ConfigurationType.IMPLICIT_OWNERSHIPS,
    state
) {

    override fun LayoutScope.columnLayout(implicitOwnership: ImplicitOwnership) {
        val (_, cosmetics, criterion) = implicitOwnership
        for (cosmetic in cosmetics) {
            labeledRow("- $cosmetic") {
                box(Modifier.widthAspect(1f).height(10f)) {
                    icon(EssentialPalette.CANCEL_5X)
                }.onLeftClick {
                    USound.playButtonPress()
                    implicitOwnership.update(implicitOwnership.copy(cosmetics = cosmetics - cosmetic))
                }
            }
        }
        labeledRow("Add cosmetic:", Arrangement.spacedBy(10f, FloatPosition.END)) {
            val cosmeticState = mutableStateOf<Cosmetic?>(null)
            val input = essentialStateTextInput(
                cosmeticState,
                { it?.id ?: "" },
                { if (it.isBlank()) null else (cosmeticsDataWithChanges.getCosmetic(it) ?: throw StateTextInput.ParseException()) }
            )
            addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName })
            cosmeticState.onChange(stateScope) {
                val cosmeticId = (it ?: return@onChange).id
                implicitOwnership.update(implicitOwnership.copy(cosmetics = cosmetics + cosmeticId))
            }
        }
        labeledRow("Criterion:") {
            val dropdown = EssentialDropDown(
                criterion,
                listStateOf(
                    EssentialDropDown.Option("Everyone", Everyone),
                    EssentialDropDown.Option("Owned Cosmetic", criterion as? OwnedCosmetic ?: OwnedCosmetic("")),
                )
            )
            dropdown.selectedOption.onChange(stateScope) {
                implicitOwnership.update(implicitOwnership.copy(criterion = it.value))
            }
            dropdown()
        }
        if (criterion is OwnedCosmetic) {
            labeledRow("Owned cosmetic:") {
                val cosmeticState = mutableStateOf(cosmeticsDataWithChanges.getCosmetic(criterion.cosmetic))
                val input = essentialStateTextInput(
                    cosmeticState,
                    { it?.id ?: "" },
                    { if (it.isBlank()) null else (cosmeticsDataWithChanges.getCosmetic(it) ?: throw StateTextInput.ParseException()) }
                )
                addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName })
                cosmeticState.onChange(stateScope) {
                    val cosmeticId = (it ?: return@onChange).id
                    implicitOwnership.update(implicitOwnership.copy(criterion = criterion.copy(cosmetic = cosmeticId)))
                }
            }
        }
    }

}
