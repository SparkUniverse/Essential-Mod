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
package gg.essential.gui.overlay

sealed interface LayerPriority {
    /**
     * Layer which is logically positioned below the currently active screen.
     *
     * Note that this does not yet have dedicated input events and will instead use the same events as
     * [AboveScreenContent] (i.e. it will handle events before the screen and can cancel them). This is not intentional
     * and will change in the future if someone has an actual need for it.
     */
    data object BelowScreen : LayerPriority

    /**
     * Layer which is logically positioned below most of the content of the given screen.
     *
     * Note that this does not yet have dedicated input events and will instead use the same events as
     * [AboveScreenContent] (i.e. it will handle events before the screen and can cancel them). This is not intentional
     * and will change in the future if someone has an actual need for it.
     */
    data class BelowScreenContent(val screen: Any) : LayerPriority

    /**
     * Layer which is logically positioned right above most of the content of the given screen.
     * Note that some special screen elements may still be drawn above this layer, especially modded elements. This is
     * intentional because this layer is meant for adding custom elements to the screen, not over the screen (e.g.
     * modded modals and tooltips should be displayed on top of this layer). If this is not desirable, you should create
     * a new layer with [AboveScreen] or higher.
     */
    data class AboveScreenContent(val screen: Any) : LayerPriority

    /**
     * Layer which is logically positioned well above the screen. Content in here should still relate to the screen but
     * will generally get higher priority than anything else in the screen, including modded content.
     */
    data object AboveScreen : LayerPriority

    /**
     * Layer for modals which do not care about the specific screen which is currently active. They go on top of
     * everything related to the active screen.
     */
    data object Modal : LayerPriority

    /**
     * Layer for notifications, which should generally always be visible regardless of what else is on screen.
     */
    data object Notifications : LayerPriority

    /**
     * The highest possible layer priority.
     * If you are considering creating a layer on this priority, consider creating a new explicit priority type instead.
     */
    data object Highest : LayerPriority
}