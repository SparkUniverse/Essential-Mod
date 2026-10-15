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
package gg.essential.network.connectionmanager.handler.profile.trustedhosts;

import gg.essential.connectionmanager.common.packet.profile.trustedhosts.ServerProfileTrustedHostsRemovePacket;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.connectionmanager.profile.ProfileManager;
import org.jetbrains.annotations.NotNull;

public class ServerProfileTrustedHostsRemovePacketHandler implements PacketHandler<ServerProfileTrustedHostsRemovePacket> {

    private final ProfileManager profileManager;

    public ServerProfileTrustedHostsRemovePacketHandler(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Override
    public void handle(@NotNull final ServerProfileTrustedHostsRemovePacket packet) {
        for (@NotNull final String trustedHostId : packet.getIds()) {
            profileManager.removeTrustedHost(trustedHostId);
        }
    }

}
