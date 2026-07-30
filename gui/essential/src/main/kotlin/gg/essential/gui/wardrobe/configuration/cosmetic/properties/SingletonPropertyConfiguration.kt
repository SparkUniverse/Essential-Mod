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

import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.essentialStateTextInput
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.wardrobe.configuration.AbstractConfiguration.AbstractConfigurationSubmenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.addAutoCompleteMenu
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.divider
import gg.essential.gui.wardrobe.configuration.ConfigurationUtils.labeledInputRow
import gg.essential.mod.cosmetics.settings.CosmeticProperty
import gg.essential.mod.cosmetics.settings.CosmeticPropertyType
import gg.essential.network.connectionmanager.cosmetics.*
import gg.essential.network.cosmetics.Cosmetic

abstract class SingletonPropertyConfiguration<P : CosmeticProperty>(
    type: CosmeticPropertyType,
    private val clazz: Class<P>,
    private val cosmeticsDataWithChanges: CosmeticsDataWithChanges,
    protected val cosmetic: Cosmetic,
) : AbstractConfigurationSubmenu<Cosmetic>("properties:${type.name}", type.displayName, cosmetic) {

    private val property = cosmetic.allProperties.firstNotNullOfOrNull { if (clazz.isInstance(it)) clazz.cast(it) else null }

    override fun LayoutScope.layout(modifier: Modifier) {
        box(Modifier.fillWidth().then(modifier)) {
            if (property != null) {
                column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
                    layout(property)
                    divider()
                    val input = labeledInputRow("Copy from:") {
                        essentialStateTextInput(
                            mutableStateOf(null),
                            { "" }, // Since we update when we get a valid result, we don't need this
                            { input ->
                                if (input.isBlank())
                                    null
                                else (cosmeticsDataWithChanges.getCosmetic(input)?.allProperties?.firstNotNullOfOrNull {
                                    if (clazz.isInstance(it)) clazz.cast(it) else null
                                } ?: throw StateTextInput.ParseException())
                            }
                        )
                    }
                    addAutoCompleteMenu(input, cosmeticsDataWithChanges.cosmetics.mapEach { it.id to it.displayName }.toListState())
                    input.state.onChange(stateScope) {
                        if (it != null) {
                            property.update(it)
                        }
                    }
                }
            } else {
                text("Property was not found?")
            }
        }
    }

    abstract fun LayoutScope.layout(property: P)

    protected fun CosmeticProperty.update(property: P) {
        var newProperty = property
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        if (newProperty is CosmeticProperty.IdShouldBeSelf && newProperty.id != cosmetic.id) {
            newProperty = when (val p = property as CosmeticProperty.IdShouldBeSelf) {
                is CosmeticProperty.ArmorHandling -> p.copy(id = cosmetic.id) as P
                is CosmeticProperty.ArmorHandlingV2 -> p.copy(id = cosmetic.id) as P
            }
        }
        cosmeticsDataWithChanges.updateCosmeticProperty(cosmetic.id, this, newProperty)
    }

}
