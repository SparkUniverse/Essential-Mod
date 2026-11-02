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
package gg.essential.gui.friends.message.v2

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.constraints.RelativeWindowConstraint
import gg.essential.elementa.constraints.SizeConstraint
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.utils.withAlpha
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.common.modal.OpenLinkModal
import gg.essential.gui.effects.AlphaEffect
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.animateTransitions
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.layoutdsl.BasicHeightModifier
import gg.essential.gui.layoutdsl.BasicWidthModifier
import gg.essential.gui.layoutdsl.BasicXModifier
import gg.essential.gui.layoutdsl.BasicYModifier
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.animateColor
import gg.essential.gui.layoutdsl.animateHeight
import gg.essential.gui.layoutdsl.animateWidth
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.effect
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.underline
import gg.essential.gui.layoutdsl.whenHovered
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.screenshot.constraints.AspectPreservingFillConstraint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Color
import java.net.URL
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

/**
 * Shows an image from an [ImageEmbed] at 75% of the window size, zooming in when opened and out when closed.
 *
 * The fade of the background is animated by this modal rather than the [ModalManager], as the modal manager's fade-in
 * fades the whole layer (including the image), and it has no fade-out.
 *
 * This modal must be opened with the modal manager's fade-in disabled (`platform.pushModal(doFadeInTransition = false)`).
 */
class ImagePreviewModal(
    modalManager: ModalManager,
    source: UIImage,
    private val aspectRatio: Float,
    private val url: URL?,
) : Modal(modalManager) {
    private val expanded = mutableStateOf(false)

    private val previewSize = State<() -> SizeConstraint> {
        val fraction = if (expanded()) EXPANDED_WINDOW_FRACTION else 0f
        { RelativeWindowConstraint(fraction) }
    }

    private val textAlpha = State { if (expanded()) 1f else 0f }
        .animateTransitions(driverComponent = this, ANIMATION_DURATION)

    private val backgroundColor = State {
        EssentialPalette.BLACK.withAlpha(if (expanded()) 200 else 0)
    }

    private val image = UIImage(CompletableFuture()).apply {
        textureMagFilter = UIImage.TextureScalingMode.LINEAR
        textureMinFilter = UIImage.TextureScalingMode.LINEAR
        source.supply(this)
    }

    override fun LayoutScope.layoutModal() {
        val imageSizeModifier = Modifier
            .then(BasicWidthModifier { AspectPreservingFillConstraint(stateOf(aspectRatio)) })
            .then(BasicHeightModifier { AspectPreservingFillConstraint(stateOf(aspectRatio)) })

        val imageBounds: UIComponent

        box(
            Modifier
                .fillParent(EXPANDED_WINDOW_FRACTION)
                .onLeftClick { handleEscapeKeyPress() }
        ) {
            // An invisible box matching the image's final size. It is used to position the "Open Original" link
            // instead of anchoring it to the image, as we do not want it to follow the image during the animation.
            imageBounds = box(imageSizeModifier)

            box(
                Modifier
                    .animateWidth(previewSize, ANIMATION_DURATION)
                    .animateHeight(previewSize, ANIMATION_DURATION)
            ) {
                image(imageSizeModifier.onLeftClick { it.stopImmediatePropagation() })
            }
        }

        if (url != null) {
            text(
                "Open Original",
                Modifier
                    .then(BasicXModifier { 0.pixels boundTo imageBounds })
                    .then(BasicYModifier { 3.pixels(alignOpposite = true, alignOutside = true) boundTo imageBounds })
                    .effect { AlphaEffect(textAlpha) }
                    .hoverScope()
                    .whenHovered(Modifier.underline())
                    .onLeftClick { OpenLinkModal.openUrl(url.toURI()) }
            )
        }
    }

    override fun onOpen() {
        super.onOpen()

        // The parent is the ModalManager's background component. We don't call the returned reset function when closing
        // the modal, as each `ImagePreviewModal` gets its own independent modal manager, and that isn't re-used.
        Modifier
            .color(Color(0, 0, 0, 0))
            .animateColor(backgroundColor, ANIMATION_DURATION)
            .applyToComponent(parent)
    }

    override fun afterInitialization() {
        super.afterInitialization()
        expanded.set(true)
    }

    override fun handleEscapeKeyPress() {
        if (!expanded.getUntracked()) {
            return
        }

        expanded.set(false)

        coroutineScope.launch {
            delay(ANIMATION_DURATION.toDouble().seconds)
            close()
        }
    }

    companion object {
        private const val ANIMATION_DURATION = 0.25f
        private const val EXPANDED_WINDOW_FRACTION = 0.75f
    }
}
