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
package gg.essential.network.connectionmanager.handler.mojang;

import gg.essential.connectionmanager.common.packet.relationships.ServerUuidNameMapPacket;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.util.UUIDUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

public class ServerUuidNameMapPacketHandler implements PacketHandler<ServerUuidNameMapPacket> {

    @Override
    public void handle(@NotNull ServerUuidNameMapPacket packet) {
        for (Map.Entry<UUID, String> uuidStringEntry : packet.getMappedUuids().entrySet()) {
            UUIDUtil.populate(uuidStringEntry.getValue(), uuidStringEntry.getKey());
        }
    }
}
