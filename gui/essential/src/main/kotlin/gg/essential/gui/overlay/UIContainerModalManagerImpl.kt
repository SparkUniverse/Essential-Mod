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

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percentOfWindow
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.elementa.utils.withAlpha
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.common.modal.defaultEssentialModalFadeTime
import gg.essential.gui.transitions.FadeInTransition
import gg.essential.util.Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.Color

/**
 * A [ModalManager] which is backed by a [UIContainer].
 */
open class UIContainerModalManagerImpl(
    backgroundColor: Color = Color.BLACK.withAlpha(150),
    private val doFadeInTransition: Boolean = true,
) : UIContainer(), ModalManager {
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Client)

    private val modalQueue = mutableListOf<Modal>()
    override var isCurrentlyFadingIn: Boolean = false
        protected set

    private var didFade: Boolean = false

    protected val background by UIBlock(backgroundColor).constrain {
        x = 0.pixels
        y = 0.pixels
        width = 100.percentOfWindow()
        height = 100.percentOfWindow()
    } childOf this

    init {
        constrain {
            x = 0.pixels
            y = 0.pixels
            width = 100.percentOfWindow()
            height = 100.percentOfWindow()
        }
    }

    override fun queueModal(modal: Modal) {
        modalQueue.add(modal)

        if (hasParent && background.children.isEmpty()) {
            pushModalFromQueue()
            return
        }
    }

    override fun modalClosed() {
        val pushedModal = pushModalFromQueue()
        if (pushedModal == null) {
            // If there are no modals left, we can remove the manager from its parent.
            parent.removeChild(this)
            coroutineScope.cancel()
        }
    }

    override fun afterInitialization() {
        super.afterInitialization()

        // If a modal was queued before we had a parent, we need to make it a child now for it to be
        // displayed as expected.
        if (background.children.isEmpty()) {
            pushModalFromQueue()
        }
    }

    protected open fun setupModal(modal: Modal) {}

    protected fun pushModalFromQueue(): Modal? {
        val modal = modalQueue.removeFirstOrNull() ?: return null
        modal childOf background
        setupModal(modal)
        modal.onOpen()

        if (doFadeInTransition && !didFade) {
            didFade = true

            isCurrentlyFadingIn = true
            FadeInTransition(defaultEssentialModalFadeTime).transition(this) {
                isCurrentlyFadingIn = false
            }
        }

        return modal
    }
}
