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

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.FillConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.state.State
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.common.shadow.ShadowIcon
import gg.essential.gui.common.state
import gg.essential.gui.elementa.state.v2.asyncMap
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.stateUsingSystemTime
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.friends.state.IStatusStates
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.sps.SpsAddress
import gg.essential.util.AddressUtil
import gg.essential.util.Client
import gg.essential.util.ServerPingInfo
import gg.essential.util.UuidNameLookup
import gg.essential.util.formatTimeDifference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.plus
import java.time.Instant
import java.util.*

class FriendStatus(
    uuid: UUID,
    statusStates: IStatusStates,
) : UIContainer() {

    init {
        constrain {
            width = 100.percent
            height = ChildBasedMaxSizeConstraint()
        }.apply {
        }
        effect(this) {
            val activity = statusStates.getActivityState(uuid)()
            clearChildren()
            when (activity) {
                is PlayerActivity.Offline -> {
                    val lastOnlineText = stateUsingSystemTime { now ->
                        val lastOnline = activity.lastOnline ?: return@stateUsingSystemTime "Offline"
                        "Last online ${formatTimeDifference(Instant.ofEpochMilli(lastOnline), now)} ago"
                    }
                    EssentialUIText(lastOnlineText.getUntracked(), shadowColor = EssentialPalette.BLACK, truncateIfTooSmall = true).constrain {
                        color = EssentialPalette.TEXT_DISABLED.toConstraint()
                    }.bindText(lastOnlineText.toV1(this@FriendStatus))
                }
                is PlayerActivity.Online -> {
                    EssentialUIText("Online", truncateIfTooSmall = true).constrain {
                        color = EssentialPalette.GREEN.toConstraint()
                    }
                }
                is PlayerActivity.OnlineWithDescription -> {
                    val description = if (activity.description == AddressUtil.SINGLEPLAYER) {
                        "In World"
                    } else {
                        activity.description
                    }
                    EssentialUIText(description, truncateIfTooSmall = true).constrain {
                        color = EssentialPalette.GREEN.toConstraint()
                    }
                }
                is PlayerActivity.Multiplayer -> {
                    createJoinableEntry(activity.serverAddress.state())
                }
                is PlayerActivity.SPSSession -> {
                    if (activity.invited) {
                        @Suppress("OPT_IN_USAGE") // FIXME ideally shouldn't use GlobalScope
                        val worldName = stateOf(uuid).asyncMap(GlobalScope + Dispatchers.Client) {
                            ServerPingInfo.fetchViaPingProxy(SpsAddress(uuid).toString())?.worldName
                        }
                        val username = UuidNameLookup.nameState(activity.host)
                        createJoinableEntry(memo { worldName() ?: "${username()}'s world" }.toV1(this@FriendStatus))
                    } else {
                        EssentialUIText("In World", truncateIfTooSmall = true).constrain {
                            color = EssentialPalette.GREEN.toConstraint()
                        }
                    }
                }
            }.constrain {
                width = width.coerceAtMost(100.percent)
            } childOf this@FriendStatus
        }
    }

    private fun createJoinableEntry(display: State<String>): UIComponent {
        val container by UIContainer().constrain {
            height = ChildBasedMaxSizeConstraint()
            width = 100.percent
        }
        ShadowIcon(EssentialPalette.JOIN_ARROW_5X, true).constrain {
            y = 1.pixel
        }.rebindPrimaryColor(EssentialPalette.MESSAGE_SENT.state()).rebindShadowColor(EssentialPalette.BLACK.state()) childOf container

        EssentialUIText(shadowColor = EssentialPalette.BLACK, truncateIfTooSmall = true).bindText(display).constrain {
            x = SiblingConstraint(3f)
            width = width.coerceAtMost(FillConstraint(false))
            color = EssentialPalette.MESSAGE_SENT.toConstraint()
        } childOf container
        return container
    }

}
