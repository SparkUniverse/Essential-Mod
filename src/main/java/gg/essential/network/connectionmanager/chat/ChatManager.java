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
package gg.essential.network.connectionmanager.chat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sparkuniverse.toolbox.chat.enums.ChannelType;
import com.sparkuniverse.toolbox.chat.model.Channel;
import com.sparkuniverse.toolbox.chat.model.Message;
import com.sparkuniverse.toolbox.chat.model.MessageContent;
import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.connectionmanager.common.packet.chat.*;
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket;
import gg.essential.gui.elementa.state.v2.ListKt;
import gg.essential.gui.elementa.state.v2.MutableState;
import gg.essential.gui.elementa.state.v2.State;
import gg.essential.gui.EssentialPalette;
import gg.essential.gui.elementa.state.v2.StateByKt;
import gg.essential.gui.elementa.state.v2.collections.MutableTrackedList;
import gg.essential.gui.friends.message.ReportMessageConfirmationModal;
import gg.essential.gui.friends.message.v2.ClientMessageKt;
import gg.essential.gui.friends.message.v2.MessageRef;
import gg.essential.gui.friends.state.IMessengerManager;
import gg.essential.gui.notification.ExtensionsKt;
import gg.essential.gui.friends.state.MessengerStateManagerImpl;
import gg.essential.gui.notification.Notifications;
import gg.essential.gui.overlay.ModalManager;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.NetworkedManager;
import gg.essential.network.connectionmanager.StateCallbackManager;
import gg.essential.network.connectionmanager.cosmetics.AssetLoader;
import gg.essential.network.connectionmanager.handler.chat.ChatChannelMemberAddPacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ChatChannelMemberRemovePacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ChatChannelMessageDeletePacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ChatChannelUpdatePacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ServerChatChannelAddPacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ServerChatChannelClearPacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ServerChatChannelMessagePacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ServerChatChannelMessageReportReasonsPacketHandler;
import gg.essential.network.connectionmanager.handler.chat.ServerChatChannelRemovePacketHandler;
import gg.essential.network.connectionmanager.queue.PacketQueue;
import gg.essential.network.connectionmanager.queue.SequentialPacketQueue;
import gg.essential.util.CachedAvatarImage;
import gg.essential.util.StringsKt;
import gg.essential.util.USession;
import gg.essential.util.UUIDUtil;
import gg.essential.util.UuidNameLookup;
import kotlin.Pair;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static gg.essential.util.ChannelExtensionsKt.isAnnouncement;

public class ChatManager extends StateCallbackManager<IMessengerManager> implements NetworkedManager {

    @NotNull
    private final Map<Long, Channel> channels = Maps.newConcurrentMap();

    @NotNull
    private final Map<Long, ConcurrentMap<Long, Message>> channelMessages = Maps.newConcurrentMap();

    @NotNull
    private final MutableState<MutableTrackedList<Long>> channelsWithMessagesListState = ListKt.mutableListStateOf();

    @NotNull
    private final Map<String, Map<String, String>> reportReasons = Maps.newConcurrentMap();

    @NotNull
    private final Set<Long> announcementChannelIds = Sets.newConcurrentHashSet();

    @NotNull
    private final ConnectionManager connectionManager;

    @NotNull
    public final MessengerStateManagerImpl messengerStateManager = new MessengerStateManagerImpl(this);

    @NotNull
    private final State<Integer> unreadMessageCount = StateByKt.stateBy(stateByScope -> {
        int unreadMessages = 0;
        for (Long channelId : stateByScope.invoke(channelsWithMessagesListState)) {
            unreadMessages += stateByScope.invoke(messengerStateManager.getNumUnread(channelId));
        }
        return unreadMessages;
    });

    @NotNull
    private final PacketQueue mutedStateUpdateQueue, sendMessageQueue;

    /**
     * A map of channel ids to ID of the oldest message that is needed for that channel
     */
    @NotNull
    private final Map<Long, EagerMessageResolver> channelEagerMessageResolverMap = new HashMap<>();

