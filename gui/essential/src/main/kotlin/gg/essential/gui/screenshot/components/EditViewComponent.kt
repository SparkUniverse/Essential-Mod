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

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.modal.ConfirmDenyModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.zip
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.screenshot.LocalScreenshot
import gg.essential.gui.screenshot.constraints.AspectPreservingFillConstraint
import gg.essential.gui.screenshot.editor.ScreenshotCanvas
import gg.essential.gui.screenshot.editor.change.EditHistory
import gg.essential.gui.screenshot.providers.RegisteredTexture
import gg.essential.gui.screenshot.providers.toSingleWindowRequest
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.centered
import gg.essential.util.thenAcceptOnMainThread
import java.io.File
import java.util.concurrent.CompletableFuture

class EditViewComponent(
    isScreenOpen: State<Boolean>,
    private val screenshotManager: IScreenshotManager,
    private val providerManager: ScreenshotProviderManager,
    stateManager: ScreenshotStateManager,
    private val view: MutableState<View>,
) : ScreenshotView(view.map { it is View.Edit }) {

    private val editHistory = EditHistory()

    private val textureState = mutableStateOf<RegisteredTexture?>(null)
    private val aspectConstraint = textureState.map {
        if (it != null) {
            it.imageWidth / it.imageHeight.toFloat()
        } else {
            16 / 9f
        }
    }

    private val imageSizeContainer by UIContainer().centered().constrain {
        width = focusImageWidthPercent.percent
        height = 100.percent - (focusImageVerticalPadding * 2).pixels
    } childOf this

    private val imageSize by UIContainer().centered().constrain {
        width = AspectPreservingFillConstraint(aspectConstraint)
        height = AspectPreservingFillConstraint(aspectConstraint)
    } childOf imageSizeContainer

    private val canvas by ScreenshotCanvas(textureState, editHistory).centered().constrain {
        width = (100.percent boundTo imageSize) + 4.pixels // Crop handles are 2px on each side
        height = (100.percent boundTo imageSize) + 4.pixels // Crop handles are 2px on each side
    } childOf this

    private val toolbar by EditorToolbar(isScreenOpen, canvas, editHistory, active.toV1(this)).constrain {
        y = 10.pixels(alignOpposite = true)
        x = 10.pixels
    } childOf this

    private val saveButton by IconButton(EssentialPalette.SAVE_9X, "Save", iconShadow = true)
        .setDimension(IconButton.Dimension.Fixed(47f, buttonSize))
        .rebindEnabled(editHistory.hasChanges.toV1(this))
        .onActiveClick {
            USound.playButtonPress()
            saveCurrentChanges()
        }.constrain {
            y = 10.pixels(alignOpposite = true)
            x = 10.pixels(alignOpposite = true)
        } childOf this

    private fun saveCurrentChanges() {
        val source = (view.getUntracked() as? View.Edit)?.screenshot
        if (source != null) {
            canvas.exportImage(
                source = source,
                screenshotManager = screenshotManager,
                temp = false,
            ).thenAcceptOnMainThread { file ->
                view.set(View.Focus(LocalScreenshot(file.toPath())))
            }
        }
    }

    init {
        val focusing = view.map { (it as? View.Edit)?.screenshot }

        val title = editHistory.hasChanges.zip(focusing) { edits, focusing ->
            if (focusing != null) {
                if (edits) {
                    "Copy of " + focusing.name
                } else {
                    focusing.name
                }
            } else {
                ""
            }
        }

        val shareButton = ShareButton(stateManager, view.map { (it as? View.Edit)?.screenshot }, this)
            .setDimension(IconButton.Dimension.Fixed(buttonSize, buttonSize))

        titleBar.layoutAsBox {
            backButton(::onBackButtonPressed, Modifier.alignHorizontal(Alignment.Start))

            text(title, Modifier.alignHorizontal(Alignment.Center))

            shareButton(Modifier.alignHorizontal(Alignment.End))
        }

        effect(this) {
            if (!active()) {
                editHistory.reset()
            }
        }

        addUpdateFunc { _, _ -> update() }
    }

    private fun update() {
        val focused = (view.getUntracked() as? View.Edit)?.screenshot
        if (focused != null) {

            val targetIndex = providerManager.currentIds.indexOf(focused)
            if (targetIndex != -1) {

                val provideFocus = providerManager.provideFocus(targetIndex.toSingleWindowRequest())
                val texture = provideFocus[focused]

                if (texture != null) {
                    textureState.set(texture)
                    return
                }
            }
        }

        textureState.set(null)
    }

    /**
     * @return whether the current screenshot has any edits made to it
     */
    fun hasEdits(): Boolean {
        return editHistory.hasChanges.getUntracked()
    }

    /**
     * Exports the current edits to a temporary file
     */
    fun exportEditImageToTempFile(): CompletableFuture<File>? {
        val source = (view.getUntracked() as? View.Edit)?.screenshot
        return if (source != null) {
            canvas.exportImage(
                source,
                screenshotManager,
                temp = true,
            )
        } else {
            null
        }
    }

    fun onBackButtonPressed() {
        if (editHistory.hasChanges.getUntracked()) {
            platform.pushModal { manager ->
                QuitWithoutSavingModal(manager).onPrimaryAction {
                    view.set { it.back }
                }
            }
        } else {
            view.set { it.back }
        }
    }

    class QuitWithoutSavingModal(manager: ModalManager) : ConfirmDenyModal(manager, false) {
        init {
            configure {
                primaryButtonText = "Continue"
                titleText = "Are you sure you want to quit without saving?"
            }
        }
    }
}
