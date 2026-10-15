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
package gg.essential.network.connectionmanager

import gg.essential.event.gui.GuiOpenedEvent
import gg.essential.gui.InternalEssentialGUI
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.universal.UScreen
import gg.essential.util.EventBus
import gg.essential.util.isMainMenu
import net.minecraft.client.gui.GuiIngameMenu
import net.minecraft.client.gui.GuiScreen

class McGameStates : GameStates {
    private val currentAndNextScreen = mutableStateOf<Pair<GuiScreen?, Class<out GuiScreen>?>>(null to null)
    private val currentScreen = State { currentAndNextScreen().first }
    private val nextScreen = State { currentAndNextScreen().second }
    private val isChangingScreen = State { nextScreen() != null }

    fun register(eventBus: EventBus) {
        eventBus.register({ event: GuiOpenedEvent -> currentAndNextScreen.set(event.screen to null) })
        currentAndNextScreen.set(UScreen.currentScreen to null)
    }

    fun setNextScreen(nextScreen: Class<out GuiScreen>?) {
        currentAndNextScreen.set { it.first to nextScreen }
    }

    override val isTitleScreen: State<Boolean> = memo { currentScreen().isMainMenu }
    override val isPauseScreen: State<Boolean> = memo { currentScreen() is GuiIngameMenu }
    override val isEssentialScreen: State<Boolean> = memo { currentScreen() is InternalEssentialGUI }

    override val isScreenFineForUnpromptedModal: State<Boolean> = memo {
        (isTitleScreen() || isPauseScreen() || isEssentialScreen()) && !isChangingScreen()
    }
}