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
package gg.essential.gui.notification

import gg.essential.api.gui.NotificationType
import gg.essential.api.gui.Slot
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.PaddingConstraint
import gg.essential.elementa.dsl.width
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.font.DefaultFonts
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.effect
import gg.essential.gui.layoutdsl.fillRemainingWidth
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.floatingBox
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.maxHeight
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.then
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.util.sumOf
import java.awt.Color

private const val actionColumnPadding = 8f

fun LayoutScope.notificationContent(
    title: String,
    message: String,
    type: NotificationType,
    trimTitle: Boolean,
    trimMessage: Boolean,
    components: Map<Slot, UIComponent> = mapOf(),
    persistent: Boolean,
) {
    val customSlotModifier = Modifier.then {
        val target = this@then
        val bounds = object : UIContainer() {
            override fun getLeft(): Float = target.getLeft()
            override fun getTop(): Float = target.getTop()
            override fun getWidth(): Float = target.getWidth() + 1 /* for shadows */
            override fun getHeight(): Float = target.getHeight() + 1 /* for shadows */
        }
        Modifier.effect { ScissorEffect(bounds) }.applyToComponent(target)
    }

    val iconComponent = components[Slot.ICON] ?: EssentialPalette.ROUND_WARNING_7X.create().takeIf { type == NotificationType.ERROR || type == NotificationType.WARNING }
    val icon = iconComponent?.let { component -> fun LayoutScope.() {
        row {
            // Center the icon with the text if it is shorter, else align it to the baseline of the text
            val fakeText = text("")
            box(Modifier.height(fakeText).width(8f)) {
                floatingBox(
                    customSlotModifier.alignVertical(Alignment.Center).alignHorizontal(Alignment.Center(true))
                ) {
                    box { component(Modifier.color(type)) }
                    spacer(width = 1f, height = 1f)
                }
            }
        }
    } } ?: {}

    val iconNextToTitle = if (title.isNotEmpty()) icon else ({})
    val iconNextToMessage = if (title.isEmpty()) icon else ({})

    val previewComponent = components[Slot.PREVIEW]
    val preview = previewComponent?.let { component -> fun LayoutScope.() {
        box(customSlotModifier) { component() }
    } } ?: {}

    val previewNextToTitle = if (title.isNotEmpty()) preview else ({})
    val previewNextToMessage = if (title.isEmpty()) preview else ({})

    val actionComponent = components[Slot.ACTION]
    val action = actionComponent?.let { component -> fun LayoutScope.() {
        box(customSlotModifier) { component() }
    } } ?: {}

    val largePreviewComponent = components[Slot.LARGE_PREVIEW]
    val largePreview = largePreviewComponent?.let { component -> fun LayoutScope.() {
        box(Modifier.fillWidth().then(customSlotModifier)) { component() }
    } } ?: {}

    val smallPreviewComponent = components[Slot.SMALL_PREVIEW]
    val smallPreview = smallPreviewComponent?.let { component -> fun LayoutScope.() {
        box(customSlotModifier) { component() }
    } } ?: {}

    val messageLineSpacing = 10f

    var titleRowComponent: UIComponent? = null
    var actionColumnComponent: UIComponent? = null

    fun LayoutScope.titleRow() {
        titleRowComponent = row(Modifier.fillWidth(), Arrangement.spacedBy(5f), Alignment.Start) {
            previewNextToTitle()
            iconNextToTitle()

            box(Modifier.fillRemainingWidth()) {
                val titleModifier = Modifier.alignHorizontal(Alignment.Start).color(type)
                if (trimTitle) {
                    text(title, titleModifier, truncateIfTooSmall = true, showTooltipForTruncatedText = false)
                } else {
                    wrappedText(title, titleModifier, lineSpacing = messageLineSpacing)
                }
            }
        }
    }

    fun LayoutScope.messageRow(modifier: Modifier = Modifier.fillWidth()) {
        row(modifier, Arrangement.spacedBy(5f), Alignment.Start) {
            previewNextToMessage()
            iconNextToMessage()

            box(Modifier.fillRemainingWidth()) {
                val textHeightWithoutShadow = DefaultFonts.VANILLA_FONT_RENDERER.getBelowLineHeight() +
                    DefaultFonts.VANILLA_FONT_RENDERER.getBaseLineHeight()
                wrappedText(
                    message,
                    Modifier
                        .alignHorizontal(Alignment.Start)
                        .color(EssentialPalette.TOAST_BODY_COLOR)
                        .shadow(EssentialPalette.TEXT_SHADOW_LIGHT)
                        .then(
                            if (trimMessage) Modifier.maxHeight(messageLineSpacing * 3 - (messageLineSpacing - textHeightWithoutShadow)) else Modifier
                        ),
                    trimText = true,
                    lineSpacing = messageLineSpacing
                )
            }
        }
    }

    fun LayoutScope.actions(narrowLayout: Boolean) {
        if (actionComponent != null || smallPreviewComponent != null) {
            actionColumnComponent = column(Modifier.alignVertical(Alignment.End), Arrangement.spacedBy(5f), Alignment.End) {
                // Prevents overlap with close button
                if (narrowLayout && persistent) {
                    spacer(height = 8f)
                }
                smallPreview()
                action()
            }
        }
    }

    fun LayoutScope.wideLayout() {
        column(Modifier.fillWidth(), Arrangement.spacedBy(6f), horizontalAlignment = Alignment.Start) {
            if (title.isNotEmpty()) {
                titleRow()
            }

            val messageRowVisible = message.isNotEmpty() || (title.isEmpty() && (previewComponent != null || iconComponent != null))

            if (messageRowVisible || largePreviewComponent != null || smallPreviewComponent != null || actionComponent != null) {
                row(Modifier.fillWidth(), Arrangement.spacedBy(actionColumnPadding), Alignment.Start) {
                    if (messageRowVisible || largePreviewComponent != null) {
                        column(Modifier.fillRemainingWidth(), Arrangement.spacedBy(6f)) {
                            if (messageRowVisible) {
                                messageRow(Modifier.fillRemainingWidth())
                            }
                            largePreview()
                        }
                    }

                    actions(false)
                }
            }
        }
    }

    fun LayoutScope.narrowLayout() {
        row(Modifier.fillWidth(), Arrangement.spacedBy(actionColumnPadding), Alignment.Start) {
            if (title.isNotEmpty() || message.isNotEmpty() || previewComponent != null || iconComponent != null || largePreviewComponent != null) {
                column(Modifier.fillRemainingWidth(), Arrangement.spacedBy(6f), Alignment.Start) {
                    if (title.isNotEmpty()) {
                        titleRow()

                        if (message.isNotEmpty()) {
                            messageRow()
                        }
                    } else if (message.isNotEmpty() || previewComponent != null || iconComponent != null) {
                        messageRow()
                    }

                    largePreview()
                }
            }

            actions(true)
        }
    }

    val useWideLayout = mutableStateOf(true)

    box(
        Modifier
            .fillWidth()
            .childBasedHeight()
            .afterEachDraw { container ->
                val titleWidth = titleRowComponent?.let { component ->
                    component.children.sumOf {
                        val padding = (it.constraints.x as? PaddingConstraint)?.getHorizontalPadding(it) ?: 0f
                        val width = if (it != component.children.last()) it.getWidth() else title.width()
                        padding + width
                    }
                } ?: 0f

                val actionWidth = actionColumnComponent?.getWidth() ?: 0f
                val width = titleWidth + actionColumnPadding + actionWidth

                Window.enqueueRenderOperation { useWideLayout.set(width > container.getWidth()) }
            }
    ) {
        if_(useWideLayout, cache = false) {
            wideLayout()
        } `else` {
            narrowLayout()
        }
    }
}

private fun Modifier.color(notificationType: NotificationType) =
    color(notificationType.color()).shadow(notificationType.shadowColor())

private fun NotificationType.color(): Color {
    return when (this) {
        NotificationType.GENERAL -> Color(0xE5E5E5)
        NotificationType.INFO -> Color(0x0A82FD)
        NotificationType.ERROR -> Color(0xCC2929)
        NotificationType.WARNING -> Color(0xFFAA2B)
        NotificationType.DISCORD -> Color(0x7289DA)
    }
}

private fun NotificationType.shadowColor(): Color {
    return when (this) {
        NotificationType.GENERAL -> EssentialPalette.TEXT_SHADOW_LIGHT
        else -> EssentialPalette.BLACK
    }
}

private fun Modifier.afterEachDraw(block: (bound: UIComponent) -> Unit): Modifier =
    this then effect { AfterDrawEffect(block) }

private class AfterDrawEffect(val block: (bound: UIComponent) -> Unit) : Effect() {
    override fun extractAfter(extractor: ElementaExtractor) {
        block(boundComponent)
    }
}
