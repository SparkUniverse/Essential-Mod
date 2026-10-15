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
package gg.essential.network.connectionmanager.relationship;

import com.google.common.collect.Maps;
import com.sparkuniverse.toolbox.relationships.enums.RelationshipState;
import com.sparkuniverse.toolbox.relationships.enums.RelationshipType;
import gg.essential.Essential;
import gg.essential.connectionmanager.common.model.relationships.Relationship;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.connectionmanager.common.packet.relationships.ClientRelationshipCreatePacket;
import gg.essential.connectionmanager.common.packet.relationships.RelationshipDeletePacket;
import gg.essential.connectionmanager.common.packet.relationships.ServerRelationshipCreateFailedResponsePacket;
import gg.essential.connectionmanager.common.packet.relationships.ServerRelationshipDeletePacket;
import gg.essential.connectionmanager.common.packet.relationships.ServerRelationshipPopulatePacket;
import gg.essential.connectionmanager.common.packet.relationships.privacy.FriendRequestPrivacySettingPacket;
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket;
import gg.essential.gui.EssentialPalette;
import gg.essential.gui.friends.state.IRelationshipManager;
import gg.essential.gui.notification.ExtensionsKt;
import gg.essential.gui.notification.Notifications;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.NetworkedManager;
import gg.essential.network.connectionmanager.StateCallbackManager;
import gg.essential.network.connectionmanager.handler.relationships.FriendRequestPrivacySettingPacketHandler;
import gg.essential.network.connectionmanager.handler.relationships.ServerRelationshipDeletePacketHandler;
import gg.essential.network.connectionmanager.handler.relationships.ServerRelationshipPopulatePacketHandler;
import gg.essential.util.ObservableMapEvent;
import gg.essential.util.SimpleObservableMap;
import gg.essential.util.StringsKt;
import gg.essential.util.USession;
import gg.essential.util.UUIDUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static gg.essential.network.connectionmanager.relationship.RelationshipResponseKt.displayToast;

// TODO: Should we have individual maps for the RelationshipType.FRIENDS and BLOCKED ?
//       _or_ perhaps we just have 1 map for all relationships and then sort through them
//      when / if necessary?
public class RelationshipManager extends StateCallbackManager<IRelationshipManager> implements NetworkedManager {

    @NotNull
    private final ConnectionManager connectionManager;


    @NotNull
    private final SimpleObservableMap<UUID, Relationship>
        friends = new SimpleObservableMap<>(Maps.newConcurrentMap()),
        outgoingFriendRequests = new SimpleObservableMap<>(Maps.newConcurrentMap()),
        incomingFriendRequests = new SimpleObservableMap<>(Maps.newConcurrentMap()),
        blockedByMe = new SimpleObservableMap<>(Maps.newConcurrentMap()),
        blockedMe = new SimpleObservableMap<>(Maps.newConcurrentMap());

