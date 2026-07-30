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
package gg.essential.gui.wardrobe.configuration.cosmetic.properties

import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.listStateOf
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.icon
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.addAutoCompleteMenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.AssetLoader
import gg.essential.network.connectionmanager.cosmetics.CosmeticsDataWithChanges
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick

class ArmorHandlingV2Configuration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.ArmorHandlingV2>(
    CosmeticPropertyType.ARMOR_HANDLING_V2,
    CosmeticProperty.ArmorHandlingV2::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.ArmorHandlingV2) {
        wrappedText("List of bones and armor slot id-s they conflict with.", Modifier.fillWidth())
        wrappedText("Input slots separated by commas, eg. 0,1,2", Modifier.fillWidth())
        text("0 - boots")
        text("1 - leggins")
        text("2 - chestplate")
        text("3 - helmet")
        text("Bones:")
        for ((id, slots) in property.data.conflicts) {
            labeledRow("$id:") {
                essentialStateTextInput(
                    mutableStateOf(slots),
                    { it.joinToString(",") },
                    { if (it.isEmpty()) emptyList() else it.split(",").map(String::toInt) },
                    Modifier.width(40f),
                ).state.onChange(stateScope) { newSlots ->
                    property.update(property.copy(data = property.data.copy(conflicts = property.data.conflicts + (id to newSlots))))
                }
                box(Modifier.width(10f).height(10f)) {
                    icon(EssentialPalette.CANCEL_5X)
                }.onLeftClick {
                    property.update(property.copy(data = property.data.copy(conflicts = property.data.conflicts - id)))
                }
            }
        }
        labeledRow("Add Bone ID:") {
            val model = platform.modelLoader.getModel(cosmetic, cosmetic.defaultVariantName, AssetLoader.Priority.Blocking).get()
            val remainingBones = model.bones.byName.keys - property.data.conflicts.keys
            val boneInput = essentialStateTextInput(
                mutableStateOf(""),
                { it },
                { if (it.isEmpty()) "" else if (remainingBones.contains(it)) it else throw StateTextInput.ParseException() },
                Modifier.width(100f),
            )
            boneInput.state.onChange(stateScope) { id ->
                property.update(property.copy(data = property.data.copy(conflicts = property.data.conflicts + (id to listOf()))))
            }
            addAutoCompleteMenu(boneInput, listStateOf(*remainingBones.map { it to it }.toTypedArray()))
        }
    }

}