    @NotNull
    private final ServerChatChannelMessagePacketHandler serverChatChannelMessagePacketHandler = new ServerChatChannelMessagePacketHandler(this);

    public ChatManager(@NotNull final ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;

        this.mutedStateUpdateQueue = new SequentialPacketQueue.Builder(connectionManager)
            .onTimeoutRetransmit()
            .create();
        this.sendMessageQueue = new SequentialPacketQueue.Builder(connectionManager)
                .onTimeoutRetransmit()
                .create();

        connectionManager.registerPacketHandler(ChatChannelMemberAddPacket.class, new ChatChannelMemberAddPacketHandler(this));
        connectionManager.registerPacketHandler(ChatChannelMessageDeletePacket.class, new ChatChannelMessageDeletePacketHandler(this));
        connectionManager.registerPacketHandler(ChatChannelMemberRemovePacket.class, new ChatChannelMemberRemovePacketHandler(this));
        connectionManager.registerPacketHandler(ChatChannelUpdatePacket.class, new ChatChannelUpdatePacketHandler(this));
        connectionManager.registerPacketHandler(ServerChatChannelAddPacket.class, new ServerChatChannelAddPacketHandler(this));
        connectionManager.registerPacketHandler(ServerChatChannelClearPacket.class, new ServerChatChannelClearPacketHandler(this));
        connectionManager.registerPacketHandler(ServerChatChannelMessagePacket.class, serverChatChannelMessagePacketHandler);
        connectionManager.registerPacketHandler(ServerChatChannelRemovePacket.class, new ServerChatChannelRemovePacketHandler(this));
        connectionManager.registerPacketHandler(ServerChatChannelMessageReportReasonsPacket.class, new ServerChatChannelMessageReportReasonsPacketHandler(this));

        connectionManager.registerPacketHandler(ChatUnfilteredContentSettingPacket.class, packet -> {
            EssentialConfig.INSTANCE.getChatFilterWithSource().set(new Pair<>(!packet.getShowUnfilteredContent(), false));
        });
    }

    @NotNull
    public Map<Long, Channel> getChannels() {
        return this.channels;
    }

    @NotNull
    public Map<String, Map<String, String>> getReportReasons() {
        return this.reportReasons;
    }

    public void setReportReasons(@Nullable final Map<String, Map<String, String>> reportReasons) {
        this.reportReasons.clear();

        if (reportReasons == null) {
            return;
        }

        this.reportReasons.putAll(reportReasons);
    }

    @NotNull
    public Optional<Channel> getChannel(@NotNull final Long id) {
        return Optional.ofNullable(this.channels.get(id));
    }

    public long getPrimaryAnnouncementChannelId() {
        Channel channel = this.getPrimaryAnnouncementChannel();
        return channel != null ? channel.getId() : 0;
    }

    @Nullable
    public Channel getPrimaryAnnouncementChannel() {
        for (Channel channel : this.channels.values()) {
            if (channel.getType() == ChannelType.ANNOUNCEMENT) {
                return channel;
            }
        }
        return null;
    }

    public long mergeAnnouncementChannel(long id) {
        return (announcementChannelIds.contains(id) && getPrimaryAnnouncementChannelId() != 0) ? getPrimaryAnnouncementChannelId() : id;
    }

    @NotNull
    public Set<Long> getAnnouncementChannelIds() {
        return this.announcementChannelIds;
    }

    @NotNull
    public Map<String, String> getReportReasons(@NotNull final String preferredLocale) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        for (@NotNull final Map.Entry<String, Map<String, String>> entry : this.reportReasons.entrySet()) {
            final Map<String, String> values = entry.getValue();

            String value = values.get(preferredLocale);
            if (value == null) {
                value = values.get("en_US");
            }

            builder.put(entry.getKey(), value);
        }