    public RelationshipManager(@NotNull final ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        connectionManager.registerPacketHandler(FriendRequestPrivacySettingPacket.class, new FriendRequestPrivacySettingPacketHandler());
        connectionManager.registerPacketHandler(ServerRelationshipDeletePacket.class, new ServerRelationshipDeletePacketHandler(this));
        connectionManager.registerPacketHandler(ServerRelationshipPopulatePacket.class, new ServerRelationshipPopulatePacketHandler(this));

        friends.addObserver((o, arg) -> {
            for (IRelationshipManager callback : getCallbacks()) {
                if (arg instanceof ObservableMapEvent.Clear) {
                    callback.clearFriends();
                } else if (arg instanceof ObservableMapEvent.Add) {
                    callback.friendAdded(((ObservableMapEvent.Add<UUID, ?>) arg).getElement().component1());
                } else if (arg instanceof ObservableMapEvent.Remove) {
                    callback.friendRemoved(((ObservableMapEvent.Remove<UUID, ?>) arg).getElement().component1());
                }
            }
        });

        blockedByMe.addObserver((o, arg) -> {
            for (IRelationshipManager callback : getCallbacks()) {
                if (arg instanceof ObservableMapEvent.Clear) {
                    callback.clearBlocked();
                } else if (arg instanceof ObservableMapEvent.Add) {
                    callback.playerBlocked(((ObservableMapEvent.Add<UUID, ?>) arg).getElement().component1());
                } else if (arg instanceof ObservableMapEvent.Remove) {
                    callback.playerUnblocked(((ObservableMapEvent.Remove<UUID, ?>) arg).getElement().component1());
                }
            }
        });

        incomingFriendRequests.addObserver((o, arg) -> {
            for (IRelationshipManager callback : getCallbacks()) {
                if (arg instanceof ObservableMapEvent.Clear) {
                    callback.clearAllIncomingRequests();
                } else if (arg instanceof ObservableMapEvent.Add) {
                    callback.newIncomingFriendRequest(((ObservableMapEvent.Add<UUID, ?>) arg).getElement().component1());
                } else if (arg instanceof ObservableMapEvent.Remove) {
                    callback.clearIncomingFriendRequest(((ObservableMapEvent.Remove<UUID, ?>) arg).getElement().component1());
                }
            }
        });

        outgoingFriendRequests.addObserver((o, arg) -> {
            for (IRelationshipManager callback : getCallbacks()) {
                if (arg instanceof ObservableMapEvent.Clear) {
                    callback.clearAllOutgoingRequests();
                } else if (arg instanceof ObservableMapEvent.Add) {
                    callback.newOutgoingFriendRequest(((ObservableMapEvent.Add<UUID, ?>) arg).getElement().component1());
                } else if (arg instanceof ObservableMapEvent.Remove) {
                    callback.clearOutgoingFriendRequest(((ObservableMapEvent.Remove<UUID, ?>) arg).getElement().component1());
                }
            }
        });
    }

    @NotNull
    public Map<UUID, Relationship> getFriends() {
        return this.friends;
    }

    @NotNull
    public Map<UUID, Relationship> getOutgoingFriendRequests() {
        return this.outgoingFriendRequests;
    }

    @NotNull
    public Map<UUID, Relationship> getIncomingFriendRequests() {
        return this.incomingFriendRequests;
    }

    @NotNull
    public Map<UUID, Relationship> getBlockedByMe() {
        return this.blockedByMe;
    }

    @Nullable
    public Relationship getFriend(@NotNull final UUID uuid) {
        return this.friends.get(uuid);
    }

    @Nullable
    public Relationship getOutgoingFriendRequest(@NotNull final UUID uuid) {
        return this.outgoingFriendRequests.get(uuid);
    }

    @Nullable
    public Relationship getIncomingFriendRequest(@NotNull final UUID uuid) {
        return this.incomingFriendRequests.get(uuid);
    }

    @Nullable
    public Relationship getBlockedByMe(@NotNull final UUID uuid) {
        return this.blockedByMe.get(uuid);
    }

    public boolean isFriend(@NotNull final UUID uuid) {
        return this.friends.containsKey(uuid);
    }

    public boolean hasOutgoingFriendRequest(@NotNull final UUID uuid) {
        return this.outgoingFriendRequests.containsKey(uuid);
    }

    public boolean isBlockedByMe(@NotNull final UUID uuid) {
        return this.blockedByMe.containsKey(uuid);
    }

    public boolean hasBlockedMe(@NotNull final UUID uuid) {
        return this.blockedMe.containsKey(uuid);
    }

    /**
     * @return False if an error was generated AND it was handled
     */
    public CompletableFuture<RelationshipResponse> createFriendRelationship(@NotNull final UUID targetUUID, final boolean acceptingRequest) {
        return createFriendRelationship(targetUUID, acceptingRequest, true);
    }

