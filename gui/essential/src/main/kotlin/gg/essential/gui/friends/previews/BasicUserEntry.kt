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
package gg.essential.gui.friends.previews

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.common.shadow.ShadowEffect
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.image.ImageFactory
import gg.essential.util.CachedAvatarImage
import gg.essential.gui.util.hoveredState
import gg.essential.util.UuidNameLookup
import java.awt.Color
import java.util.*

abstract class BasicUserEntry(
    val user: UUID,
    imageFactory: ImageFactory,
    hoverIconColor: Color,
) : UIBlock(EssentialPalette.COMPONENT_BACKGROUND), SearchableItem {
    val usernameState = UuidNameLookup.nameState(user, "Loading...")

    protected val imageContainer by CachedAvatarImage.create(user).constrain {
        x = 8.pixels
        y = CenterConstraint()
        width = 24.pixels
        height = AspectConstraint()
    }.apply {
        effect(ShadowEffect(Color.BLACK))
    } childOf this

    protected val textContainer by UIContainer().constrain {
        x = SiblingConstraint(7f)
        y = (CopyConstraintFloat() boundTo imageContainer) + 2.pixels
        height = 100.percent boundTo imageContainer
        width = basicWidthConstraint { button.getLeft() - it.getLeft() } - 7.pixels
    } childOf this

    protected val titleText by EssentialUIText(truncateIfTooSmall = true).bindText(usernameState.toV1(this)).constrain {
        width = width.coerceAtMost(100.percent)
    } childOf textContainer

    protected val button by IconButton(imageFactory).constrain {
        x = 10.pixels(alignOpposite = true)
        y = CenterConstraint()
        width = 17.pixels
        height = AspectConstraint()
    }.apply {
        rebindIconColor(hoveredState().map {
            if (it) {
                hoverIconColor
            } else {
                EssentialPalette.TEXT
            }
        })
    } childOf this

    init {
        constrain {
            y = SiblingConstraint(7f)
            width = 100.percent
            height = 40.pixels
        }
    }

    override fun getSearchTag() = usernameState.get()
}
