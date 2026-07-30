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

import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.friends.message.SocialMenuActions
import gg.essential.util.*
import gg.essential.vigilance.utils.onLeftClick
import java.util.*

class BlockedUserEntry(
    user: UUID,
    socialMenuActions: SocialMenuActions,
) : BasicUserEntry(user, EssentialPalette.CANCEL_5X, EssentialPalette.RED) {

    init {
        titleText.constrain {
            y = CenterConstraint()
        }

        button.bindHoverEssentialTooltip(usernameState.map { "Unblock $it" }.toV1(this))
        button.onLeftClick {
            socialMenuActions.blockOrUnblock(user)
        }
    }
}