        return builder.build();
    }

    public void addChannels(@NotNull final Iterable<Channel> channels) {
        for (@NotNull final Channel channel : channels) {
            this.addChannel(channel);
        }
    }

    public void addChannel(@NotNull final Channel channel) {
        this.channels.put(channel.getId(), channel);

        for (UUID member : channel.getMembers()) {
            CachedAvatarImage.create(member, AssetLoader.Priority.Background);
        }
        retrieveRecentMessageHistory(channel.getId(), 10, null); // Prefetch a few messages to look for unread messages
        if (isAnnouncement(channel)) {
            for (long announcementChannelId : announcementChannelIds) {
                if (announcementChannelId != channel.getId()) {
                    retrieveRecentMessageHistory(announcementChannelId, 100, null);
                }
            }

            announcementChannelIds.add(channel.getId());
        }
        for (IMessengerManager iMessengerManager : getCallbacks()) {
            iMessengerManager.newChannel(channel);
        }
    }

    public void setChannelInfo(final long channelId, @Nullable String channelName, @Nullable String channelTopic) {
        final Optional<Channel> channelOptional = this.getChannel(channelId);

        if (!channelOptional.isPresent()) {
            return;
        }

        final Channel channel = channelOptional.get();

        if (channelName != null) {
            channel.setName(channelName);
        }

        if (channelTopic != null) {
            channel.setTopic(channelTopic);
        }

        for (IMessengerManager iMessengerManager : getCallbacks()) {
            iMessengerManager.channelUpdated(channel);
        }
    }

    public void removeChannels(final long[] channelIds) {
        for (final long channelId : channelIds) {
            this.removeChannel(channelId);
        }
    }

    public void removeChannels(@NotNull final Iterable<Long> channelIds) {
        for (final long channelId : channelIds) {
            this.removeChannel(channelId);
        }
    }

    @Nullable
    public Channel removeChannel(final long id) {
        Channel channel = this.channels.remove(id);
        ConcurrentMap<Long, Message> removed = this.channelMessages.remove(id);
        updateChannelListState();
        if (channel != null) {
            for (IMessengerManager iMessengerManager : getCallbacks()) {
                iMessengerManager.channelDeleted(channel);
            }
        }
        this.channelEagerMessageResolverMap.remove(id);
        announcementChannelIds.remove(id);
        return channel;
    }

    public void clearChannels() {
        this.channels.clear();
        this.channelMessages.clear();
        this.updateChannelListState();
        this.announcementChannelIds.clear();
        this.reportReasons.clear();
        this.channelEagerMessageResolverMap.clear();

        for (IMessengerManager callback : getCallbacks()) {
            callback.reset();
        }
    }

    @Override
    public void onConnected() {
        this.mutedStateUpdateQueue.reset();

        resetState();
    }

    @Override
    public void resetState() {
        this.clearChannels();
    }

    @Nullable
    public Map<Long, Message> getMessages(final long channelId) {
        ConcurrentMap<Long, Message> messageMap = this.channelMessages.get(channelId);
        return messageMap != null ? Collections.unmodifiableMap(messageMap) : null;
    }

    /**
     * Upserts this message to the supplied channel.
     *
     * @return true if the message was updated, false if it was inserted or an error occurred
     */
    public boolean upsertMessageToChannel(final long channelId, @NotNull final Message message, final boolean isFromHistoryRequest) {
        final Channel channel = getChannel(channelId).orElse(null);
        if (channel == null) {
            Essential.logger.error("Received message: " + message + " for channel that does not exist");
            return false;
        }

        ConcurrentMap<Long, Message> map = channelMessages.get(channelId);
        if (map == null) {
            map = Maps.newConcurrentMap();
            channelMessages.put(channelId, map);
            this.updateChannelListState();
        }
        boolean previousMessageExisted = map.put(message.getId(), message) != null; // Message was edited if it already existed

        for (IMessengerManager iMessengerManager : getCallbacks()) {
            iMessengerManager.messageReceived(channel, message);
        }

        EagerMessageResolver eagerMessageResolver = channelEagerMessageResolverMap.get(channelId);
        if (eagerMessageResolver != null) {
            eagerMessageResolver.messageReceived(message);
        }

        if (!isFromHistoryRequest && !previousMessageExisted) {
            if ((channel.getLastReadMessageId() == null || channel.getLastReadMessageId() < message.getId())
                    && !message.getSender().equals(USession.Companion.activeNow().getUuid())
            ) {
                changeUnreadMessageCount(channel, 1);
            }
        }

        return previousMessageExisted;
    }

    public void sendMessage(final long channelId, @NotNull final String messageContent) {
        this.sendMessage(channelId, messageContent, null);
    }

    public void sendMessage(
        final long channelId,
        @NotNull final String messageContent,
        @Nullable final Long replyToMessageId,
        @Nullable final Consumer<Optional<Packet>> callback
    ) {
        sendMessageQueue.enqueue(new ClientChatChannelMessageCreatePacket(channelId, messageContent, replyToMessageId), callback);
    }

    public void sendMessage(
        final long channelId,
        @NotNull final String messageContent,
        @Nullable final Consumer<Optional<Packet>> callback
    ) {
        this.sendMessage(channelId, messageContent, null, callback);
    }

    public void editMessage(
        final long channelId,
        final long messageId,
        @NotNull final String messageContent,
        @Nullable final Consumer<Boolean> callback
    ) {
        Message original = Objects.requireNonNull(getMessageById(channelId, messageId));
        assert original.getContent().getType() == MessageContent.MessageContentType.PLAIN;

        sendMessageQueue.enqueue(new ClientChatChannelMessageUpdatePacket(channelId, messageId, messageContent), maybePacket -> {
            Packet packet = maybePacket.orElse(null);
            if (packet instanceof ResponseActionPacket && ((ResponseActionPacket) packet).isSuccessful()) {
                Message message = getMessageById(channelId, messageId);
                if (message != null) {
                    upsertMessageToChannel(channelId, new Message(
                        message.getId(),
                        message.getChannelId(),
                        message.getSender(),
                        new MessageContent.Plain(messageContent, null),
                        message.getReplyTargetId(),
                        message.getLastEditTime(),
                        message.getCreatedAt()
                    ), false);
                }
                if (callback != null) callback.accept(true);
            } else {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public void removeMessage(final long channelId, final long messageId) {
        ConcurrentMap<Long, Message> channelMessages = this.channelMessages.get(channelId);
        if (channelMessages != null) {
            Message message = channelMessages.remove(messageId);
            if (message != null) {
                Channel channel = this.channels.get(channelId);
                if ((channel.getLastReadMessageId() == null || channel.getLastReadMessageId() < message.getId())
                        && !message.getSender().equals(USession.Companion.activeNow().getUuid())
                ) {
                    changeUnreadMessageCount(channel, -1);
                }
                for (IMessengerManager iMessengerManager : getCallbacks()) {
                    iMessengerManager.messageDeleted(message);
                }
            }
        }
    }

    public void deleteMessage(final long channelId, final long messageId) {
        this.deleteMessage(channelId, messageId, null);
        this.removeMessage(channelId, messageId);
    }

    public void deleteMessage(
        final long channelId, final long messageId, @Nullable final Consumer<Optional<Packet>> callback
    ) {
        this.connectionManager.send(new ChatChannelMessageDeletePacket(channelId, messageId), callback);
    }

    public void createGroupDM(@NotNull UUID[] members, @Nullable String groupName, @Nullable Consumer<Optional<Channel>> callback) {
        for (UUID member : members) {
            if (!connectionManager.getRelationshipManager().isFriend(member) && member != UUIDUtil.getClientUUID()) {
                if (callback != null) {
                    callback.accept(Optional.empty());
                }
                return;
            }
        }

        if (groupName == null) {
            groupName = "New Group";
        } else if (groupName.length() > 64) {
            groupName = groupName.substring(0, 63);
        }

        connectionManager.send(new ClientChatChannelCreatePacket(ChannelType.GROUP_DIRECT_MESSAGE, groupName, members), response -> {
            Optional<Channel> channel;
            if (!response.isPresent()) {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "Failed to create group.\nPlease try again.");
                channel = Optional.empty();
            } else if (response.get() instanceof ServerChatChannelAddPacket) {
                channel = ((ServerChatChannelAddPacket) response.get()).getChannels().stream().findFirst();
            } else {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "Failed to create group.\nPlease try again.");
                channel = Optional.empty();
            }
            if (callback != null) {
                callback.accept(channel);
            }
        });
    }

    public void createDM(@NotNull UUID otherParty, @Nullable String playerName, @Nullable Consumer<Optional<Packet>> callback) {
        if (!connectionManager.getRelationshipManager().isFriend(otherParty)) {
            return;
        }

        connectionManager.send(new ClientChatChannelCreatePacket(ChannelType.DIRECT_MESSAGE, playerName, new UUID[]{otherParty}), callback);
    }

    public void updateChannelInformation(final long channelId, @Nullable String channelName, @Nullable String channelTopic) {
        Channel currentChannelInfo = channels.get(channelId);
        if (currentChannelInfo == null) {
            return;
        }

        if (channelName == null) {
            channelName = currentChannelInfo.getName();
        } else if (channelName.length() > 64) {
            channelName = channelName.substring(0, 63);
        }

        if (channelTopic == null) {
            channelTopic = currentChannelInfo.getTopic();
        }

        if (channelName.equals(currentChannelInfo.getName()) && (channelTopic == null || channelTopic.equals(currentChannelInfo.getTopic()))) {
            return;
        }

        ChatChannelUpdatePacket update = new ChatChannelUpdatePacket(channelId, channelName, channelTopic);
        connectionManager.send(update, responseOptional -> {
            Packet packet = responseOptional.orElse(null);
            if (!(packet instanceof ResponseActionPacket) || !((ResponseActionPacket) packet).isSuccessful()) {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "An unexpected error occurred. Please try again.");
                return;
            }
            setChannelInfo(channelId, update.getName(), update.getTopic());
        });
    }

    public void addPlayersToChannel(final long channelId, @NotNull final UUID[] players) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return;
        }
        if (channel.getType() != ChannelType.GROUP_DIRECT_MESSAGE) {
            return;
        }
        if (channel.getType().hasUserLimit() && channel.getMembers().size() + 1 > channel.getType().getBaseUserLimit()) {
            ExtensionsKt.warning(Notifications.INSTANCE, "Group is full", "");
            return;
        }

        connectionManager.send(new ChatChannelMemberAddPacket(channelId, players), maybePacket -> {
            Packet packet = maybePacket.orElse(null);
            if (packet instanceof ServerChannelMemberActionResponsePacket) {
                for (Map.Entry<UUID, Boolean> entry : ((ServerChannelMemberActionResponsePacket) packet).getResults().entrySet()) {
                    UUID uuid = entry.getKey();
                    if (entry.getValue()) {
                        channel.getMembers().add(uuid);
                    } else {
                        UUIDUtil.getName(uuid).thenAccept(name -> {
                            ExtensionsKt.error(Notifications.INSTANCE, "Error", "",
                                () -> Unit.INSTANCE, () -> Unit.INSTANCE, builder -> {
                                    ExtensionsKt.markdownBody(builder,
                                        "Failed to add " + StringsKt.colored(name, EssentialPalette.TEXT_HIGHLIGHT) + " to group. Please try again."
                                    );
                                    return Unit.INSTANCE;
                                });
                        });
                    }
                }
                for (IMessengerManager iMessengerManager : getCallbacks()) {
                    iMessengerManager.channelUpdated(channel);
                }
            } else {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "Failed to add player to group. Please try again.");
            }
        });
    }

    public void removePlayerFromChannel(final long channelId, @NotNull final UUID player) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return;
        }
        if (channel.getType() != ChannelType.GROUP_DIRECT_MESSAGE) {
            return;
        }
        if (!channel.getMembers().contains(player)) {
            return;
        }

        connectionManager.send(new ChatChannelMemberRemovePacket(channelId, new UUID[]{player}), maybePacket -> {
            Packet packet = maybePacket.orElse(null);
            if (packet instanceof ServerChannelMemberActionResponsePacket) {
                for (Map.Entry<UUID, Boolean> entry : ((ServerChannelMemberActionResponsePacket) packet).getResults().entrySet()) {
                    UUID uuid = entry.getKey();
                    if (entry.getValue()) {
                        if (uuid.equals(UUIDUtil.getClientUUID())) {
                            this.removeChannel(channelId);
                            return;
                        }
                        channel.getMembers().remove(uuid);
                    } else {
                        UUIDUtil.getName(uuid).thenAccept(name -> {
                            ExtensionsKt.error(Notifications.INSTANCE, "Error", "",
                                () -> Unit.INSTANCE, () -> Unit.INSTANCE, builder -> {
                                    ExtensionsKt.markdownBody(builder,
                                        "Failed to remove " + StringsKt.colored(name, EssentialPalette.TEXT_HIGHLIGHT) + " from group. Please try again."
                                    );
                                    return Unit.INSTANCE;
                                });
                        });
                    }
                }
                for (IMessengerManager iMessengerManager : getCallbacks()) {
                    iMessengerManager.channelUpdated(channel);
                }
            } else {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "Failed to remove player from group. Please try again.");
            }
        });
    }

    public void retrieveMessageHistory(final long channelId, @Nullable Long before, @Nullable Long after, @Nullable Consumer<Optional<Packet>> callback) {
        retrieveMessageHistory(channelId, before, after, 50, callback);
    }

    public void retrieveMessageHistory(final long channelId, Long before, Long after, final int messageLimit, @Nullable Consumer<Optional<Packet>> callback) {
        if (!channels.containsKey(channelId)) {
            Essential.logger.error("Attempted to request message history for a channel that is not locally cached!");
            return;
        }

        connectionManager.send(new ClientChatChannelMessagesRetrievePacket(channelId, before, after, messageLimit), response -> {

            Packet packet = response.orElse(null);
            if (packet instanceof ServerChatChannelMessagePacket) {
                boolean isEmpty = ((ServerChatChannelMessagePacket) packet).getMessages().length == 0;
                if (before == null && after == null && isEmpty) {
                    // This channel has not seen any messages yet, we can still cache that very fact though.
                    this.channelMessages.putIfAbsent(channelId, Maps.newConcurrentMap());
                    this.updateChannelListState();
                }
            }

            if (callback != null) {
                callback.accept(response);
            }
        });
    }

    public void retrieveRecentMessageHistory(final long channelId, @Nullable Consumer<Optional<Packet>> callback) {
        retrieveRecentMessageHistory(channelId, 50, callback);
    }

    public void retrieveRecentMessageHistory(final long channelId, final int messageLimit, @Nullable Consumer<Optional<Packet>> callback) {
        retrieveMessageHistory(channelId, null, null, messageLimit, callback);
    }

    public boolean isMuted(@NotNull final Channel channel) {
        return channel.isMuted();
    }

    public void updateMutedState(@NotNull final Channel channel, final boolean muted) {
        mutedStateUpdateQueue.enqueue(new ClientChatChannelMutePacket(channel.getId(), muted), response -> {
            Packet packet = response.orElse(null);
            boolean success = packet instanceof ResponseActionPacket && ((ResponseActionPacket) packet).isSuccessful();
            if (success) {
                channel.setMuted(muted);
                if (muted) {
                    CompletableFuture<String> nameFuture;
                    if (channel.getType() == ChannelType.DIRECT_MESSAGE) {
                        nameFuture = channel.getMembers().stream().filter(uuid -> !uuid.equals(UUIDUtil.getClientUUID())).findFirst().map(UuidNameLookup::getName).orElse(CompletableFuture.completedFuture("Unknown"));
                    } else {
                        nameFuture = CompletableFuture.completedFuture(channel.getName());
                    }
                    nameFuture.whenCompleteAsync((name, throwable) ->
                        Notifications.INSTANCE.push("", "", notificationBuilder -> {
                            ExtensionsKt.iconAndMarkdownBody(notificationBuilder,
                                    EssentialPalette.MUTE_8X9.create(),
                                    StringsKt.colored(name, EssentialPalette.TEXT_HIGHLIGHT) + " has been muted"
                            );
                            return Unit.INSTANCE;
                        })
                    );
                }
            } else {
                ExtensionsKt.error(Notifications.INSTANCE, "Error", "Failed to mute channel.\nPlease try again.");
            }

            for (IMessengerManager iMessengerManager : getCallbacks()) {
                iMessengerManager.channelUpdated(channel);
            }
        });
    }

    public void fileReport(ModalManager modalManager, long channelId, long messageId, UUID sender, String reason) {
        mutedStateUpdateQueue.enqueue(new ClientChatChannelMessageReportPacket(channelId, messageId, reason), response -> {
            Packet packet = response.orElse(null);
            if (packet instanceof ServerChatChannelMessageReportPacket) {
                modalManager.queueModal(new ReportMessageConfirmationModal(modalManager, sender, false));
            } else {
                ExtensionsKt.error(Notifications.INSTANCE, "Report Failed", "Failed to report player.\nPlease try again.");
            }
        });
    }


    private void updateChannelListState() {
        ListKt.setAll(channelsWithMessagesListState, new ArrayList<>(channelMessages.keySet()));
    }

    public void setLastReadMessage(final long channelId, @Nullable final Long lastReadMessageId) {
        Channel channel = getChannel(channelId).orElseThrow(() -> new IllegalStateException("Can not find channel for message id: " + lastReadMessageId));
        if (Objects.equals(channel.getLastReadMessageId(), lastReadMessageId)) {
            return;
        }
        channel.setLastReadMessageId(lastReadMessageId);
        // Update unread message count in channel
        Map<Long, Message> messages = getMessages(channel.getId());
        if (messages != null) {
            Stream<Message> otherUserMessages = messages.values().stream().filter(m -> !m.getSender().equals(USession.Companion.activeNow().getUuid()));
            if (lastReadMessageId == null) {
                channel.setUnreadMessages((int) otherUserMessages.count());
            } else {
                channel.setUnreadMessages((int) otherUserMessages.filter(m -> lastReadMessageId < m.getId()).count());
            }
        }
        for (IMessengerManager manager : getCallbacks()) {
            manager.channelUpdated(channel);
        }
        this.connectionManager.call(new ClientChatChannelReadStatePacket(channel.getId(), lastReadMessageId)).fireAndForget();
    }

    public void changeUnreadMessageCount(Channel channel, int delta) {
        // Infra does not send anything over 100, so we will treat 100 as infinity which means adding or subtracting doesn't change anything
        if (channel.getUnreadMessages() >= 100) {
            return;
        }
        channel.setUnreadMessages(channel.getUnreadMessages() + delta);
        for (IMessengerManager manager : getCallbacks()) {
            manager.channelUpdated(channel);
        }
    }

    public State<Integer> getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public void membersAdded(long channelId, UUID[] members) {
        final Optional<Channel> channelOptional = getChannel(channelId);
        if (!channelOptional.isPresent()) {
            return;
        }
        final Channel channel = channelOptional.get();
        channel.getMembers().addAll(Arrays.asList(members));
        for (IMessengerManager manager : getCallbacks()) {
            manager.channelUpdated(channel);
        }
    }

    public void membersRemoved(long channelId, UUID[] members) {
        final Optional<Channel> channelOptional = getChannel(channelId);
        if (!channelOptional.isPresent()) {
            return;
        }
        final Channel channel = channelOptional.get();
        Arrays.asList(members).forEach(channel.getMembers()::remove);
        for (IMessengerManager manager : getCallbacks()) {
            manager.channelUpdated(channel);
        }
    }

    public @Nullable Message getMessageById(long channelId, long messageId) {
        ConcurrentMap<Long, Message> messages = channelMessages.get(channelId);
        if (messages == null) {
            return null;
        }
        return messages.get(messageId);
    }

    /**
     * Returns the ID of the oldest loaded message in a channel or null if no messages are available
     */
    private @Nullable Long getOldestLoadedMessageId(long channel) {
        Map<Long, Message> messages = getMessages(channel);
        if (messages == null) {
            return null;
        }
        return messages.values().stream().min(Comparator.comparingLong(Message::getId)).map(Message::getId).orElse(null);
    }

    /**
     * Requests more messages from the channel until it finds messageId
     *
     * @param ref The messageref to load
     */
    public void retrieveChannelHistoryUntil(MessageRef ref) {
        if (ref.getMessageId() == -1) {
            throw new IllegalArgumentException("Cannot request channel history for a deleted message");
        }

        if (ref.isInitialized()) {
            return;
        }

        Message messageById = getMessageById(ref.getChannelId(), ref.getMessageId());
        if (messageById != null) {
            ref.supplyValue(ClientMessageKt.infraInstanceToClient(messageById));
            return;
        }

        // Request messages until we find the target one
        channelEagerMessageResolverMap.computeIfAbsent(ref.getChannelId(), EagerMessageResolver::new).resolve(ref);
    }

    /**
     * Utility class retrieve all the messages in a channel until a certain target message is loaded
     */
    private class EagerMessageResolver {

        private final long channelId;
        private final Map<Long, List<MessageRef>> messageRefMap = new HashMap<>();
        private boolean activelyResolving = false;
        private boolean retrievedAllMessages = false;
        private boolean messagesRequestedAtLeastOnce = false;
        private EagerMessageResolver(long channelId) {
            this.channelId = channelId;
        }

        /**
         * Update the message this resolver must load until it reaches
         */
        public void resolve(MessageRef ref) {
            messageRefMap.computeIfAbsent(ref.getMessageId(), aLong -> new ArrayList<>()).add(ref);
            if (!activelyResolving) {
                activelyResolving = true;
                resolve();
            }
        }

        public void messageReceived(Message message) {
            List<MessageRef> messageRefs = messageRefMap.remove(message.getId());
            if (messageRefs != null) {
                for (MessageRef messageRef : messageRefs) {
                    messageRef.supplyValue(ClientMessageKt.infraInstanceToClient(message));
                }
            }
        }

        /**
         * Requests messages from the CM until the target message indicated by oldestRequiredMessageId
         * is retrieved or no more messages are available in the channel.
         */
        private void resolve() {
            if (retrievedAllMessages || channelEagerMessageResolverMap.get(channelId) != this) {
                activelyResolving = false;
                return;
            }
            Long oldestLoadedMessageId = getOldestLoadedMessageId(channelId);

            // If there are no messages loaded by this point then there are no messages in the channel
            if (oldestLoadedMessageId == null && messagesRequestedAtLeastOnce) {
                activelyResolving = false;
                retrievedAllMessages = true;
                return;
            }

            messagesRequestedAtLeastOnce = true;

            retrieveMessageHistory(channelId, oldestLoadedMessageId, null, 50, packetOptional -> {
                Packet packet = packetOptional.orElse(null);

                // CM gave an unexpected response, abort loading
                if (!(packet instanceof ServerChatChannelMessagePacket)) {
                    activelyResolving = false;
                    return;
                }

                @NotNull Message[] messages = ((ServerChatChannelMessagePacket) packet).getMessages();

                // All messages in the channel are retrieved
                if (messages.length == 0) {
                    activelyResolving = false;
                    retrievedAllMessages = true;
                    return;
                }

                // At least one message is not yet loaded, load the next batch
                // Callback to messageReceived will trigger and clear messages that resolved
                // before this packet handler is called
                if (!messageRefMap.isEmpty()) {
                    resolve();
                }
            });
        }
    }
}
