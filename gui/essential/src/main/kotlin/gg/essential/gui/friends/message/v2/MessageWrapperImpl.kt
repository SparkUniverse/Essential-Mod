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

import com.sparkuniverse.toolbox.chat.enums.ChannelType
import gg.essential.config.EssentialConfig
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.events.UIClickEvent
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.pixels
import gg.essential.elementa.state.toConstraint
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.*
import gg.essential.gui.common.modal.DangerConfirmationEssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.common.shadow.EssentialUIText
import gg.essential.gui.common.shadow.ShadowEffect
import gg.essential.gui.elementa.GuiScaleOffsetConstraint
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.add
import gg.essential.gui.elementa.state.v2.color.toConstraint
import gg.essential.gui.elementa.state.v2.combinators.and
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.combinators.or
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableListStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.elementa.state.v2.toV2
import gg.essential.gui.friends.message.MessageScreen
import gg.essential.gui.friends.message.MessageUtils
import gg.essential.gui.friends.message.NewReportMessageModal
import gg.essential.gui.friends.state.IMessengerStates
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignBoth
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.hoverTooltip
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.sendCheckmarkNotification
import gg.essential.gui.util.hoveredState
import gg.essential.gui.util.hoveredStateV2
import gg.essential.universal.UDesktop
import gg.essential.universal.UKeyboard
import gg.essential.universal.USound
import gg.essential.util.*
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick
import java.time.Instant

