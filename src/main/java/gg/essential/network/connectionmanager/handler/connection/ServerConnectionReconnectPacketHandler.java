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
package gg.essential.network.connectionmanager.handler.connection;

import gg.essential.connectionmanager.common.packet.connection.ServerConnectionReconnectPacket;
import gg.essential.network.connectionmanager.CloseReason;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import org.jetbrains.annotations.NotNull;

public class ServerConnectionReconnectPacketHandler implements PacketHandler<ServerConnectionReconnectPacket> {

    private final ConnectionManager connectionManager;

    public ServerConnectionReconnectPacketHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void handle(@NotNull final ServerConnectionReconnectPacket packet) {
        connectionManager.close(CloseReason.SERVER_REQUESTED_RECONNECT);
    }

}
