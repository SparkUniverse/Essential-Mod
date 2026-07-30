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

import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.ContextOptionMenu
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.bindParent
import gg.essential.gui.common.onSetValueAndNow
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.friends.message.SocialMenuActions
import gg.essential.gui.friends.state.SocialStates
import gg.essential.gui.util.hoveredState
import gg.essential.network.connectionmanager.notices.SocialMenuNewFriendRequestNoticeManager
import gg.essential.util.*
import gg.essential.vigilance.utils.onLeftClick
import java.time.Instant
import java.util.*

class PendingUserEntry(
    user: UUID,
    val since: Instant,
    val incoming: Boolean,
    socialStates: SocialStates,
    socialMenuActions: SocialMenuActions,
    friendRequestNoticeManager: SocialMenuNewFriendRequestNoticeManager,
) : BasicUserEntry(user, EssentialPalette.CANCEL_5X, EssentialPalette.RED) {

    private val actions = socialStates.relationships
    private val hasUnseenRequest = friendRequestNoticeManager.hasUnseenFriendRequests(user)

    // True when this request was unseen and holds as true for the lifetime of this entry
    private val displayAsNewRequest = BasicState(false).apply {
        hasUnseenRequest.onSetValueAndNow(this@PendingUserEntry) {
            if (it) {
                set(true)
            }
        }
    }

    private val acceptButton by IconButton(EssentialPalette.CHECKMARK_7X5).constrain {
        x = SiblingConstraint(3f, alignOpposite = true)
        y = CenterConstraint() boundTo button
        width = CopyConstraintFloat() boundTo button
        height = AspectConstraint()
    }.apply {
        bindHoverEssentialTooltip(BasicState("Accept"), padding = 5f)
        bindParent(this@PendingUserEntry, BasicState(incoming))
        rebindIconColor(hoveredState().map {
            if (it) {
                EssentialPalette.GREEN
            } else {
                EssentialPalette.TEXT
            }
        })
    }.onLeftClick {
        actions.acceptIncomingFriendRequest(user, false)
    }

    private val requestTypeText by EssentialUIText(shadowColor = EssentialPalette.BLACK).bindText(
        displayAsNewRequest.map {
            if (it) {
                "New Incoming Request"
            } else if (incoming) {
                "Incoming Request"
            } else {
                "Outgoing Request"
            }
        }
    ).constrain {
        y = SiblingConstraint(6f) boundTo titleText
        x = CopyConstraintFloat() boundTo titleText
        color = EssentialPalette.TEXT.toConstraint()
    } childOf this

    init {
        button.bindHoverEssentialTooltip(BasicState("Cancel"))
        button.onLeftClick {
            if (incoming) {
                actions.declineIncomingFriendRequest(user, false)
            } else {
                actions.cancelOutgoingFriendRequest(user, false)
            }
        }
        onMouseClick {
            if (it.mouseButton != 1) {
                return@onMouseClick
            }

            ContextOptionMenu.create(
                ContextOptionMenu.Position(it),
                Window.of(this),
                ContextOptionMenu.Option("Block Player", image = EssentialPalette.BLOCK_10X7) {
                    socialMenuActions.blockOrUnblock(user)
                })
        }

        addUpdateFunc { _, _ ->
            // Clear the friend request seen notice if it exists
            // Cannot be done in init because the component is created on menu init
            if (hasUnseenRequest.get()) {
                friendRequestNoticeManager.clearUnseenFriendRequests(user)
            }
        }
    }
}