    public CompletableFuture<RelationshipResponse> createFriendRelationship(@NotNull final UUID targetUUID, final boolean acceptingRequest, final boolean notification) {
        // Before we send data to the connection manager about attempting to create a friend relationship let's just
        // check our cache first to help prevent the waste of packets hitting the CM.

        if (targetUUID.equals(UUIDUtil.getClientUUID())) {
            if (notification) {
            }
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "Cannot add yourself"));
        }

        if (this.isFriend(targetUUID)) {
            if (notification) {
                UUIDUtil.getName(targetUUID).whenCompleteAsync((name, e) -> {
                    String username = name != null ? name : "unknown";
                    ExtensionsKt.error(
                        Notifications.INSTANCE, "Friend request failed", "",
                        () -> Unit.INSTANCE, () -> Unit.INSTANCE,
                        builder -> {
                            ExtensionsKt.markdownBody(builder,
                                "You are already friends with " + StringsKt.colored(username, EssentialPalette.TEXT_HIGHLIGHT) + "."
                            );
                            return Unit.INSTANCE;
                        }
                    );
                });
            }
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "Already your friend"));
        }

        if (!acceptingRequest && this.hasOutgoingFriendRequest(targetUUID)) {
            if (notification) {
                UUIDUtil.getName(targetUUID).whenCompleteAsync((name, e) -> {
                    String username = name != null ? name : "unknown";
                    ExtensionsKt.error(
                        Notifications.INSTANCE, "Friend request failed", "",
                        () -> Unit.INSTANCE, () -> Unit.INSTANCE,
                        builder -> {
                            ExtensionsKt.markdownBody(builder,
                                "Friend request already sent to " + StringsKt.colored(username, EssentialPalette.TEXT_HIGHLIGHT) + "."
                            );
                            return Unit.INSTANCE;
                        }
                    );
                });
            }
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "Already invited to be friend"));
        }

        if (this.isBlockedByMe(targetUUID)) {
            UUIDUtil.getName(targetUUID).whenCompleteAsync((name, e) -> {
                String username = name != null ? name : "unknown";
                ExtensionsKt.error(
                    Notifications.INSTANCE, "Friend request failed", "",
                    () -> Unit.INSTANCE, () -> Unit.INSTANCE,
                    builder -> {
                        ExtensionsKt.markdownBody(builder,
                            "You have blocked " + StringsKt.colored(username, EssentialPalette.TEXT_HIGHLIGHT) + "."
                                + " Unblock them before sending them a friend request."
                        );
                        return Unit.INSTANCE;
                    }
                );
            });
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "You blocked this player"));
        }

        if (this.hasBlockedMe(targetUUID)) {
            if (notification) {
                UUIDUtil.getName(targetUUID).whenCompleteAsync((name, e) -> {
                    String username = name != null ? name : "unknown";
                    ExtensionsKt.warning(
                        Notifications.INSTANCE, "Friend request declined", "",
                        () -> Unit.INSTANCE, () -> Unit.INSTANCE,
                        builder -> {
                            ExtensionsKt.markdownBody(builder,
                                StringsKt.colored(username, EssentialPalette.TEXT_HIGHLIGHT) + " has blocked you."
                            );
                            return Unit.INSTANCE;
                        }
                    );
                });
            }
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "This player blocked you"));
        }
        return this.createRelationship(targetUUID, RelationshipType.FRIENDS);
    }

    public CompletableFuture<RelationshipResponse> createBlockedRelationship(@NotNull final UUID uuid) {
        return createBlockedRelationship(uuid, true);
    }

    public CompletableFuture<RelationshipResponse> createBlockedRelationship(@NotNull final UUID uuid, final boolean notification) {
        if (uuid.equals(UUIDUtil.getClientUUID())) {
            if (notification) {
            }
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "Cannot block yourself"));
        }
        if (this.isBlockedByMe(uuid)) {
            if (notification) {
            }
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "Already blocked this player"));
        }
        return this.createRelationship(uuid, RelationshipType.BLOCKED);
    }

    public void createRelationship(@NotNull final Relationship relationship) {
        final UUID ourUUID = USession.Companion.activeNow().getUuid();
        final boolean areWeTheSender = (relationship.getSenderUUID().equals(ourUUID));
        final UUID targetUUID = (areWeTheSender ? relationship.getTargetUUID() : relationship.getSenderUUID());

        switch (relationship.getType()) {
            case NEUTRAL:
                this.friends.remove(targetUUID);
                this.outgoingFriendRequests.remove(targetUUID);
                this.incomingFriendRequests.remove(targetUUID);
                this.blockedMe.remove(targetUUID);
                this.blockedByMe.remove(targetUUID);
                break;
            case FRIENDS:
                switch (relationship.getState()) {
                    case PENDING:
                        this.friends.remove(targetUUID);
                        this.blockedMe.remove(targetUUID);
                        this.blockedByMe.remove(targetUUID);

                        if (areWeTheSender) {
                            this.outgoingFriendRequests.put(targetUUID, relationship);
                        } else this.incomingFriendRequests.put(targetUUID, relationship);

                        break;
                    case VERIFIED:
                        this.outgoingFriendRequests.remove(targetUUID);
                        this.incomingFriendRequests.remove(targetUUID);
                        this.blockedMe.remove(targetUUID);
                        this.blockedByMe.remove(targetUUID);

                        this.friends.put(targetUUID, relationship);
                        break;
                    case DECLINED:
                        this.friends.remove(targetUUID);
                        this.outgoingFriendRequests.remove(targetUUID);
                        this.incomingFriendRequests.remove(targetUUID);
                        this.blockedMe.remove(targetUUID);
                        this.blockedByMe.remove(targetUUID);
                        break;
                }
                break;
            case BLOCKED:
                this.friends.remove(targetUUID);
                this.outgoingFriendRequests.remove(targetUUID);
                this.incomingFriendRequests.remove(targetUUID);

                if (areWeTheSender) {
                    this.blockedByMe.put(targetUUID, relationship);
                } else this.blockedMe.put(targetUUID, relationship);
                break;
        }
    }

    public CompletableFuture<RelationshipResponse> createRelationship(@NotNull final UUID targetUUID, @NotNull final RelationshipType type) {
        final CompletableFuture<RelationshipResponse> future = new CompletableFuture<>();
        this.connectionManager.send(new ClientRelationshipCreatePacket(targetUUID, type), responseOptional -> {
            if (!responseOptional.isPresent()) {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "A timeout occurred please try again.");
            }
            future.complete(responseOptional.map(packet -> {
                if (packet instanceof ServerRelationshipCreateFailedResponsePacket) {
                    String reason = ((ServerRelationshipCreateFailedResponsePacket) packet).getReason();
                    RelationshipErrorResponse relationshipErrorResponse = RelationshipErrorResponse.getResponse(reason);
                    if (relationshipErrorResponse == null) {
                        Essential.logger.error("Unknown relationshipErrorResponse reason: " + reason);
                    }
                    return new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, relationshipErrorResponse);
                }
                if (!(packet instanceof ServerRelationshipPopulatePacket)) {
                    return new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "An unknown error occurred");
                }
                return new RelationshipResponse((((ServerRelationshipPopulatePacket) packet).getRelationships()[0].getState() != RelationshipState.DECLINED ? FriendRequestState.SENT : FriendRequestState.ERROR_UNHANDLED));
            }).orElse(new RelationshipResponse(FriendRequestState.ERROR_HANDLED, "An unknown error occurred")));
        });
        return future;
    }

    public void removeRelationship(@NotNull final Relationship relationship) {
        final UUID ourUUID = USession.Companion.activeNow().getUuid();
        final boolean areWeTheSender = (relationship.getSenderUUID().equals(ourUUID));
        final UUID targetUUID = (areWeTheSender ? relationship.getTargetUUID() : relationship.getSenderUUID());

        switch (relationship.getType()) {
            case FRIENDS:
                if (relationship.isPending()) {
                    if (areWeTheSender) {
                        this.outgoingFriendRequests.remove(targetUUID);
                    } else this.incomingFriendRequests.remove(targetUUID);
                } else this.friends.remove(targetUUID);
                break;
            case BLOCKED:
                if (areWeTheSender) {
                    this.blockedByMe.remove(targetUUID);
                } else this.blockedMe.remove(targetUUID);
                break;
        }
    }

    public CompletableFuture<RelationshipResponse> deleteRelationship(@NotNull final UUID targetUUID, @NotNull final RelationshipType type) {
        CompletableFuture<RelationshipResponse> future = new CompletableFuture<>();
        this.connectionManager.send(new RelationshipDeletePacket(targetUUID, type), responseOptional -> {
            if (!responseOptional.isPresent()) {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "A timeout occurred please try again.");
                future.complete(new RelationshipResponse(FriendRequestState.ERROR_HANDLED));
                return;
            }

            final Packet responsePacket = responseOptional.get();

            if (!(responsePacket instanceof ResponseActionPacket)) {
                if (responsePacket instanceof ServerRelationshipCreateFailedResponsePacket) {
                    String reason = ((ServerRelationshipCreateFailedResponsePacket) responsePacket).getReason();
                    RelationshipErrorResponse relationshipErrorResponse = RelationshipErrorResponse.getResponse(reason);
                    if (relationshipErrorResponse == null) {
                        Essential.logger.error("Unknown relationshipErrorResponse reason: " + reason);
                    }
                    RelationshipResponse relationshipResponse = new RelationshipResponse(FriendRequestState.ERROR_HANDLED, relationshipErrorResponse);
                    displayToast(relationshipResponse, targetUUID);
                    future.complete(relationshipResponse);
                } else {
                    future.complete(new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "An unknown error occurred. Please contact support if issues persist."));
                }
                return;
            }

            final ResponseActionPacket response = (ResponseActionPacket) responsePacket;

            //Should never happen because failure should trigger a ServerRelationshipCreateFailedResponsePacket
            if (!response.isSuccessful()) {
                future.complete(new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "An unknown error occurred. Please contact support if issues persist."));
                return;
            }

            this.outgoingFriendRequests.remove(targetUUID);
            this.incomingFriendRequests.remove(targetUUID);
            this.friends.remove(targetUUID);
            this.blockedByMe.remove(targetUUID);
            this.blockedMe.remove(targetUUID);

            future.complete(new RelationshipResponse(FriendRequestState.SENT));
        });
        return future;
    }

    public CompletableFuture<RelationshipResponse> unblock(@NotNull UUID user) {
        Relationship block = blockedByMe.get(user);
        if (block != null) {
            return deleteRelationship(user, RelationshipType.BLOCKED);
        } else {
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "This player is not blocked"));
        }
    }

    public CompletableFuture<RelationshipResponse> removeFriend(@NotNull UUID otherUser) {
        Relationship friendRelationship = friends.get(otherUser);
        if (friendRelationship != null) {
            return deleteRelationship(otherUser, friendRelationship.getType());
        } else {
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "You are not friends with this player"));
        }
    }

    public CompletableFuture<RelationshipResponse> acceptFriend(@NotNull UUID user) {
        return createFriendRelationship(user, true);
    }

    public CompletableFuture<RelationshipResponse> denyFriend(@NotNull UUID user) {
        Relationship request = incomingFriendRequests.get(user);
        if (request != null) {
            removeRelationship(request);
            return deleteRelationship(user, RelationshipType.FRIENDS);
        } else {
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "You do not have a friend request from this player"));
        }
    }

    public CompletableFuture<RelationshipResponse> cancelFriendRequest(@NotNull UUID user) {
        Relationship request = outgoingFriendRequests.get(user);
        if (request != null) {
            removeRelationship(request);
            return deleteRelationship(user, RelationshipType.FRIENDS);
        } else {
            return CompletableFuture.completedFuture(new RelationshipResponse(FriendRequestState.ERROR_UNHANDLED, "You do not have a friend request to this player"));
        }
    }

    public CompletableFuture<RelationshipResponse> addFriend(@NotNull UUID uuid, boolean notification) {
        return createFriendRelationship(uuid, false, notification);
    }

    @Override
    public void onConnected() {
        resetState();
    }

    @Override
    public void resetState() {
        this.blockedMe.clear();
        this.blockedByMe.clear();
        this.incomingFriendRequests.clear();
        this.outgoingFriendRequests.clear();
        this.friends.clear();
    }

}
