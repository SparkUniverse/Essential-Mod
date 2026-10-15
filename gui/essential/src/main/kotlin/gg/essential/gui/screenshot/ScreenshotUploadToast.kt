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
package gg.essential.gui.screenshot

import com.sparkuniverse.toolbox.chat.model.Channel
import gg.essential.api.gui.Slot
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIText
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.FillConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixel
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.elementa.dsl.toConstraint
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.renderer.fillMcScale
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.shadow.ShadowIcon
import gg.essential.gui.elementa.state.v2.animateTransitions
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.notification.Notifications
import gg.essential.universal.USound
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick
import java.awt.Color
import java.util.function.Consumer

class ScreenshotUploadToast : UIContainer() {

    private val maxCompletionDelayMillis = 500
    private val startUploadMillis = System.currentTimeMillis()
    private val initialProgress: ToastProgress = ToastProgress.Step(0)
    private var targetProgress: ToastProgress = initialProgress
    private var currentProgress: ToastProgress = targetProgress
    val timerEnabled = BasicState(false)
    private val stateText by UIText("Uploading...").setShadowColor(EssentialPalette.TEXT_SHADOW_LIGHT).constrain {
        x = SiblingConstraint(6f)
        y = CenterConstraint()
        color = EssentialPalette.TEXT_HIGHLIGHT.toConstraint()
    } childOf this

    private val progressState = mutableStateOf(0f)
    private val progressStateAnimated = progressState.animateTransitions(this@ScreenshotUploadToast, 0.5f, Animations.LINEAR)

    private val progress by object : UIComponent() {

        init {
            isFloating = true
        }

        override fun extractComponent(extractor: ElementaExtractor) {
            val x = constraints.getX()
            val y = constraints.getY()
            val width = constraints.getWidth()
            val height = constraints.getHeight()

            val percent = progressStateAnimated.getUntracked()

            extractInner(extractor, x + 1, y + 1, width, height, percent, EssentialPalette.TEXT_SHADOW_LIGHT)
            extractInner(extractor, x, y, width, height, percent, EssentialPalette.TEXT_HIGHLIGHT)
        }

        private fun extractInner(extractor: ElementaExtractor, x: Float, y: Float, width: Float, height: Float, percent: Float, color: Color) {
            extractor.fillMcScale(x, y, x + width, y + 1, color)
            extractor.fillMcScale(x, y + height - 1, x + width, y + height, color)
            extractor.fillMcScale(x, y + 1, x + 1, y + height - 1, color)
            extractor.fillMcScale(x + width - 1, y + 1, x + width, y + height - 1, color)

            extractor.fillMcScale(x + 1, y + 1, x + 1 + (width - 2) * percent, y + height - 1, color)
        }
    }.constrain {
        x = SiblingConstraint(4f)
        y = CenterConstraint()
        width = FillConstraint(useSiblings = false) - 1.pixel
        height = 8.pixels
    } childOf this

    init {
        constrain {
            width = 100.percent
            height = ChildBasedMaxSizeConstraint() - 2.pixels
        }

        onLeftClick { click ->
            val progress = currentProgress
            if (progress is ToastProgress.Complete && progress.success) {
                progress.channels.lastOrNull()?.let { channel ->
                    platform.openSocialMenu(channel.id)
                    click.stopPropagation()
                }
            }
        }

        addUpdateFunc { _, _ -> updateProgress() }
    }

    private fun updateProgress() {
        val targetProgress = targetProgress
        if (currentProgress != targetProgress) {
            val previousProgress = currentProgress
            currentProgress = targetProgress

            // If we went straight to complete, skip the upload bar and just show the result
            if (targetProgress is ToastProgress.Complete && previousProgress == initialProgress) {
                fireComplete(targetProgress)
                return
            }

            val targetPercent = if (targetProgress is ToastProgress.Step) {
                targetProgress.completionPercent.coerceAtMost(100)
            } else {
                100
            }

            progressState.set(targetPercent * 0.01f)

            delay(500) {
                if (targetProgress is ToastProgress.Complete) {
                    // If we were successful, and it's been under maxCompletionDelayMillis, use some delay for dramatic effect.
                    val timeElapsedMillis = System.currentTimeMillis() - startUploadMillis
                    val delayMillis = maxCompletionDelayMillis - timeElapsedMillis
                    if (targetProgress.success && delayMillis > 0) {
                        delay(delayMillis) { fireComplete(targetProgress) }
                    } else {
                        fireComplete(targetProgress)
                    }
                }
            }
        }
    }

    fun createProgressConsumer(): Consumer<ToastProgress> {
        return Consumer<ToastProgress> { t ->
            Window.enqueueRenderOperation {
                targetProgress = t
            }
        }
    }

    private fun fireComplete(status: ToastProgress.Complete) {
        val action = {
            timerEnabled.set(true)
            removeChild(progress)
            stateText.setText(status.message)
            this.insertChildAt(
                ShadowIcon(
                    if (status.success) {
                        EssentialPalette.CHECKMARK_7X5
                    } else {
                        EssentialPalette.CANCEL_5X
                    }, true
                ).rebindPrimaryColor(BasicState(EssentialPalette.TEXT_HIGHLIGHT))
                    .rebindShadowColor(BasicState(EssentialPalette.MODAL_OUTLINE)).constrain {
                        y = CenterConstraint()
                    }, 0
            )
            USound.playLevelupSound()
        }
        action()
    }

    sealed class ToastProgress {

        data class Complete(val message: String, val success: Boolean, val channels: List<Channel> = listOf()) : ToastProgress() {

            constructor(message: String, success: Boolean) : this(message, success, listOf<Channel>())
        }

        data class Step(val completionPercent: Int) : ToastProgress()
    }

    companion object {
        @JvmStatic
        fun create(): Consumer<ToastProgress> {
            val toast = ScreenshotUploadToast()

            Notifications.push("", "") {
                timerEnabled = toast.timerEnabled
                withCustomComponent(Slot.LARGE_PREVIEW, toast)
            }
            return toast.createProgressConsumer()
        }
    }
}
