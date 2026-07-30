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
package gg.essential.gui.proxies

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.Window
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.overlay.Layer
import gg.essential.handlers.OptionsScreenOverlay
import gg.essential.handlers.PauseMenuDisplay
import gg.essential.mixins.transformers.client.gui.GuiScreenAccessor
import net.minecraft.client.gui.GuiScreen

class ScreenWithProxiesHandler(
    private val screen: GuiScreen,
    private val buttonIds: Map<String, Int>,
    private val flagIds: Map<String, Int>,
    private val playerIds: Map<String, Int>,
    private val initialLayout: (Window, ScreenWithProxiesHandler) -> Unit,
) {
    private val proxiesById = mutableMapOf<String, EssentialProxyElement<*>>()
    private val elementaById = mutableMapOf<String, Pair<UIComponent, MutableState<State<Boolean>>>>()
    private val access = screen as GuiScreenAccessor

    var layer: Layer? = null
    var lastWidth = -1 // TODO remove, see usages

    fun initGui() {
        val proxies = mutableListOf<EssentialProxyElement<*>>()
        buttonIds.mapTo(proxies) { MenuButtonProxy(it.key, it.value) }
        flagIds.mapTo(proxies) { NoticeFlagProxy(it.key, it.value) }
        playerIds.mapTo(proxies) { UIPlayerProxy(it.key, it.value) }

        // Note: We must not move the buttons when FancyMenu is doing their identification pass that expects
        // consistent x y positions from which it derives the button id
        // for values see fancy menu github:
        // v2 https://github.com/Keksuccino/FancyMenu/blob/4b85ac0906d8b1862312779d1efe5ec48b8dec31/src/main/java/de/keksuccino/fancymenu/menu/button/ButtonCache.java#L126
        // v3 https://github.com/Keksuccino/FancyMenu/blob/57564b4891ab83fc258377d03db8a857a39c91b8/common/src/main/java/de/keksuccino/fancymenu/customization/widget/ScreenWidgetDiscoverer.java#L42
        val isProbablyFancyMenuIdentifierPass = screen.width == 1000 && screen.height == 1000

        if (isProbablyFancyMenuIdentifierPass) {
            proxies.forEach(::addProxy)
            return
        }

        proxies.forEach { proxiesById[it.essentialId] = it }

        val dummyWindow = Window(ElementaVersion.V10)

        // Setup initial layout
        initialLayout(dummyWindow, this)
        // With all components in place, invalidate any constraints that might have been queried prematurely
        dummyWindow.invalidateCachedConstraints()
        // Apply layout to proxies
        proxies.forEach { it.initAfterInitialLayout() }

        // Once all proxies have been positioned, add them to the Minecraft screen
        // (we only add them after positioning them, in case a mod modifies the `addDrawableChild` method and expects
        //  correct positioning in there already, because vanilla buttons would already be positioned too)
        proxies.forEach { addProxy(it) }

        // If we have already set up the overlay previously, re-attach those Elementa components to the new proxies
        for ((id, elementa) in elementaById) {
            val (container, mounted) = elementa
            proxiesById[id]?.acceptNewEssentialContainer(container, mounted)
        }
    }

    private fun addProxy(proxy: EssentialProxyElement<*>) {
        //#if MC>=11700
        //$$ access.`essential$addDrawableChild`(proxy)
        //#else
        access.buttonList.add(proxy)
        //#endif
    }

    companion object {

        /** Main / Pause Menus */
        @JvmStatic
        fun forMainMenu(screen: GuiScreen) : ScreenWithProxiesHandler =
            ScreenWithProxiesHandler(screen, mainMenuButtons, mainMenuFlags, mainAndPauseMenuPlayers) { window, handler ->
                PauseMenuDisplay().initContent(screen, window, handler)
            }

        @JvmStatic
        fun forPauseMenu(screen: GuiScreen) : ScreenWithProxiesHandler =
            ScreenWithProxiesHandler(screen, pauseMenuButtons, pauseMenuFlags, mainAndPauseMenuPlayers) { window, handler ->
                PauseMenuDisplay().initContent(screen, window, handler)
            }

        @JvmStatic
        fun forOptionsMenu(screen: GuiScreen) : ScreenWithProxiesHandler =
            ScreenWithProxiesHandler(screen, optionsMenuButtons, emptyMap(), emptyMap()) { window, handler ->
                OptionsScreenOverlay().init(screen, window, handler)
            }

        fun LayoutScope.mountWithProxy(proxyHandler: ScreenWithProxiesHandler?, id: String, modifier: Modifier = Modifier, block: LayoutScope.() -> Unit) {
            val mounted = mutableStateOf(stateOf(true))
            lateinit var container: UIComponent
            if_({ mounted()() }) {
                // use box to create a container to hold the components and send to the proxies
                container = box(modifier) {
                    block() // may setup any components, even none or conditional ones
                }
            }
            if (proxyHandler != null) {
                val proxy = proxyHandler.proxiesById[id]
                    ?: throw IllegalArgumentException("No proxy found for `$id`. Did you forget to add it to the label-to-id mapping in `ScreenWithProxiesHandler`?")
                proxyHandler.elementaById[id] = Pair(container, mounted)
                proxy.acceptNewEssentialContainer(container, mounted)
            }
        }

        // menu components
        // the numbers are hardcoded as they are essential (heh) to allowing fancy menu to consistently identify the buttons
        // so we MUST ensure that the numbers remain the same even if elements are changed in future updates
        // if these were inconsistent (e.g. driven by index then entries changed) then exising fancy menu layouts would misidentify the buttons and break
        // while these are arbitrary they will also represent where our disabled/inactive buttons will appear in the fancy menu editor
        // at position [val * 2, val], and should remain small
        private val mainMenuButtons = listOfNotNull(
            null,
            "invite_host" to 2,
            "world_host" to 3,
            "social" to 4,
            "wardrobe" to 5,
            "wardrobe_2" to 6,
            "pictures" to 7,
            "settings" to 8,
            "account" to 9,
            null,
            null,
        ).toMap()
        private val mainMenuFlags = listOfNotNull(
            null,
            "beta" to 11,
            "update" to 12,
            "message" to 13,
        ).toMap()
        private val mainAndPauseMenuPlayers = mapOf(
            "player" to 14, // only 1 entry for now, but still use a map for consistency
        )

        private val pauseMenuButtons = listOfNotNull(
            "invite_host" to 2,
            "world_host" to 3,
            "social" to 4,
            "wardrobe" to 5,
            "wardrobe_2" to 6,
            "pictures" to 7,
            "settings" to 8,
            null,
        ).toMap()
        private val pauseMenuFlags = mapOf(
            "beta" to 11,
            "update" to 12,
            "message" to 13,
        )

        private val optionsMenuButtons = mapOf(
            "settings" to 1,
        )
    }
}