class MessageWrapperImpl(
    message: ClientMessage,
    private val messageScreen: MessageScreen,
    private val messengerStates: IMessengerStates,
) : MessageWrapper(message) {

    private val replyTo = message.replyTo
    private val replyToState = replyTo?.asState

    private val isEditing = messageScreen.editingMessage.map { it == message }
    private val isEdited = stateOf(message.lastEditTime != null)
    private val shouldShowTimestamp = isEditing or isEdited or stateOf(replyTo != null)

    private val senderUsernameState = UuidNameLookup.getNameAsState(sender)
    private val messageLines = mutableListStateOf<MessageLine>()
    private val messageLinesHoveredStates = messageLines.mapEach {
        if (it is ParagraphLineImpl) {
            it.bubble.hoveredState()
        } else {
            it.hoveredState()
        }.toV2().map { it }
    }

    private val topSpacer by Spacer(height = 5f) childOf this

    private val messageContainer by UIContainer().constrain {
        y = SiblingConstraint()
        width = 100.percent
        height = ChildBasedSizeConstraint()
    } childOf this

    private val usernameTimestampBox by UIContainer().constrain {
        x = 0.pixels(alignOpposite = sentByClient)
        width = ChildBasedSizeConstraint()
        // Height setup in init
    }.bindParent(messageContainer, showTimestamp or shouldShowTimestamp, index = 0)

    private val usernameVisible = sender != USession.activeNow().uuid && channelType == ChannelType.GROUP_DIRECT_MESSAGE

    private val usernameText by EssentialUIText(shadow = false).bindText(senderUsernameState).constrain {
        color = EssentialPalette.TEXT.toConstraint()
        textScale = GuiScaleOffsetConstraint(getGuiScaleOffset())
    }.bindParent(usernameTimestampBox, BasicState(usernameVisible))

    private val replyContextContainer by UIContainer().constrain {
        x = SiblingConstraint(5f)
        height = ChildBasedMaxSizeConstraint()
        width = ChildBasedSizeConstraint()
    }.apply {
        val replyToState = replyToState ?: return@apply

        bindParent(usernameTimestampBox, replyToState.letState { it != null }, index = if (usernameVisible) {
            1
        } else {
            0
        })

        val hovered = hoveredState()
        val colorState = EssentialPalette.getTextColor(hovered)

        replyToState.onSetValueAndNow(this@MessageWrapperImpl) { replyTo ->
            clearChildren()
            fun UIComponent.applyConstraints() = apply {
                constrain {
                    color = colorState.toConstraint()
                    x = SiblingConstraint(2f)
                    textScale = GuiScaleOffsetConstraint(getGuiScaleOffset())
                }
            }
            if (replyTo == null) {
                EssentialUIText("Loading...").applyConstraints() childOf this
                return@onSetValueAndNow
            }
            val replyIcon by EssentialPalette.REPLY_7X5.create().constrain {
                y = CenterConstraint()
                color = colorState.toConstraint()
                width *= GuiScaleOffsetConstraint(getGuiScaleOffset())
                height *= GuiScaleOffsetConstraint(getGuiScaleOffset())
            } childOf this

            val messagePreviewText = when (val part = replyTo.parts.firstOrNull()) {
                is ClientMessage.Part.Text -> State {
                    if (EssentialConfig.chatFilterWithSource().first) part.filteredContent else part.unfilteredContent
                }
                is ClientMessage.Part.Image -> stateOf("Image")
                else -> stateOf("Unknown")
            }

            val replyTextContent = if (replyTo == MessageRef.DELETED) {
                BasicState("Deleted Message")
            } else {
                memo {
                    val username = UuidNameLookup.nameState(replyTo.sender)()
                    "$username: ${messagePreviewText().replace(Regex("(\r\n|\r|\n)"), "")}"
                }.toV1(this)
            }

            // Width of container is set to actual text width of replyText so sibling components are positioned correctly when replyText is truncated
            val replyTextContainer by UIContainer().constrain {
                x = SiblingConstraint(2f)
                height = ChildBasedSizeConstraint()
            } childOf this

            val replyText by EssentialUIText(shadow = false, truncateIfTooSmall = true, showTooltipForTruncatedText = false)
                .apply { replyTextContainer.setWidth(textWidth.pixels()) }
                .bindText(replyTextContent).applyConstraints().constrain {
                    textScale = GuiScaleOffsetConstraint(getGuiScaleOffset())
                    width = width.coerceAtMost(MessageUtils.getMessageWidth(false, this@MessageWrapperImpl) / 2)
            } childOf replyTextContainer
        }

    }.onLeftClick {
        if (replyToState != null) {
            when (val messageRef = replyToState.getUntracked()) {
                null -> {
                    // Wait for the message to be resolved and then scroll
                    replyToState.onSetValue(this) {
                        if (it != null) {
                            messageScreen.scrollToMessage(it)
                        }
                    }
                }
                MessageRef.DELETED -> {
                    // Do nothing
                }
                else -> {
                    messageScreen.scrollToMessage(messageRef)
                }
            }
        }
    }

    private val timestampText by EssentialUIText(formatTime(sendTime, includeSeconds = false), shadow = false).constrain {
        x = SiblingConstraint(5f)
        textScale = GuiScaleOffsetConstraint(getGuiScaleOffset())
        color = EssentialPalette.TEXT_DISABLED.toConstraint()
    } childOf usernameTimestampBox

    init {
        if (showEditedLabel) {
            val editedText by EssentialUIText().bindText(isEditing.map { if (it) "editing" else "(edited)" }.toV1(this)).constrain {
                x = SiblingConstraint(4f)
                textScale = GuiScaleOffsetConstraint(getGuiScaleOffset())
                color = isEditing.map { if (it) EssentialPalette.BANNER_BLUE else EssentialPalette.TEXT_DISABLED }.toConstraint()
            }.bindParent(usernameTimestampBox, isEditing or isEdited).apply {
                bindEssentialTooltip(
                    (hoveredState().toV2() and isEdited and !isEditing).toV1(this),
                    BasicState(Instant.ofEpochMilli(message.lastEditTime ?: 0L).formatter("$DATE_FORMAT, ${getTimeFormat(false)}")),
                    EssentialTooltip.Position.ABOVE,
                )
            }
        }
    }

    // Constraints/parent set in addComponent method
    private val actionButtonHitbox = UIContainer()

    init {
        constrain {
            x = CenterConstraint()
            y = SiblingConstraint()
            width = 100.percent - 20.pixels // Subtract padding
            height = (100.percent boundTo messageContainer) + (100.percent boundTo topSpacer)
        }

        usernameTimestampBox.setHeight(100.percent boundTo timestampText)

        val replyingToThisMessage = messageScreen.replyingTo.map { it == message }
        replyingToThisMessage.onChange(this) {
            if (it) {
                messageLines.get().forEach { it.beginHighlight() }
            } else {
                messageLines.get().forEach { it.releaseHighlight() }
            }
        }
        messageScreen.editingMessage.map { it == message }.onChange(this) {
            if (it) {
                messageLines.get().forEach { it.beginHighlight() }
            } else {
                messageLines.get().forEach { it.releaseHighlight() }
            }
        }
    }

    override fun delete() {
        hide(instantly = true)
    }

    override fun addComponent(line: MessageLine) {
        messageLines.add(line)
        line.constrain {
            y = SiblingConstraint(3f)
            x = 0.pixels(alignOpposite = message.sender == USession.activeNow().uuid)
        } childOf messageContainer

        if (!actionButtonHitbox.hasParent && !message.channel.isAnnouncement()
            && !((message.sendState is SendState.Blocked || messageScreen.preview.isChannelSuspendedState.getUntracked()))
            && (line is ParagraphLine || (line is ImageEmbed && !sentByClient))
        ) {
            val messageBox = (line as? ParagraphLineImpl)?.bubble ?: line
            val actionTooltipText = BasicState(if (sentByClient) "Edit" else "Reply")
            val actionButtonIcon = if (sentByClient) EssentialPalette.PENCIL_7x7 else EssentialPalette.REPLY_LEFT_7X5

            fun runAction() {
                if (sentByClient) {
                    if (message.sendState !is SendState.Confirmed) return
                    messageScreen.editingMessage.set(message)
                } else {
                    messageScreen.replyingTo.set(message)
                }
            }

            val messageHitboxPadding by UIContainer().constrain {
                x = 100.percent boundTo messageBox
                y = 0.pixels boundTo messageBox
                width = 5.pixels
                height = 100.percent boundTo messageBox
            }.onRightClick { openOptionMenu(it, line) } childOf this

            val anyLineHovered = State { messageLinesHoveredStates().any { it() } }

            actionButtonHitbox.constrain {
                x = (-7).pixels(alignOpposite = !sentByClient) boundTo messageBox
                y = (-7).pixels boundTo messageBox
                width = 15.pixels
                height = AspectConstraint()
            }.onLeftClick {
                runAction()
                USound.playButtonPress()
                it.stopPropagation()
            }.layoutAsBox(Modifier.hoverScope().hoverTooltip(actionTooltipText, position = EssentialTooltip.Position.ABOVE, padding = 3f)) {
                if_({ actionButtonHitbox.hoveredStateV2()() || anyLineHovered() || messageHitboxPadding.hoveredStateV2()() }) {
                    val actionButton by IconButton(actionButtonIcon).constrain {
                        width = 100.percent - 4.pixels
                        height = AspectConstraint()
                        color = actionButtonHitbox.hoveredStateV2().letState { if (it) EssentialPalette.BUTTON_HIGHLIGHT else EssentialPalette.BUTTON }.toConstraint()
                    }.rebindIconColor(
                        actionButtonHitbox.hoveredState().map { if (it) EssentialPalette.TEXT_HIGHLIGHT else EssentialPalette.TEXT }
                    ).onActiveClick { runAction() } effect ShadowEffect(EssentialPalette.BLACK)
                    actionButton(Modifier.alignBoth(Alignment.Center))
                }
            }

            actionButtonHovered.set(State { actionButtonHitbox.hoveredStateV2()() || messageHitboxPadding.hoveredStateV2()() })

            if (message.sendState == SendState.Confirmed) {
                actionButtonHitbox childOf this
            }
        }
    }

    override fun openOptionMenu(event: UIClickEvent, component: MessageLine) {
        val posX = event.absoluteX
        val posY = event.absoluteY
        val options = mutableListOf<ContextOptionMenu.Item>()

        val replyOption = ContextOptionMenu.Option("Reply", image = EssentialPalette.REPLY_10X5) {
            messageScreen.replyingTo.set(message)
        }

        fun doDelete() {
            messengerStates.deleteMessage(message)
        }

        val deleteOption = ContextOptionMenu.Option(
            "Delete",
            image = EssentialPalette.TRASH_9X,
            hoveredColor = EssentialPalette.TEXT_WARNING,
            hoveredShadowColor = EssentialPalette.COMPONENT_BACKGROUND,
        ) {
            if (UKeyboard.isShiftKeyDown()) {
                doDelete()
            } else {
                platform.pushModal { manager ->
                    DeleteMessageConfirmationModal(manager).onPrimaryAction {
                        doDelete()
                    }
                }
            }
        }

        val markUnreadOption = ContextOptionMenu.Option("Mark Unread", image = EssentialPalette.MARK_UNREAD_10X7) {
            messageScreen.markMessageAsUnread(this)
        }

        val reportOption = ContextOptionMenu.Option(
            "Report",
            image = EssentialPalette.REPORT_10X7,
            hoveredColor = EssentialPalette.TEXT_WARNING
        ) {
            platform.pushModal { manager ->
                NewReportMessageModal(manager, message)
            }
        }

        when (component) {
            is ParagraphLine -> {
                if (message.sendState is SendState.Blocked) {
                    options.add(deleteOption.copy(action = {
                        messageScreen.removeUnsent(message)
                    }))
                } else {
                    val copyOption = ContextOptionMenu.Option("Copy", image = EssentialPalette.COPY_10X7) {
                        UDesktop.setClipboardString(
                            component.selectedText.ifEmpty { component.messageContent.getUntracked() }.trim().removePrefix("<")
                                .removeSuffix(">")
                        )
                    }
                    if (sentByClient && (!messageScreen.preview.isChannelSuspendedState.getUntracked())) {
                        options.add(ContextOptionMenu.Option("Edit", image = EssentialPalette.PENCIL_7x7) {
                            messageScreen.editingMessage.set(message)
                        })
                    }
                    if (channelType != ChannelType.ANNOUNCEMENT && (!messageScreen.preview.isChannelSuspendedState.getUntracked())) {
                        options.add(replyOption)
                    }
                    options.add(copyOption)
                }
            }

            is ImageEmbed -> {
                val copyLinkOption = ContextOptionMenu.Option(
                    "Copy Link",
                    image = EssentialPalette.LINK_10X7
                ) {
                    val url = component.url
                    if (url != null) {
                        UDesktop.setClipboardString(url.toString())
                        sendCheckmarkNotification("Link copied to clipboard")
                    } else {
                        Notifications.error("Failed to copy link", "")
                    }
                }
                val copyImageOption = ContextOptionMenu.Option("Copy Picture", image = EssentialPalette.COPY_10X7) {
                    component.copyImageToClipboard()
                }
                val saveImageOption = ContextOptionMenu.Option("Save Picture", image = EssentialPalette.DOWNLOAD_7x8) {
                    component.saveImageToScreenshotBrowser()
                }
                val openInBrowserOption =
                    ContextOptionMenu.Option("Open in Browser", image = EssentialPalette.ARROW_UP_RIGHT_5X5) {
                        val url = component.url
                        if (url != null) {
                            UDesktop.browse(url.toURI())
                        } else {
                            Notifications.error("Failed to open link", "")
                        }
                    }

                if (channelType != ChannelType.ANNOUNCEMENT) {
                    if (!messageScreen.preview.isChannelSuspendedState.getUntracked()) {
                        options.add(replyOption)
                    }
                    options.add(ContextOptionMenu.Divider)
                }
                options.add(copyImageOption)
                options.add(copyLinkOption)
                options.add(saveImageOption)
                options.add(openInBrowserOption)
                if (!sentByClient) options.add(ContextOptionMenu.Divider)
            }

            is InviteEmbed -> {}
            is GiftEmbed -> {}
            is SkinEmbed -> {
                options.add(ContextOptionMenu.Option("Copy Link", image = EssentialPalette.LINK_10X7) {
                    UDesktop.setClipboardString(component.skin.url)
                    sendCheckmarkNotification("Link copied to clipboard.")
                })
            }
        }

        if (message.sent) {
            if (sentByClient) {
                if (options.isNotEmpty()) {
                    options.add(ContextOptionMenu.Divider)
                }
                options.add(deleteOption)
            } else {
                options.add(markUnreadOption)
                if (channelType != ChannelType.ANNOUNCEMENT) {
                    options.add(ContextOptionMenu.Divider)
                    options.add(reportOption)
                }
            }
        }

        val menu = ContextOptionMenu(
            posX,
            posY,
            *options.toTypedArray()
        ) childOf Window.of(this)

        menu.init()
        dropdownOpen.set(true)
        menu.onClose {
            dropdownOpen.set(false)
        }
    }

    override fun flashHighlight() {
        messageLines.get().forEach {
            it.flashHighlight()
        }
    }

    override fun retrySend() {
        messageScreen.retrySend(message)
    }

    override fun extractComponent(extractor: ElementaExtractor) {
        maybeLoadReplyTo()
    }

    private fun maybeLoadReplyTo() {
        // If this element is on screen (or slightly off screen), we must load the reply
        // context if it is not already loaded. The implementation in chatManager is safe against many calls to eagerlyLoad
        if (replyTo != null && !replyTo.isInitialized() && (getTop() > -600)) {
            Window.enqueueRenderOperation {
                replyTo.eagerlyLoad()
            }
        }
    }

    class DeleteMessageConfirmationModal(manager: ModalManager) : DangerConfirmationEssentialModal(
        manager,
        "Delete",
        requiresButtonPress = false
    ) {
        init {
            configure {
                titleText = "Are you sure you want to delete this message?"
            }
        }
    }

}
