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
package gg.essential.gui.friends.message.v2

import com.sparkuniverse.toolbox.chat.model.MessageContent.Plain
import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.packet.chat.ServerChatChannelMessagePacket
import gg.essential.connectionmanager.common.packet.chat.ServerChatChannelMessageRejectedPacket
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.*
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.add
import gg.essential.gui.elementa.state.v2.combinators.and
import gg.essential.gui.elementa.state.v2.combinators.zip
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.elementa.state.v2.removeAll
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toList
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.elementa.state.v2.toSet
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.friends.message.MessageInput
import gg.essential.gui.friends.message.MessageScreen
import gg.essential.gui.friends.message.MessageTitleBar
import gg.essential.gui.friends.message.SocialMenuActions
import gg.essential.gui.friends.previews.ChannelPreview
import gg.essential.gui.friends.state.SocialStates
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.layout
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.util.*
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit


class ReplyableMessageScreen(
    override val preview: ChannelPreview,
    private val socialStates: SocialStates,
    socialMenuActions: SocialMenuActions,
    titleBar: UIComponent,
    rightDivider: UIComponent,
    active: State<Boolean>,
    val isScreenOpen: State<Boolean>,
) : MessageScreen() {

    private val refHolder = ReferenceHolderImpl()

    private val standardBar by MessageTitleBar(this, socialStates, socialMenuActions).bindParent(titleBar, active)

    private val scroller by ScrollComponent(
        verticalScrollOpposite = true,
        scrollAcceleration = 2f
    ).constrain {
        y = SiblingConstraint()
        width = 100.percent
        height = FillConstraint()
    }

    private val content by UIContainer().constrain {
        y = 0.pixels(alignOpposite = true)
        width = 100.percent
        height = ChildBasedSizeConstraint()
    } childOf scroller

    private val showEmptyText = mutableStateOf(true).apply {
        content.children.addObserver { _, _ ->
            set(content.children.none {
                it is MessageWrapper
            })
        }
    }

    private var messageInput: MessageInput? = null

    private val topSpacer by Spacer(height = 6.pixels) childOf content

    private val bottomSpacer by Spacer(height = 7.pixels) childOf content

    private val emptyText by EssentialUIText("Send a message to begin chatting!").constrain {
        x = CenterConstraint()
        y = 10.pixels
        color = EssentialPalette.TEXT.toConstraint()
    }.bindParent(scroller.children[0], showEmptyText)

    private val scrollCleanup: () -> Unit

    private var lastRequest = 0L
    private val channel = preview.channel
    private var receivedAllMessages = false
    private val sendQueue = mutableListStateOf<ClientMessage>()

    private val baseMessageListState = socialStates.messages.getMessageListState(preview.channel.id)

    private val messageListState = memo {
        val list = baseMessageListState().toMutableList()
        val latestMessageTimestamp = list.maxOfOrNull { if (it.sent) it.sendTime.toEpochMilli() else 0 } ?: 0
        for (messageWithIndex in sendQueue().withIndex()) {
            val message = messageWithIndex.value
            if (list.none { it.id == message.id }) {
                list.add(
                    if (message.sendState == SendState.Sending) {
                        message.copy(createdAt = kotlin.math.max(latestMessageTimestamp, message.sendTime.toEpochMilli()))
                    } else {
                        message
                    }
                )
            }
        }
        list
    }.toListState()

    private var addedUnreadDivider = false
    private var markedManuallyUnread = false

    private val contentSortComparator = compareBy<UIComponent> {
        // Sort by spacer vs content
        when (it) {
            topSpacer -> 0
            bottomSpacer -> 2
            else -> 1
        }
    }.thenComparing { uiComponent: UIComponent ->
        // Then sort by send time
        when (val component = uiComponent as MessengerElement) {
            is MessageWrapper -> component.message.sendTime
            is Divider -> component.timeStamp
        }
    }.thenComparing { uiComponent: UIComponent ->
        // Then sort by type
        when (uiComponent as MessengerElement) {
            is MessageWrapper -> 1
            is Divider -> 0
        }
    }.thenComparing { uiComponent: UIComponent ->
        // Then sort by divider type
        when (uiComponent) {
            is UnreadDivider -> 0
            is DateDivider -> 1
            else -> 2
        }
    }

    init {
        if (!preview.channel.isAnnouncement()) {
            messageInput = MessageInput(
                channel,
                isScreenOpen,
                socialStates,
                preview.titleState,
                replyingTo,
                editingMessage,
                ::scrollToMessage,
                ::sendMessage,
                ::editMessage,
            )
        }

        layout {
            scroller()
            if_(preview.isChannelSuspendedState) {
                messageInputSuspended(preview.titleState, Modifier.alignVertical(Alignment.End))
            } `else` {
                messageInput?.invoke()
            }
        }

        createScrollbarRelativeTo(
            active.toV1(this),
            xPositionAndWidth = rightDivider,
            parent = rightDivider,
            yPositionAndHeight = scroller,
            initializeToBottom = true,
        ).let { (component, cleanup) ->
            scroller.setVerticalScrollBarComponent(component, hideWhenUseless = true)
            scrollCleanup = cleanup
        }

        if (messageInput != null) {
            val isPickingScreenshots = messageInput!!.screenshotAttachmentManager.isPickingScreenshots
            scroller.onLeftClick {
                if (isPickingScreenshots.get()) {
                    isPickingScreenshots.set(false)
                }
            }.bindEffect(FadeEffect(EssentialPalette.GUI_BACKGROUND, 0.4f), isPickingScreenshots)
        }

        val messageDates = messageListState.mapEach {
            val systemDefault = ZoneId.systemDefault()
            it.sendTime.atZone(systemDefault).toLocalDate().atStartOfDay(systemDefault)
        }.toSet().toList()

        content.layout {
            forEach(messageDates) {
                DateDividerImpl(it.toInstant())()
            }
            forEach(messageListState) { message ->
                MessageWrapperImpl(message, this@ReplyableMessageScreen, socialStates.messages).apply {
                    parseComponents(message, this).forEach { addComponent(it) }
                }()
            }
        }

        // Do the percent state manually as the default is not useful for a scroller which dynamically adds content
        // This one fades in the gradient over 100 pixels scrolled.
        // Could be made into a more generic solution, but to do the same trick for the opposite side,
        // one must have the actual max offset of the scroller, which isn't accessible and duplicating the calculation code seems like a bad idea.
        val percentState = mutableStateOf(0f)
        val hidden = mutableStateOf(true)

        scroller.addScrollAdjustEvent(false) { _, percentageOfParent ->
            percentState.set(1 - (scroller.verticalOffset / 100).coerceIn(0f, 1f))
            hidden.set(percentageOfParent == 1f)
        }

        scroller.createGradient(false, 30.pixels, percentState = percentState, heightState = scroller.getHeightState(), hidden = hidden)

        messageListState.onSetValue(this) { _ ->
            content.children.sortWith(contentSortComparator)

            // Check if we need to add the unread divider based on the new messages received
            insertUnreadDivider()

            // Recalculate visibility of timestamps when messages are mutated
            recalculateTimestampVisibility()
        }

        val channelMessages = socialStates.messages.getMessagesRaw(channel.id)

        if (channelMessages == null) { // Even for announcements this will still be true first round since personal channel isn't requested until after type ANNOUNCEMENT
            socialStates.messages.retrieveMessageHistoryRaw(channel.id)
        } else if (channelMessages.size < 50) {
            socialStates.messages.retrieveMessageHistoryRaw(
                channel.id,
                channelMessages.values.minByOrNull { it.id }?.id,
                null,
                50 - channelMessages.size,
                null
            )
        }

        (replyingTo.zip(editingMessage)).onChange(this) {
            // If the user is scrolled all the way down, no change to scrolling is needed
            if (scroller.verticalOffset == 0f) {
                return@onChange
            }

            // If the user is not scrolled all the way down, we need to adjust the scroll to avoid moving content
            val scrollAdjust = if (it.first == null && it.second == null) {
                -14f
            } else {
                14f
            }
            scroller.scrollTo(verticalOffset = scroller.verticalOffset + scrollAdjust, smoothScroll = false)
        }

        scroller.addScrollAdjustEvent(isHorizontal = false) { scrollPercentage, _ ->
            val time = System.currentTimeMillis()
            if (scrollPercentage < -0.9 && time - lastRequest > 1000L) {
                lastRequest = time
                requestMoreMessages()
            }
        }

        content.children.sortWith(contentSortComparator)
        recalculateTimestampVisibility()
        insertUnreadDivider()
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        if (!markedManuallyUnread) {
            findAndMarkLatestMessageAsRead()
        }
    }

    /**
     * Inserts a new message divider at the transition from read to unread messages
     * if a new line divider is not already present.
     */
    private fun insertUnreadDivider() {
        if (addedUnreadDivider) {
            return
        }

        fun clearDateDividerUnreadStates() {
            content.findChildrenOfType<DateDivider>(true).forEach { it.unread.set(false) }
        }

        fun insertDividerAtInstant(instant: Instant) {
            clearDateDividerUnreadStates()
            val unreadDivider by UnreadDividerImpl(instant) childOf content
            content.children.sortWith(contentSortComparator)
            addedUnreadDivider = true
        }

        fun insertDividerAt(clientMessage: ClientMessage) {

            // If the message is right after a date divider, set the date divider as unread instead of showing
            // an additional "NEW" line before the date divider.
            content.children.indexOfFirst {
                it is MessageWrapper && it.message == clientMessage
            }.let { index ->
                val previousSibling = content.children.getOrNull(index - 1)
                if (previousSibling is DateDivider) {
                    clearDateDividerUnreadStates()
                    previousSibling.unread.set(true)
                    addedUnreadDivider = true
                    return@insertDividerAt
                }
            }

            insertDividerAtInstant(clientMessage.sendTime)
        }

        val messengerStates = socialStates.messages

        // Insert at the oldest message
        val sortedMessages = messageListState.getUntracked().sortedBy { it.sendTime }

        val sortedOtherUserMessages = sortedMessages.filter { USession.activeNow().uuid != it.sender }

        if (sortedOtherUserMessages.isEmpty()) {
            return
        }

        val lastReadMessageId = messengerStates.getLastReadMessageId(channel.id).getUntracked()

        if (lastReadMessageId != null) {
            if (sortedOtherUserMessages.last().id <= lastReadMessageId) {
                addedUnreadDivider = true
                return
            }

            if (sortedOtherUserMessages.first().id > lastReadMessageId && sortedMessages.any { it.id == lastReadMessageId }) {
                insertDividerAt(sortedOtherUserMessages.first())
                return
            }

            sortedOtherUserMessages.firstOrNull { it.id > lastReadMessageId }?.let {
                insertDividerAt(it)
                return
            }
        } else {
            if (receivedAllMessages) {
                // All messages are unread and the new line divider should appear at the top of the list
                insertDividerAt(sortedOtherUserMessages.first())
                return
            }
        }
    }

    private fun requestMoreMessages() {
        val messages = socialStates.messages.getMessagesRaw(channel.id) ?: return

        socialStates.messages.retrieveMessageHistoryRaw(
            channel.id,
            messages.values.minOfOrNull { it.id } ?: return,
            null,
        ) retrieveMessageHistory@{
            if (!it.isPresent) {
                return@retrieveMessageHistory
            }

            val receivedMessages = (it.get() as? ServerChatChannelMessagePacket)?.messages ?: return@retrieveMessageHistory

            // If there are no more messages in the channel, check if the first messages
            // is unread to insert the unread divider
            if (receivedMessages.isEmpty()) {
                receivedAllMessages = true
                insertUnreadDivider() // Manually call because the observer that calls this processes before we set receivedAllMessages
            }

        }
    }

    private fun recalculateTimestampVisibility() {
        // Timestamps are hidden if the message was sent by the same player,
        // within 60 seconds of the previous message, and within 5 minutes of the
        // latest message with a timestamp

        val chainStartDelta = TimeUnit.MINUTES.toMillis(5)
        var chainStartTime: Long? = null
        for (it in content.children) {
            if (it !is MessageWrapper) {
                chainStartTime = null
                continue
            }
            if (chainStartTime != null && it.previousSiblingIsSameSenderWithinAMinute() && (it.message.sendTime.toEpochMilli() - chainStartTime <= chainStartDelta)) {
                it.showTimestamp.set(false)
                continue
            }
            chainStartTime = it.message.sendTime.toEpochMilli()
            it.showTimestamp.set(true)
        }
    }

    private fun parseComponents(message: ClientMessage, messageWrapper: MessageWrapper): List<MessageLine> {
        val messages = mutableListOf<MessageLine>()
        for (part in message.parts) {
            messages.add(when (part) {
                is ClientMessage.Part.Gift -> GiftEmbedImpl(part.id, messageWrapper)
                is ClientMessage.Part.Image -> ImageEmbedImpl(part.id, messageWrapper)
                is ClientMessage.Part.InvitePart -> InviteEmbedImpl(part.invite, messageWrapper)
                is ClientMessage.Part.Skin -> SkinEmbedImpl(part.skin, messageWrapper)
                is ClientMessage.Part.Text -> ParagraphLineImpl(messageWrapper) {
                    if (EssentialConfig.chatFilterWithSource().first) part.filteredContent else part.unfilteredContent
                }
            })
        }
        if (messages.isEmpty()) {
            messages.add(ParagraphLineImpl(messageWrapper, stateOf("")))
        }
        return messages
    }

    fun editMessage(message: String) {
        val originalMessage = editingMessage.get() ?: return
        editingMessage.set(null)

        require(originalMessage.content is Plain)

        if (message == originalMessage.content.unfilteredText) {
            return
        }

        socialStates.messages.editMessage(originalMessage.channel.id, originalMessage.id, message) { success ->
            if (!success) {
                Notifications.push("Error editing message", "An error occurred while editing your message")
            }
        }
    }

    fun sendMessage(message: String) {
        val replyingTo = replyingTo.get()
        this.replyingTo.set(null)

        if (message.isBlank())
            return

        scroller.scrollToBottom()

        platform.playNoteHatSound(0.25f, 0.75f)

        val fakeMessage = ClientMessage(
            fakeID,
            preview.channel,
            USession.activeNow().uuid,
            Plain(message, null),
            SendState.Sending,
            replyingTo?.let {
                MessageRef(preview.channel.id, it.id)
            },
            null,
            Instant.now().toEpochMilli()
        )
        sendMessage(fakeMessage)
    }

    private fun sendMessage(message: ClientMessage) {
        require(message.content is Plain)

        // Add to sendQueue, so we can track the order of messages
        // and manage time desync between client and server
        sendQueue.add(message)

        val trimmed = message.content.unfilteredText.trim().replace("`", "").replace("(?<!  )\\n".toRegex(), "  \n")

        socialStates.messages.sendMessage(message.channel.id, trimmed, message.replyTo?.messageId) { packet ->
            sendQueue.removeAll { it.id == message.id }

            val response = packet.orElse(null)
            if (response !is ServerChatChannelMessagePacket) {
                if (response is ServerChatChannelMessageRejectedPacket) {
                    val blockedSendState = SendState.Blocked(response.reason)
                    sendQueue.add(message.copy(sendState = blockedSendState))
                    Notifications.error(blockedSendState.toastMessage, "")
                } else {
                    sendQueue.add(message.copy(sendState = SendState.Failed))
                }
            }
        }
    }

    override fun onOpen() {
    }

    override fun onClose() {
        standardBar.hide(instantly = true)
        scrollCleanup()
    }

    override fun scrollToMessage(message: ClientMessage) {
        val component = content.children.find {
            it is MessageWrapper && it.message.id == message.id
        } as? MessageWrapper ?: return

        scroller.scrollToCenterComponent(component, smooth = true)

        if (editingMessage.get() == null) {
            component.flashHighlight()
        }
    }

    override fun retrySend(message: ClientMessage) {
        if (message.sendState != SendState.Failed || message.sender != USession.activeNow().uuid) {
            throw IllegalArgumentException("Message was already sent or was not sent by the client")
        }
        sendQueue.removeAll { it.id == message.id }
        sendMessage(message.copy(sendState = SendState.Sending))
    }

    override fun removeUnsent(message: ClientMessage) {
        if (!((message.sendState == SendState.Failed || message.sendState is SendState.Blocked) && message.sender == USession.activeNow().uuid)) {
            throw IllegalArgumentException("Cannot remove a message which has been sent successfully or received")
        }
        sendQueue.removeAll { it.id == message.id }
    }

    private fun findAndMarkLatestMessageAsRead() {
        // Find latest message from other user
        val messages = baseMessageListState.getUntracked().sortedBy { it.id }
        var latestMessage = messages.lastOrNull { USession.activeNow().uuid != it.sender }
        if (latestMessage == null) {
            // If none can be found then find the latest message in general unless the latest read message is already in the message list
            val currentLastReadMessageId = socialStates.messages.getLastReadMessageId(channel.id).getUntracked()
            if (currentLastReadMessageId != null && messages.any { it.id == currentLastReadMessageId }) {
                return
            }
            latestMessage = messages.lastOrNull() ?: return
        }
        socialStates.messages.setLastReadMessage(latestMessage)
    }

    override fun markMessageAsUnread(messageWrapper: MessageWrapper) {
        val messages = content.childrenOfType<MessageWrapperImpl>()
        val readMessageIndex = messages.indexOf(messageWrapper) - 1
        if (readMessageIndex >= 0) {
            socialStates.messages.setLastReadMessage(messages[readMessageIndex].message)
        } else {
            socialStates.messages.setLastReadMessage(channel.id, null)
        }
        markedManuallyUnread = true
        scroller.holdScrollVerticalLocation(messageWrapper) {
            addedUnreadDivider = false

            // Delete the existing unread divider if it exists
            content.children.find { it is UnreadDivider }?.hide(instantly = true)

            // Add the unread message divider
            insertUnreadDivider()
        }
    }

    private companion object {

        var fakeID = Long.MIN_VALUE
            get() = field++

    }
}
