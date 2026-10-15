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
package gg.essential.mod.vigilance2.builder

import gg.essential.gui.elementa.state.v2.State

/**
 * Used in [gg.essential.config.GuiBuilder.PropertyBuilderImpl.build] to expose a [State] indicating whether a property
 * is visible or not to the [gg.essential.gui.vigilancev2.components.settingsCategory] component.
 *
 * The links field is unrelated and passed along as a hack to avoid changing the vigilance api.
 */
class VisibleDependencyPredicate(val visible: State<Boolean>, val links: List<Pair<String, () -> Unit>>) : (Any?) -> Boolean {
    override fun invoke(any: Any?): Boolean {
        return visible.getUntracked()
    }
}
