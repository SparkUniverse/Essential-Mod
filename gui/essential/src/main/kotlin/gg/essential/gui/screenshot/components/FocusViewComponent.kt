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

import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.IconButton
import gg.essential.gui.effects.AlphaEffect
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.image.ImageFactory
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.BasicWidthModifier
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedWidth
import gg.essential.gui.layoutdsl.effect
import gg.essential.gui.layoutdsl.fillHeight
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.withHoverState
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.modal.deleteScreenshot
import gg.essential.gui.screenshot.openScreenshotPropertiesModal
import gg.essential.gui.screenshot.providers.RegisteredTexture
import gg.essential.gui.screenshot.providers.WindowedProvider
import gg.essential.gui.util.hoveredState
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.universal.UKeyboard
import gg.essential.universal.UResolution
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.onRightClick
import gg.essential.vigilance.utils.onLeftClick

class FocusViewComponent(
    private val screenshotManager: IScreenshotManager,
    private val providerManager: ScreenshotProviderManager,
    private val stateManager: ScreenshotStateManager,
    private val optionsDropdown: ScreenshotOptionsDropdown,
    private val view: MutableState<View>,
) : ScreenshotView(view.map { it is View.Focus }) {

    val previewing: ScreenshotId
        get() = (view.getUntracked() as View.Focus).screenshot

    val idState = view.map { (it as? View.Focus)?.screenshot }

    val textures = mutableStateOf<Map<ScreenshotId, RegisteredTexture>>(emptyMap())

    val centerIndex = memo { providerManager.currentIdsState().indexOf(idState()) }
    val leftId = memo { providerManager.currentIdsState().getOrNull(centerIndex() - 1) }
    val centerId = idState
    val rightId = memo { providerManager.currentIdsState().getOrNull(centerIndex() + 1) }

    init {
        titleBar.layoutAsBox {
            layoutTitleBar()
        }

        layoutAsBox {
            layoutContent()
        }
    }

    override fun afterInitialization() {
        super.afterInitialization()

        Window.of(this).onKeyType { _, keyCode ->
            if (active.get()) {
                val previewIndex = providerManager.currentIds.indexOf(idState.getUntracked() ?: return@onKeyType)
                if (keyCode == UKeyboard.KEY_LEFT && previewIndex > 0) {
                    focus(previewIndex - 1)
                } else if (keyCode == UKeyboard.KEY_RIGHT && previewIndex < providerManager.currentIds.size - 1) {
                    focus(previewIndex + 1)
                }
            }
        }
    }

    private fun LayoutScope.layoutTitleBar() {
        backButton(::onBackButtonPressed, Modifier.alignHorizontal(Alignment.Start))
        text({ centerId()?.name ?: "" }, Modifier.alignHorizontal(Alignment.Center))
        row(Modifier.alignHorizontal(Alignment.End), Arrangement.spacedBy(10f)) {
            if_({ textures()[centerId()]?.error == true }, cache = false) {
                row(Arrangement.spacedBy(3f)) {
                    propertiesButton()
                    deleteButton()
                }
            } `else` {
                row(Arrangement.spacedBy(3f)) {
                    editButton()
                    propertiesButton()
                }

                row(Arrangement.spacedBy(3f)) {
                    shareButton()
                    favoriteButton()
                    deleteButton()
                }
            }
        }
    }

    private fun LayoutScope.propertiesButton() =
        titleButton(EssentialPalette.PROPERTIES_7X5, "Properties")
            .onLeftClick {
                val id = previewing
                val metadata = stateManager.metadata(id).getUntracked()
                openScreenshotPropertiesModal(ScreenshotProperties(id, metadata))
            }

    private fun LayoutScope.deleteButton() =
        titleButton(EssentialPalette.TRASH_9X, "Delete")
            .onLeftClick {
                launchModalFlow(platform.createModalManager()) {
                    deleteScreenshot(screenshotManager, previewing)
                }
            }

    private fun LayoutScope.favoriteButton() {
        val modifier = Modifier.onLeftClick {
            stateManager.getFavoriteState(previewing).set { !it }
        }
        if_({ centerId()?.let { stateManager.getFavoriteState(it)() } ?: false }) {
            titleButton(EssentialPalette.HEART_FILLED_9X, "Remove Favorite", modifier)
                .rebindIconColor(BasicState(EssentialPalette.TEXT_RED))
        } `else` {
            titleButton(EssentialPalette.HEART_EMPTY_9X, "Favorite", modifier)
                .apply {
                    rebindIconColor(EssentialPalette.getTextColor(hoveredState()))
                }
        }
    }

    private fun LayoutScope.shareButton() =
        ShareButton(stateManager, centerId, null)
            .setDimension(IconButton.Dimension.Fixed(buttonSize, buttonSize))
            .invoke()

    private fun LayoutScope.editButton() =
        IconButton(EssentialPalette.EDIT_10X7, "Edit")
            .invoke()
            .setDimension(IconButton.Dimension.Fixed(44f, buttonSize))
            .onLeftClick {
                view.set { View.Edit(previewing, it) }
            }

    private fun LayoutScope.titleButton(icon: ImageFactory, tooltip: String, modifier: Modifier = Modifier) =
        IconButton(icon, tooltipText = tooltip)
            .setDimension(IconButton.Dimension.Fixed(buttonSize, buttonSize))
            .invoke(modifier)

    private fun LayoutScope.layoutContent() {
        // Based on the Figma designs (Screenshot Sharing > Share from Browser)
        val padding = 23f / UResolution.scaleFactor.toFloat()

        val imageSlotModifier = BasicWidthModifier { focusImageWidthPercent.percent boundTo this@FocusViewComponent }.fillHeight()

        box(Modifier.fillWidth().fillHeight(padding = focusImageVerticalPadding).effect { ScissorEffect() }) {
            row(Modifier.fillHeight().childBasedWidth()) {
                box(imageSlotModifier) {
                    ifNotNull(leftId) { id ->
                        sideScreenshotItem(id, textures, Modifier.alignHorizontal(Alignment.End))
                    }
                }
                spacer(width = padding)
                box(imageSlotModifier) {
                    ifNotNull(centerId) { id ->
                        centerScreenshotItem(id, textures)
                    }
                }
                spacer(width = padding)
                box(imageSlotModifier) {
                    ifNotNull(rightId) { id ->
                        sideScreenshotItem(id, textures, Modifier.alignHorizontal(Alignment.Start))
                    }
                }
            }
        }
    }

    init { addUpdateFunc { _, _ -> update() } }
    private fun update() {
        textures.set(providerManager.provideFocus(
            // Preload one extra in each direction so switching feels better, multiple windows ordered by importance
            listOf(0, 1, -1, 2, -2)
                .map { it + centerIndex.getUntracked() }
                .filter { it in providerManager.currentIds.indices }
                .map { WindowedProvider.Window(IntRange(it, it), false) }
        ))
    }

    private fun LayoutScope.centerScreenshotItem(
        id: ScreenshotId,
        textures: State<Map<ScreenshotId, RegisteredTexture?>>,
        modifier: Modifier = Modifier
    ) {
        screenshotImage(textures.map { it[id] }, modifier)
            .onRightClick {
                val error = textures.getUntracked()[id]?.error == true
                optionsDropdown.handleRightClick(id, it, error)
            }
    }

    private fun LayoutScope.sideScreenshotItem(
        id: ScreenshotId,
        textures: State<Map<ScreenshotId, RegisteredTexture?>>,
        modifier: Modifier = Modifier
    ) {
        val alpha = Modifier.hoverScope().withHoverState { hovered ->
            Modifier.effect { AlphaEffect(hovered.map { if (it) 0.5f else 0.25f }) }
        }
        screenshotImage(textures.map { it[id] }, alpha.then(modifier))
            .onLeftClick {
                USound.playButtonPress()
                view.set(View.Focus(id))
            }
    }

    /**
     * Opens the focus around the specified index. Exits if index is -1
     */
    fun focus(index: Int) {
        if (index == -1) {
            view.set { it.back }
            return
        }
        val id = providerManager.currentIds[index]
        view.set { when (it) {
            View.List -> it
            is View.Edit -> it.copy(screenshot = id)
            is View.Focus -> it.copy(screenshot = id)
        } }
    }

    /**
     * Fired when the back button is pressed
     */
    fun onBackButtonPressed() {
        view.set { it.back }
    }
}


