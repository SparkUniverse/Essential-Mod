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
package gg.essential.gui.layoutdsl

import gg.essential.elementa.UIComponent

private sealed interface TemplatedStringMember {
    data class ComponentReference(val name: String) : TemplatedStringMember

    data class TextLiteral(val value: String) : TemplatedStringMember
}

class WrappedTextBuilder {
    private val components = mutableMapOf<String, LayoutScope.() -> Unit>()

    operator fun String.invoke(block: LayoutScope.() -> Unit) = components.put(this, block)

    fun build(): Map<String, LayoutScope.() -> Unit> = this.components
}

/**
 * Allows regular components in a [LayoutScope] block to be inserted in-line with text.
 * Components are identified by a name surrounded by curly braces (`{name}`).
 *
 * Example [text]: `Your purchase of {cosmetic-icon} ${cosmetic.name} has been {checkmark} completed.`
 * In this [text] example, `cosmetic-icon` and `checkmark` are both components that should be provided in the [block]
 * (see [WrappedTextBuilder]).
 *
 * @param modifier The modifier to apply to the main column which holds each line.
 * @param textModifier The modifier to apply to text literals, this will not be applied to components.
 * @param verticalArrangement The arrangement to use between each line, defaults to `4f`.
 */
fun LayoutScope.wrappedText(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    verticalArrangement: Arrangement = Arrangement.spacedBy(4f),
    block: WrappedTextBuilder.() -> Unit
): UIComponent {
    val builder = WrappedTextBuilder()
    val components = builder.apply(block).build()
    val lines = text.split("\n")

    return column(modifier, verticalArrangement) {
        lines.forEach { line ->
           row(Arrangement.spacedBy(1f)) {
               line.splitByComponentTemplate().forEach { part ->
                   when (part) {
                       is TemplatedStringMember.TextLiteral -> text(part.value, textModifier)
                       is TemplatedStringMember.ComponentReference -> components[part.name]?.invoke(this)
                   }
               }
           }
        }
    }
}

private fun String.splitByComponentTemplate(): List<TemplatedStringMember> {
    val parts = mutableListOf<TemplatedStringMember>()

    var isComponentReference = false
    var currentPart = ""

    fun addCurrentPart() {
        if (currentPart.isEmpty()) return

        if (isComponentReference) {
            parts.add(TemplatedStringMember.ComponentReference(currentPart))
        } else {
            parts.add(TemplatedStringMember.TextLiteral(currentPart))
        }

        currentPart = ""
    }

    for (char in this) {
        when (char) {
            '{' -> {
                addCurrentPart()
                isComponentReference = true

                continue
            }

            '}' -> {
                addCurrentPart()
                isComponentReference = false

                continue
            }
        }

        currentPart += char
    }

    addCurrentPart()

    return parts
}
