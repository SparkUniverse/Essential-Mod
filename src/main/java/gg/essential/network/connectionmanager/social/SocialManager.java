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
package gg.essential.network.connectionmanager.social;

import com.google.common.collect.Maps;
import gg.essential.connectionmanager.common.packet.social.SocialInviteToServerCancelPacket;
import gg.essential.connectionmanager.common.packet.social.SocialInviteToServerPacket;
import gg.essential.event.network.server.ServerLeaveEvent;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.NetworkedManager;
import gg.essential.network.connectionmanager.social.handler.SocialInviteToServerCancelPacketHandler;
import gg.essential.network.connectionmanager.social.handler.SocialInviteToServerPacketHandler;
import gg.essential.util.UUIDUtil;
import kotlin.collections.SetsKt;
import me.kbrewster.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SocialManager implements NetworkedManager {

    @NotNull
    private final Map<UUID, String> incomingServerInvites = Maps.newConcurrentMap();

    @NotNull
    private final Map<String, Set<UUID>> invitedFriends = Maps.newConcurrentMap();

    private final @NotNull ConnectionManager connectionManager;

    public SocialManager(@NotNull final ConnectionManager connectionManager) {
        connectionManager.registerPacketHandler(SocialInviteToServerPacket.class, new SocialInviteToServerPacketHandler(this));
        connectionManager.registerPacketHandler(SocialInviteToServerCancelPacket.class, new SocialInviteToServerCancelPacketHandler(this));
        this.connectionManager = connectionManager;
    }

    @NotNull
    public Map<UUID, String> getIncomingServerInvites() {
        return this.incomingServerInvites;
    }

    public void addIncomingServerInvite(@NotNull final UUID uuid, @NotNull final String address) {
        this.incomingServerInvites.put(uuid, address);
    }

    public void removeIncomingServerInvite(@NotNull final UUID uuid) {
        this.incomingServerInvites.remove(uuid);
    }

    public Set<UUID> getInvitesOnServer(String server) {
        return this.invitedFriends.getOrDefault(server, Collections.emptySet());
    }

    private void sendInvitesForServer(String server, Set<UUID> friends) {
        friends.forEach(uuid -> connectionManager.send(new SocialInviteToServerPacket(uuid, server)));
    }

    private void revokeInvites(Set<UUID> friends) {
        friends.forEach(uuid -> connectionManager.send(new SocialInviteToServerCancelPacket(uuid)));
    }

    public void setInvitedFriendsOnServer(String server, Set<UUID> friends) {
        // Copy the set
        friends = new HashSet<>(friends);

        // Remove the client UUID, so we don't end up inviting ourselves
        friends.remove(UUIDUtil.getClientUUID());

        friends = Collections.unmodifiableSet(friends);

        final Set<UUID> previousValue = this.invitedFriends.put(server, friends);
        final Set<UUID> previousInvites = previousValue != null ? previousValue : Collections.emptySet();

        revokeInvites(SetsKt.minus(previousInvites, friends));
        sendInvitesForServer(server, SetsKt.minus(friends, previousInvites));
    }

    public void reinviteFriendsOnServer(String server, Set<UUID> friends) {
        Set<UUID> invites = getInvitesOnServer(server);
        setInvitedFriendsOnServer(server, SetsKt.minus(invites, friends));
        setInvitedFriendsOnServer(server, SetsKt.plus(invites, friends));
    }

    @Override
    public void resetState() {
        this.invitedFriends.forEach((ignored, uuids) -> revokeInvites(uuids));
        this.invitedFriends.clear();
        this.incomingServerInvites.clear();
    }

    @Subscribe
    public void onServerLeave(ServerLeaveEvent event) {
        this.invitedFriends.forEach((ignored, uuids) -> revokeInvites(uuids));
        this.invitedFriends.clear();
    }
}
