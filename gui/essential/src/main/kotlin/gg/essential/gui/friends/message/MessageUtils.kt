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
package gg.essential.gui.friends.message

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.essentialmarkdown.BlockquoteConfig
import gg.essential.gui.elementa.essentialmarkdown.HeaderConfig
import gg.essential.gui.elementa.essentialmarkdown.ListConfig
import gg.essential.gui.elementa.essentialmarkdown.MarkdownConfig
import gg.essential.gui.elementa.essentialmarkdown.ParagraphConfig
import gg.essential.gui.elementa.essentialmarkdown.TextConfig
import gg.essential.gui.elementa.essentialmarkdown.URLConfig
import java.awt.Color

object MessageUtils {

    fun getMessageWidth(isAnnouncement: Boolean, boundTo: UIComponent? = null): WidthConstraint {
        return if (isAnnouncement) {
            80.percent.apply {
                constrainTo = boundTo
            }
        } else {
            40.percent.apply {
                constrainTo = boundTo
            } + dotWidth.pixels
        }
    }

    val markdownStyleConfig: MarkdownConfig = MarkdownConfig(
        textConfig = TextConfig(EssentialPalette.TEXT_HIGHLIGHT),
        // Design measures line height from the bottom of normal characters,
        // while markdown components measure it from the bottom of lower characters like g
        // This is why this value is 1 lower than mentioned in design
        paragraphConfig = ParagraphConfig(
            spaceBetweenLines = 3f,
        ),
        urlConfig = URLConfig(EssentialPalette.LINK, EssentialPalette.LINK_HIGHLIGHT, underline = true),
    )

    val restrictedMarkdownConfig: MarkdownConfig = markdownStyleConfig.copy(
        headerConfig = HeaderConfig(enabled = false),
        blockquoteConfig = BlockquoteConfig(enabled = false),
        listConfig = ListConfig(enabled = false),
    )

    val incomingMessageMarkdownConfig: MarkdownConfig = restrictedMarkdownConfig.copy(
        textConfig = restrictedMarkdownConfig.textConfig.copy(
            shadowColor = EssentialPalette.TEXT_SHADOW,
        )
    )

    val outgoingMessageMarkdownConfig: MarkdownConfig = restrictedMarkdownConfig.copy(
        textConfig = restrictedMarkdownConfig.textConfig.copy(shadowColor = EssentialPalette.TEXT_SHADOW),
        urlConfig = restrictedMarkdownConfig.urlConfig.copy(
            fontColor = EssentialPalette.TEXT_HIGHLIGHT,
            fontColorOnHover = EssentialPalette.WHITE
        ),
    )

    val failedMessageMarkdownConfig = outgoingMessageMarkdownConfig.copy(
        textConfig = outgoingMessageMarkdownConfig.textConfig.copy(
            color = EssentialPalette.FAILED_MESSAGE_TEXT,
        )
    )

    val blockedMessageMarkdownConfig = restrictedMarkdownConfig.copy(
        textConfig = restrictedMarkdownConfig.textConfig.copy(
            color = EssentialPalette.TEXT_DISABLED,
            shadowColor = EssentialPalette.TEXT_SHADOW,
        ),
        urlConfig = URLConfig(enabled = false),
    )

    val fullMarkdownConfig: MarkdownConfig = markdownStyleConfig.copy(
        textConfig = markdownStyleConfig.textConfig.copy(
            shadowColor = Color(0x101010),
        )
    )

    val INLINE_STYLE_LINK_REGEX: Regex = Regex("\\[(?<text>.*)]\\(.*\\)")

    private const val URL_REGEX_TEXT = "(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})"

    val URL_REGEX: Regex = Regex(URL_REGEX_TEXT)

    fun String.handleMarkdownUrls(): String {
        var result = this
        result = INLINE_STYLE_LINK_REGEX.replace(result) { it.groups["text"]?.value ?: "" }
        result = URL_REGEX.replace(result) {
            val url = it.value
            "<${if ("://" in url) url else "https://$url"}>"
        }.replace("`", "")
        return result
    }

    const val dotWidth = 3f
    const val messagePadding = 20f

}
