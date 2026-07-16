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

import gg.essential.gui.common.ContextOptionMenu
import gg.essential.gui.friends.previews.ChannelPreview
import java.util.UUID

interface SocialMenuActions {
    fun openMessageScreen(channelId: Long)
    fun openMessageScreen(user: UUID)

    fun showManagementDropdown(
        preview: ChannelPreview,
        position: ContextOptionMenu.Position,
        extraOptions: List<ContextOptionMenu.Item> = emptyList(),
        onClose: () -> Unit = {},
    )

    fun showUserDropdown(
        user: UUID,
        position: ContextOptionMenu.Position,
        extraOptions: List<ContextOptionMenu.Item> = emptyList(),
        onClose: () -> Unit = {},
    )

    // TODO these don't really belong in here, but they have platform-dependencies, so we can't yet move them
    fun joinSessionWithConfirmation(user: UUID)
    fun invitePlayers(users: Set<UUID>, name: String)
    fun addOrRemoveFriend(uuid: UUID)
    fun blockOrUnblock(uuid: UUID)
}
