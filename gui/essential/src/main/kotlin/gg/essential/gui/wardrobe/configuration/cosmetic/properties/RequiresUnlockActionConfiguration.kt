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

import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledListInputRow
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledStringInputRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticProperty.RequiresUnlockAction.Data
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

class RequiresUnlockActionConfiguration(
    cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    cosmetic: Cosmetic,
) : SingletonPropertyConfiguration<CosmeticProperty.RequiresUnlockAction>(
    CosmeticPropertyType.REQUIRES_UNLOCK_ACTION,
    CosmeticProperty.RequiresUnlockAction::class.java,
    cosmeticsDataWithChanges,
    cosmetic
) {

    override fun LayoutScope.layout(property: CosmeticProperty.RequiresUnlockAction) {
        val data = property.data

        labeledListInputRow(
            "Type:",
            data,
            stateOf(listOfNotNull<EssentialDropDown.Option<Data>>(
                EssentialDropDown.Option(
                    "Open Link",
                    if (data is Data.OpenLink) data else Data.OpenLink("", "", "")
                ),
                EssentialDropDown.Option(
                    "Join Server",
                    if (data is Data.JoinServer) data else Data.JoinServer("", "")
                ),
                EssentialDropDown.Option(
                    "Join SPS",
                    if (data is Data.JoinSps) data else Data.JoinSps("", "")
                ),
                null,
                null,
            )).toListState()
        ) {
            property.update(property.copy(data = it))
        }

        when (data) {
            is Data.OpenLink -> openLink(property, data)
            is Data.JoinServer -> joinServer(property, data)
            is Data.JoinSps -> joinSPS(property, data)
        }
    }

    private fun LayoutScope.openLink(property: CosmeticProperty.RequiresUnlockAction, data: Data.OpenLink) {
        labeledStringInputRow("Description", mutableStateOf(data.actionDescription)).state.onChange(stateScope) { property.update(property.copy(data = data.copy(actionDescription = it))) }
        labeledStringInputRow("Link", mutableStateOf(data.linkAddress)).state.onChange(stateScope) { property.update(property.copy(data = data.copy(linkAddress = it))) }
        labeledStringInputRow("Link Short", mutableStateOf(data.linkShort)).state.onChange(stateScope) { property.update(property.copy(data = data.copy(linkShort = it))) }

    }

    private fun LayoutScope.joinServer(property: CosmeticProperty.RequiresUnlockAction, data: Data.JoinServer) {
        labeledStringInputRow("Description", mutableStateOf(data.actionDescription)).state.onChange(stateScope) { property.update(property.copy(data = data.copy(actionDescription = it))) }
        labeledStringInputRow("Server Address", mutableStateOf(data.serverAddress)).state.onChange(stateScope) { property.update(property.copy(data = data.copy(serverAddress = it))) }

    }

    private fun LayoutScope.joinSPS(property: CosmeticProperty.RequiresUnlockAction, data: Data.JoinSps) {
        labeledStringInputRow("Description", mutableStateOf(data.actionDescription)).state.onChange(stateScope) { property.update(property.copy(data = data.copy(actionDescription = it))) }
        labeledStringInputRow(
            "Required Version",
            mutableStateOf(data.requiredVersion ?: "")
        ).state.onChange(stateScope) { property.update(property.copy(data = data.copy(requiredVersion = it.ifBlank { null }))) }
    }

}
