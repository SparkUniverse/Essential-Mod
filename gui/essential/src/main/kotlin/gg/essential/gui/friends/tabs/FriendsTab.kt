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
package gg.essential.gui.friends.tabs

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.FillConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.components.*
import gg.essential.elementa.constraints.CopyConstraintFloat
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.bindParent
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.friends.Tab
import gg.essential.gui.friends.message.SocialMenuActions
import gg.essential.gui.friends.previews.*
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.gui.friends.state.SocialStates
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillRemainingHeight
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.layoutAsColumn
import gg.essential.gui.layoutdsl.scrollable
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.text
import gg.essential.network.connectionmanager.notices.SocialMenuNewFriendRequestNoticeManager
import gg.essential.util.scrollGradient

class FriendsTab(
    selectedTab: State<Tab>,
    private val socialStates: SocialStates,
    private val socialMenuActions: SocialMenuActions,
    private val friendRequestNoticeManager: SocialMenuNewFriendRequestNoticeManager,
    private val tabsSelector: UIComponent,
    private val rightDivider: UIComponent,
    private val searchQuery: State<String>,
) : TabComponent(Tab.FRIENDS, selectedTab) {

    private val horizontalDivider by UIBlock(EssentialPalette.COMPONENT_BACKGROUND).constrain {
        y = SiblingConstraint() boundTo tabsSelector
        width = 100.percent
        height = rightDivider.getWidth().pixels
    } childOf this

    private val sectionContainer by UIContainer().constrain {
        y = SiblingConstraint()
        width = 100.percent
        height = FillConstraint(useSiblings = false)
    } childOf this

    private val friendSection by Section(UserEntryType.FRIEND) childOf sectionContainer
    private val firstDivider by createDivider(friendSection) childOf sectionContainer
    private val pendingSection by Section(UserEntryType.PENDING) childOf sectionContainer
    private val secondDivider by createDivider(pendingSection) childOf sectionContainer
    private val blockedSection by Section(UserEntryType.BLOCKED) childOf sectionContainer
    private val thirdScrollArea by UIContainer().constrain {
        y = CopyConstraintFloat() boundTo blockedSection
        height = CopyConstraintFloat() boundTo blockedSection
        width = 100.percent
    }.bindParent(rightDivider, active).also {
        blockedSection.setupScrollbar(it)
    }

    // Unused, previously used for search in TabComponent, before Section class rewrite
    override val userLists: List<ScrollComponent> = listOf()

    private fun createDivider(section: Section): UIBlock {
        return UIBlock(EssentialPalette.COMPONENT_BACKGROUND).constrain {
            x = SiblingConstraint()
            width = rightDivider.getWidth().pixels
            height = 100.percent boundTo section
        }.also {
            section.setupScrollbar(it)
        }
    }

    override fun populate() {
        // Unused, previously used for search in TabComponent, before Section class rewrite
    }

    override fun sortUserLists() {
        // Unused, previously used for search in TabComponent, before Section class rewrite
    }

    private inner class Section(private val type: UserEntryType) : UIContainer() {
        private var scroller: ScrollComponent
        private val userList = State {
            val relationships = socialStates.relationships
            val search = searchQuery().lowercase()
            when (type) {
                UserEntryType.FRIEND -> relationships.friends()
                    .map { FriendUserEntry(it, socialStates, socialMenuActions) }
                    .sortedWith(
                        compareBy<FriendUserEntry> {
                            val activity = socialStates.activity.getActivityState(it.user)()
                            if (activity.isJoinable()) {
                                return@compareBy 0L
                            }
                            when (activity) {
                                is PlayerActivity.Multiplayer -> 0L
                                is PlayerActivity.SPSSession -> if (activity.isJoinable()) 0L else 1L
                                is PlayerActivity.OnlineWithDescription -> 1L
                                PlayerActivity.Online -> 2L
                                is PlayerActivity.Offline -> Long.MAX_VALUE - (activity.lastOnline ?: 0L)
                            }
                        }.thenBy { it.usernameState() }
                    )

                UserEntryType.BLOCKED -> relationships.blocked()
                    .map { BlockedUserEntry(it, socialMenuActions) }
                    .sortedBy { it.usernameState() }

                UserEntryType.PENDING -> (relationships.incomingFriendRequests() + relationships.outgoingFriendRequests())
                    .map { PendingUserEntry(it.user, it.since, false, socialStates, socialMenuActions, friendRequestNoticeManager) }
                    .sortedWith(
                        compareBy<PendingUserEntry> { it.incoming }.thenByDescending { it.since }
                    )
            }.filter { it.usernameState().contains(search, ignoreCase = true) }
        }.toListState()


        init {
            constrain {
                x = SiblingConstraint()
                width = (100.percent - (rightDivider.getWidth().pixels * 2)) / 3
                height = 100.percent
            }
            layoutAsColumn {
                spacer(height = 8f)
                text(type.sectionTitle, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT))
                spacer(height = 10f)
                scroller = scrollable(vertical = true, modifier = Modifier.fillRemainingHeight().fillWidth(1f, 10f)) {
                    column(Modifier.fillWidth().alignVertical(Alignment.Start)) {
                        if_({ userList().isEmpty() }) {
                            spacer(height = 4f)
                            text(type.emptyText, Modifier.color(EssentialPalette.TEXT))
                        } `else` {
                            column(Modifier.fillWidth(), Arrangement.spacedBy(7f)) {
                                forEach(userList) {
                                    it()
                                }
                            }
                            spacer(height = 10f)
                        }
                    }
                }
                scroller.scrollGradient(20.pixels)
            }
        }

        fun setupScrollbar(parent: UIComponent) {
            val scrollbar = UIBlock(EssentialPalette.SCROLLBAR).constrain {
                width = 100.percent
            } childOf parent

            scroller.setVerticalScrollBarComponent(scrollbar, hideWhenUseless = true)
        }

    }

    private enum class UserEntryType(
        val sectionTitle: String,
        val emptyText: String,
    ) {
        FRIEND("Friend List", "No Friends"),
        PENDING("Friend Requests", "No Friend Requests"),
        BLOCKED("Blocked Players", "No Players Blocked")
    }
}
