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

import gg.essential.connectionmanager.common.packet.chat.ChatChannelMessageDeletePacket;
import gg.essential.network.connectionmanager.chat.ChatManager;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import org.jetbrains.annotations.NotNull;

public class ChatChannelMessageDeletePacketHandler implements PacketHandler<ChatChannelMessageDeletePacket> {

    private final ChatManager chatManager;

    public ChatChannelMessageDeletePacketHandler(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @Override
    public void handle(@NotNull final ChatChannelMessageDeletePacket packet) {
        chatManager.removeMessage(packet.getChannelId(), packet.getMessageId());
    }

}
