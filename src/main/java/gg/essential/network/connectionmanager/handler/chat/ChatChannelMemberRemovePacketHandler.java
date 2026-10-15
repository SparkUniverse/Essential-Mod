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
package gg.essential.network.connectionmanager.handler.chat;

import gg.essential.connectionmanager.common.packet.chat.ChatChannelMemberRemovePacket;
import gg.essential.network.connectionmanager.chat.ChatManager;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import org.jetbrains.annotations.NotNull;

public class ChatChannelMemberRemovePacketHandler implements PacketHandler<ChatChannelMemberRemovePacket> {

    private final ChatManager chatManager;

    public ChatChannelMemberRemovePacketHandler(ChatManager chatManager) {
        this.chatManager = chatManager;
    }


    @Override
    public void handle(@NotNull final ChatChannelMemberRemovePacket packet) {
        chatManager.membersRemoved(packet.getChannelId(), packet.getMembers());
    }

}
