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
package gg.essential.gui.screenshot.components

import gg.essential.Essential
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.components.UIText
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.utils.elementaDev
import gg.essential.gui.InternalEssentialGUI
import gg.essential.gui.common.bindParent
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.collections.trackedListOf
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.flatten
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.fillHeight
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.layout
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.screenshot.LocalScreenshot
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.components.ScreenshotProviderManager.Companion.minResolutionTargetResolution
import gg.essential.gui.util.pollingState
import gg.essential.gui.util.pollingStateV2
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMinecraft
import gg.essential.util.*
import java.nio.file.Path

class ScreenshotBrowser(editPath: Path? = null) : InternalEssentialGUI(
    ElementaVersion.V10,
    "Pictures",
    discordActivityDescription = "Browsing pictures",
) {

    val view = mutableStateOf<View>(View.List)
    val focusingV2 = State {
        when (val v = view()) {
            View.List -> null
            is View.Focus -> v.screenshot
            is View.Edit -> v.screenshot
        }
    }

    val screenshotManager = Essential.getInstance().connectionManager.screenshotManager
    val stateManager = ScreenshotStateManager(screenshotManager)

    private val previewItemsSource = mutableStateOf(stateOf(trackedListOf<ScreenshotId>()))
    private val previewItems = previewItemsSource.flatten()
    private val previewImageSize = mutableStateOf(minResolutionTargetResolution)
    private val focusImageSize = window.pollingStateV2(minResolutionTargetResolution) {
        val realWidth = (window.getWidth() * 0.57 * UMinecraft.guiScale).toInt()
        // Using a generous upper bound for the height, width is usually the limiting factor anyway
        val realHeight = (window.getHeight() * UMinecraft.guiScale).toInt()
        Pair(realWidth, realHeight)
    }
    val providerManager = ScreenshotProviderManager(screenshotManager, screenOpen, previewItems, previewImageSize, focusImageSize)

    val optionsDropdown = ScreenshotOptionsDropdown(screenshotManager, stateManager, view)

    val listViewComponent = ListViewComponent(screenshotManager, stateManager, providerManager, previewImageSize, rightDivider, optionsDropdown, view)
    val focusViewComponent = FocusViewComponent(screenshotManager, providerManager, stateManager, optionsDropdown, view)
    val editViewComponent = EditViewComponent(screenOpen, screenshotManager, providerManager, stateManager, view)

    init {
        titleText.bindParent(titleBar, listViewComponent.active)

        previewItemsSource.set(listViewComponent.listView.items.mapEach { it.id })

        effect(window) {
            backButtonVisible = view() == View.List
        }

        // Clear the default key handler and override so that hitting escape doesn't unconditionally close the UI
        window.keyTypedListeners.removeFirst()
        window.onKeyType { typedChar, keyCode ->
            if (keyCode == UKeyboard.KEY_ESCAPE && onEscapeButtonPressed()) {
                return@onKeyType
            }
            defaultKeyBehavior(typedChar, keyCode)
        }

        // Open the editor to a specific file if supplied
        if (editPath != null) {
            view.set { View.Edit(LocalScreenshot(editPath), it) }
        }

        // Return to list if the item currently being focused is no longer available
        var oldItems = emptyList<ScreenshotId>()
        effect(window) {
            val items = providerManager.currentIdsState()
            val focused = focusingV2()
            if (focused != null && focused !in items) {
                val filteredOldItems = oldItems.filterTo(mutableListOf()) { it in items || it == focused }
                val index = filteredOldItems.indexOf(focused)
                filteredOldItems.removeAt(index)
                if (filteredOldItems.isEmpty()) {
                    view.set(View.List)
                } else {
                    val newId = filteredOldItems[index.coerceIn(filteredOldItems.indices)]
                    view.set { v ->
                        when (v) {
                            View.List -> View.List
                            is View.Focus -> View.Focus(newId)
                            is View.Edit -> View.Edit(newId, if (v.back is View.Focus) View.Focus(newId) else View.List)
                        }
                    }
                }
            }
            oldItems = items
        }

        val viewComponent = State {
            when (view()) {
                View.List -> listViewComponent
                is View.Focus -> focusViewComponent
                is View.Edit -> editViewComponent
            }
        }

        titleBar.layoutAsBox {
            box(Modifier.fillWidth(padding = 10f).fillHeight()) {
                bind(viewComponent) { component ->
                    component.titleBar(Modifier.fillParent())
                }
            }
        }

        content.layout {
            bind(viewComponent) { component ->
                component(Modifier.fillParent())
            }
        }

        if (elementaDev || System.getProperty("essential.debugScreenshots", "false") == "true") {
            val text = window.pollingState { "${(providerManager.allocatedBytes / 1024)} KB" }
            UIText().bindText(text).constrain {
                x = 5.pixels(alignOpposite = true)
                y = 5.pixels
            } childOf window
        }
    }

    /**
     * On escape button pressed, returns true if call has been handled by method.
     */
    private fun onEscapeButtonPressed(): Boolean {
        return when (view.getUntracked()) {
            View.List -> false
            is View.Focus -> { focusViewComponent.onBackButtonPressed(); true }
            is View.Edit -> { editViewComponent.onBackButtonPressed(); true }
        }
    }

    override fun updateGuiScale() {
        newGuiScale = GuiUtil.getGuiScale()
        super.updateGuiScale()
    }
}
