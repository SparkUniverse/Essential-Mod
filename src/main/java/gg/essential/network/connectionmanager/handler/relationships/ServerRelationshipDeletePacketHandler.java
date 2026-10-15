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
package gg.essential.network.connectionmanager.handler.relationships;

import gg.essential.connectionmanager.common.packet.relationships.ServerRelationshipDeletePacket;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.connectionmanager.relationship.RelationshipManager;
import org.jetbrains.annotations.NotNull;

public class ServerRelationshipDeletePacketHandler implements PacketHandler<ServerRelationshipDeletePacket> {

    private final RelationshipManager relationshipManager;

    public ServerRelationshipDeletePacketHandler(RelationshipManager relationshipManager) {
        this.relationshipManager = relationshipManager;
    }

    @Override
    public void handle(@NotNull final ServerRelationshipDeletePacket packet) {
        relationshipManager.removeRelationship(packet.getRelationship());
    }

}
