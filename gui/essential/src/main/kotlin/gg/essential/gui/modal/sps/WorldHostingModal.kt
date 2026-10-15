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
package gg.essential.gui.modal.sps

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.MousePositionConstraint
import gg.essential.elementa.dsl.effect
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.events.UIClickEvent
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.ContextOptionMenu
import gg.essential.gui.common.EssentialDropDown
import gg.essential.gui.common.EssentialExpandableMenu.Style
import gg.essential.gui.common.EssentialTooltip
import gg.essential.gui.common.FullEssentialToggle
import gg.essential.gui.common.LoadingIcon
import gg.essential.gui.common.MenuButton
import gg.essential.gui.common.OutlineButton
import gg.essential.gui.common.OutlineButtonStyle
import gg.essential.gui.common.StyledButton
import gg.essential.gui.common.effect.HorizontalScissorEffect
import gg.essential.gui.common.expandableMenu
import gg.essential.gui.common.input.StateTextInput
import gg.essential.gui.common.input.UITextInput
import gg.essential.gui.common.input.essentialInput
import gg.essential.gui.common.input.essentialIntInput
import gg.essential.gui.common.input.essentialStringInput
import gg.essential.gui.common.modal.EssentialModal2
import gg.essential.gui.common.modal.continueModal
import gg.essential.gui.common.outlineButton
import gg.essential.gui.common.state
import gg.essential.gui.common.textStyle
import gg.essential.gui.effects.AlphaEffect
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.asyncMap
import gg.essential.gui.elementa.state.v2.combinators.bimap
import gg.essential.gui.elementa.state.v2.combinators.letState
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.combinators.not
import gg.essential.gui.elementa.state.v2.combinators.or
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.flatten
import gg.essential.gui.elementa.state.v2.listStateOf
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mapList
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.onChange
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.elementa.state.v2.withSetter
import gg.essential.gui.friends.state.PlayerActivity
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.BasicYModifier
import gg.essential.gui.layoutdsl.DetachedLayout
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.animateColor
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.childBasedMaxHeight
import gg.essential.gui.layoutdsl.childBasedMaxWidth
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.detachedLayout
import gg.essential.gui.layoutdsl.effect
import gg.essential.gui.layoutdsl.fillHeight
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.fillRemainingHeight
import gg.essential.gui.layoutdsl.fillRemainingWidth
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.floatingBox
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.heightAspect
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.hoverTooltip
import gg.essential.gui.layoutdsl.icon
import gg.essential.gui.layoutdsl.image
import gg.essential.gui.layoutdsl.maxHeight
import gg.essential.gui.layoutdsl.outline
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.scrollable
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.tag
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.then
import gg.essential.gui.layoutdsl.whenHovered
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.widthAspect
import gg.essential.gui.layoutdsl.withHoverState
import gg.essential.gui.layoutdsl.wrappedText
import gg.essential.gui.modals.select.component.playerAvatarWithOnlineIndicator
import gg.essential.gui.modals.select.component.playerName
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.overlay.modalFlow
import gg.essential.gui.util.Tag
import gg.essential.gui.util.findChildrenByTag
import gg.essential.gui.util.focusedState
import gg.essential.gui.util.getTag
import gg.essential.gui.util.hoverScopeV2
import gg.essential.gui.util.hoveredStateV2
import gg.essential.gui.util.makeHoverScope
import gg.essential.sps.CWGameRule
import gg.essential.sps.ResourcePackName
import gg.essential.sps.WorldDifficulty
import gg.essential.sps.WorldGameMode
import gg.essential.sps.WorldManager
import gg.essential.sps.WorldsManager
import gg.essential.universal.UDesktop
import gg.essential.universal.UI18n
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMouse
import gg.essential.universal.USound
import gg.essential.universal.utils.ReleasedDynamicTexture
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.ServerType
import gg.essential.util.Sha256
import gg.essential.util.UIdentifier
import gg.essential.util.UuidNameLookup
import gg.essential.util.image.bitmap.toTexture
import gg.essential.util.loadUIImage
import gg.essential.util.onLeftClick
import gg.essential.util.onRightClick
import gg.essential.util.scrollGradient
import gg.essential.util.toImageFactory
import gg.essential.util.toState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.io.path.inputStream
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class WorldHostingModal(
    modalManager: ModalManager,
    val worldsManager: WorldsManager,
    val worldManager: WorldManager,
) : EssentialModal2(modalManager) {

    constructor(modalManager: ModalManager, worldManager: WorldManager) : this(modalManager, platform.worldsManager, worldManager)

    private lateinit var mainPane: UIComponent

    val activeTab = mutableStateOf(Tab.Members).apply {
        onChange(this@WorldHostingModal) {
            commitResourcePacks()
            commitResourcePacks = {}
        }
    }
    private val gameRuleSearchSource = mutableStateOf(stateOf(""))
    private val gameRuleSearch = gameRuleSearchSource.flatten()

    private var suppressClose = false

    private var commitResourcePacks: () -> Unit = {}

    init {
        worldManager.reloadGameRulesIfNecessary()
    }

    override fun LayoutScope.layoutModal() {
        column(Modifier.width(309f).fillHeight().maxHeight(274f).color(EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT)) {
            layoutTitlebar(Modifier.fillWidth(padding = 1f).height(27f))
            row(Modifier.fillWidth(padding = 1f).fillRemainingHeight()) {
                layoutSidebar(Modifier.width(84f).fillHeight())
                layoutMainPane(Modifier.fillRemainingWidth().fillHeight())
            }
            spacer(height = 1f)
        }
    }

    private fun LayoutScope.layoutTitlebar(modifier: Modifier = Modifier) {
        box(modifier) {
            row(Modifier.fillParent(padding = 10f), Arrangement.SpaceBetween) {
                row(Modifier.alignVertical(Alignment.Center(true)), Arrangement.spacedBy(5f)) {
                    text(worldManager.name, Modifier.shadow(Color.BLACK))
                }
                row(Arrangement.spacedBy(4f)) {
                    button(OutlineButtonStyle.GRAY, {
                        UDesktop.open(worldManager.localFolder.toFile())
                    }, Modifier.width(13f).heightAspect(1f)) { currentStyle ->
                        column {
                            image(EssentialPalette.MC_FOLDER_7X6, Modifier.textStyle(currentStyle))
                            spacer(height = 1f) // Move icon up by 1 pixel
                        }
                    }
                    button(OutlineButtonStyle.GRAY, { close() }, Modifier.width(13f).heightAspect(1f)) { currentStyle ->
                        image(EssentialPalette.CANCEL_5X, Modifier.textStyle(currentStyle))
                    }
                }
            }
        }
    }

    private fun LayoutScope.layoutSidebar(modifier: Modifier = Modifier) {
        box(Modifier.color(EssentialPalette.COMPONENT_BACKGROUND).then(modifier)) {
            column(Modifier.fillParent(padding = 10f), Arrangement.SpaceBetween) {
                column(Modifier.fillWidth(), Arrangement.spacedBy(8f), Alignment.Start) {
                    for (tab in Tab.entries) {
                        val visible = State {
                            when (tab) {
                                Tab.Debug -> isDebugEnabled()
                                else -> true
                            }
                        }
                        val selected = memo { activeTab() == tab }
                        val color = Modifier.withHoverState { hovered ->
                            Modifier.animateColor(memo {
                                when {
                                    selected() -> EssentialPalette.ACCENT_BLUE
                                    hovered() -> EssentialPalette.TEXT_HIGHLIGHT
                                    else -> EssentialPalette.TEXT
                                }
                            }, 0.5f).shadow(EssentialPalette.BLACK)
                        }
                        if_(visible) {
                            text(tab.displayName, color.hoverScope()).onLeftClick { activeTab.set(tab) }
                        }
                    }
                }
                column(Modifier.fillWidth(), Arrangement.spacedBy(10f), Alignment.Start) {
                    column(Arrangement.spacedBy(2f), Alignment.Start) {
                        text("Status", Modifier.color(EssentialPalette.TEXT_DISABLED).shadow(Color.BLACK))
                        bind({
                            if (worldManager.host() != null) {
                                "Online" to EssentialPalette.GREEN
                            } else {
                                "Offline" to EssentialPalette.RED
                            }
                        }) { (text, color) ->
                            text(text, Modifier.color(color).shadow(Color.BLACK))
                        }
                    }
                    column(Modifier.fillWidth(), Arrangement.spacedBy(2f), Alignment.Start) {
                        text("Host", Modifier.color(EssentialPalette.TEXT_DISABLED).shadow(Color.BLACK))
                        text({
                            val uuid = worldManager.host() ?: return@text "n/a"
                            UuidNameLookup.nameState(uuid, "Loading..")()
                        }, Modifier.color(EssentialPalette.TEXT).shadow(Color.BLACK), truncateIfTooSmall = true)
                    }
                    column(Arrangement.spacedBy(2f), Alignment.Start) {
                        text("Version", Modifier.color(EssentialPalette.TEXT_DISABLED).shadow(Color.BLACK))
                        text(worldManager.gameDisplayVersion, Modifier.color(EssentialPalette.TEXT).shadow(Color.BLACK))
                        ifNotNull({ worldManager.gameModLoader()?.name }) { modLoader ->
                            text(modLoader, Modifier.color(EssentialPalette.TEXT).shadow(Color.BLACK))
                        }
                    }
                }
            }
        }
    }

    private fun LayoutScope.layoutMainPane(modifier: Modifier = Modifier) {
        column(Modifier.color(EssentialPalette.GUI_BACKGROUND).then(modifier)) {
            if_({ activeTab().hasTopButtons }) {
                box(Modifier.fillWidth(padding = 10f).childBasedHeight(padding = 10f)) {
                    layoutTopButtons()
                }
            }
            mainPane = box(Modifier.fillWidth().fillRemainingHeight()) {
                val scrollComponent = scrollable(Modifier.fillWidth(padding = 10f).fillHeight(), vertical = true) {
                    box(Modifier.fillWidth().alignVertical(Alignment.Start)) {
                        bind(activeTab) { tab ->
                            when (tab) {
                                Tab.Debug -> layoutDebugContent()
                                Tab.General -> layoutGeneralContent()
                                Tab.Members -> layoutMembersContent()
                                Tab.GameRules -> layoutGameRulesContent()
                                Tab.Packs -> layoutPacksContent()
                            }
                        }
                    }
                }

                val scrollbar: UIComponent
                box(Modifier.height(scrollComponent).width(3f).alignHorizontal(Alignment.End).hoverScope()) {
                    scrollbar = box(
                        Modifier.width(3f).fillHeight().color(EssentialPalette.LIGHTEST_BACKGROUND)
                            .hoverColor(EssentialPalette.SCROLLBAR)
                    )
                }
                scrollComponent.setVerticalScrollBarComponent(scrollbar, hideWhenUseless = true)

                scrollGradient(scrollComponent, true, 30f, maxGradient = 153)
                scrollGradient(scrollComponent, false, 30f, maxGradient = 153)
                scrollComponent.removeEffect<ScissorEffect>()
                scrollComponent.effect(HorizontalScissorEffect())
            }
            if_({ activeTab().hasBottomButtons }) {
                box(Modifier.fillWidth(padding = 10f).childBasedHeight(padding = 10f)) {
                    layoutButtons()
                }
            }
        }
    }

    private fun LayoutScope.setting(name: String, content: LayoutScope.() -> Unit) {
        box(Modifier.fillWidth().height(27f).color(EssentialPalette.COMPONENT_BACKGROUND)) {
            row(Modifier.fillWidth(padding = 6f).height(17f), Arrangement.SpaceBetween) {
                text(name, Modifier.shadow(Color.BLACK).alignVertical(Alignment.Center(true)))
                content()
            }
        }
    }

    private fun LayoutScope.layoutDebugContent() {
        column(Modifier.childBasedHeight(padding = 5f), Arrangement.spacedBy(3f)) {
            return@column
        }
    }

    private fun LayoutScope.layoutGeneralContent() {
        column(Modifier.fillWidth().childBasedHeight(padding = 10f), Arrangement.spacedBy(1f)) {
            setting("Name") {
                var updateJob: Job? = null
                val worldName = memo { worldManager.name() }
                    .withSetter { update ->
                        updateJob?.cancel()
                        updateJob = coroutineScope.launch {
                            worldManager.updateName(update(getUntracked()))
                        }
                    }
                val input = StateTextInput(worldName, true, 2f, 32, { it }, { it.trim() }).apply {
                    setMinWidth(1f.pixels)
                    setMaxWidth(100.percent - 10.pixels)
                }
                val inputFocusedState = input.focusedState()
                val colorModifier = Modifier.withHoverState { hovered ->
                    Modifier.color {
                        when {
                            inputFocusedState() -> EssentialPalette.BLUE_BUTTON
                            hovered() -> EssentialPalette.GRAY_OUTLINE_BUTTON_OUTLINE
                            else -> EssentialPalette.BANNER_GRAY
                        }
                    }
                }
                box(Modifier.width(90f).height(17f).then(colorModifier).shadow().hoverScope()) {
                    box(Modifier.fillParent(padding = 1f).color(EssentialPalette.COMPONENT_BACKGROUND)) {
                        input(Modifier.alignHorizontal(Alignment.Start(5f)).alignVertical(Alignment.Center(true)))
                    }
                }.onMouseClick { event ->
                    input.mouseClick(event.absoluteX.toDouble(), event.absoluteY.toDouble(), event.mouseButton)
                    event.stopPropagation()
                }
            }
            setting("Game Mode") {
                val dropdown = EssentialDropDown(
                    worldManager.gameSettings.getUntracked().gameMode,
                    listStateOf(*WorldGameMode.entries.map { EssentialDropDown.Option(it.name, it) }.toTypedArray()),
                    isPopupMenu = true,
                )
                var updateJob: Job? = null
                dropdown.selectedOption.onChange(stateScope) { newOption ->
                    updateJob?.cancel()
                    updateJob = coroutineScope.launch {
                        worldManager.updateGameSettings { it.copy(gameMode = newOption.value) }
                    }
                }
                dropdown()
            }
            if_({ !worldManager.gameSettings().difficultyLocked }) {
                setting("Difficulty") {
                    val dropdown = EssentialDropDown(
                        worldManager.gameSettings.getUntracked().difficulty,
                        listStateOf(*WorldDifficulty.entries.map { EssentialDropDown.Option(it.name, it) }
                            .toTypedArray()),
                        isPopupMenu = true,
                    )
                    var updateJob: Job? = null
                    dropdown.selectedOption.onChange(stateScope) { newOption ->
                        updateJob?.cancel()
                        updateJob = coroutineScope.launch {
                            worldManager.updateGameSettings { it.copy(difficulty = newOption.value) }
                        }
                    }
                    dropdown()
                }
            }
            setting("Cheats") {
                val state = memo { worldManager.gameSettings().cheats }
                var updateJob: Job? = null
                val toggle = FullEssentialToggle(
                    state.withSetter { mapper ->
                        val oldValue = getUntracked()
                        val newValue = mapper(oldValue)
                        if (oldValue == newValue) return@withSetter
                        updateJob?.cancel()
                        updateJob = coroutineScope.launch {
                            worldManager.updateGameSettings { it.copy(cheats = newValue) }
                        }
                    }
                )
                toggle()
            }
        }
    }

    private fun LayoutScope.layoutMembersContent() {
        val activities = platform.createSocialStates().activity
        fun LayoutScope.section(title: State<String>, players: State<Set<UUID>>, canInvite: State<Boolean> = stateOf(false)) {
            if_({ players().isNotEmpty() }) {
                column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
                    row(Modifier.fillWidth(), Arrangement.spacedBy(3f)) {
                        text(title, Modifier.color(EssentialPalette.TEXT_DISABLED))
                        box(Modifier.fillRemainingWidth().height(1f).color(EssentialPalette.DIVIDER))
                    }
                    column(Modifier.fillWidth(), Arrangement.spacedBy(3f)) {
                        val sortedPlayers = memo {
                            players().sortedWith(compareBy<UUID> {
                                if (it == worldManager.owner()) 0 else 1
                            }.thenBy {
                                activities.getActivityState(it)() is PlayerActivity.Offline
                            }.thenBy {
                                // Initial value is tilde to ensure loading items are sorted to the bottom of the list.
                                UuidNameLookup.nameState(it, "~")().lowercase()
                            })
                        }.toListState()
                        forEach(sortedPlayers) { uuid ->
                            val isOwner = memo { uuid == worldManager.owner() }
                            val isOp = memo { uuid in worldManager.gameSettings().ops }
                            val isHost = memo { uuid == worldManager.host() }
                            val isConnected = memo { uuid in worldManager.connectedMembers() }
                            val isInviteDisabled = mutableStateOf(false)

                            fun createPlayerContextMenu(clickEvent: UIClickEvent, uiComponent: UIComponent) {
                                ContextOptionMenu.create(
                                    ContextOptionMenu.Position(clickEvent),
                                    Window.of(uiComponent),
                                    State {
                                        buildList {
                                            if (canInvite() && !isOwner()) {
                                                add(ContextOptionMenu.Option("Send game invite", EssentialPalette.ENVELOPE_9X7, isInviteDisabled) {
                                                    isInviteDisabled.set(true)
                                                    worldManager.sendInviteToMember(uuid)
                                                    coroutineScope.launch {
                                                        delay(5.seconds)
                                                        isInviteDisabled.set(false)
                                                    }
                                                })
                                                add(ContextOptionMenu.Divider)
                                            }
                                            if (!isOwner()) {
                                                add(ContextOptionMenu.Option("Remove member", EssentialPalette.REMOVE_FRIEND_PLAYER_10X7) {
                                                    coroutineScope.launch { removeMember(uuid) }
                                                })
                                                add(ContextOptionMenu.Divider)
                                            }
                                            add(ContextOptionMenu.Option(
                                                memo { if (isOp()) "Remove operator" else "Make operator" },
                                                stateOf(EssentialPalette.OP_7X5)
                                            ) {
                                                coroutineScope.launch { toggleOp(uuid) }
                                            })
                                        }
                                    }.toListState()
                                )
                            }

                            box(Modifier.fillWidth().height(21f).color(EssentialPalette.COMPONENT_BACKGROUND).hoverColor(EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT).hoverScope()) {
                                row(Modifier.fillWidth(padding = 6f).fillHeight(padding = 4f), Arrangement.SpaceBetween) {
                                    row(Arrangement.spacedBy(5f)) {
                                        row(Modifier.childBasedMaxHeight(1f), Arrangement.spacedBy(5f)) {
                                            playerAvatarWithOnlineIndicator(uuid, activities)
                                            playerName(uuid, Modifier.shadow(Color.BLACK).alignVertical(Alignment.End))
                                        }
                                        row(Modifier.alignVertical(Alignment.Center(true)), Arrangement.spacedBy(5f)) {
                                            if_(isOwner) {
                                                icon(
                                                    EssentialPalette.CROWN_ICON,
                                                    Modifier.color(EssentialPalette.MODAL_WARNING).shadow(Color.BLACK).hoverTooltip("Owner", position = EssentialTooltip.Position.ABOVE).hoverScope()
                                                )
                                            }
                                            if_(isOp) {
                                                icon(
                                                    EssentialPalette.OP_7X5,
                                                    Modifier.color(EssentialPalette.RED).shadow(Color.BLACK).hoverTooltip("Operator", position = EssentialTooltip.Position.ABOVE).hoverScope()
                                                )
                                            }
                                        }
                                    }
                                    row {
                                        box(Modifier.height(10f).widthAspect(1f)) {
                                            image(
                                                EssentialPalette.OPTIONS_8X2,
                                                Modifier.whenHovered(
                                                    Modifier.color(EssentialPalette.TEXT).shadow(Color.BLACK),
                                                    Modifier.color(EssentialPalette.COMPONENT_BACKGROUND)
                                                )
                                            )
                                        }.onLeftClick {
                                            createPlayerContextMenu(it, this)
                                            it.stopPropagation()
                                        }
                                        spacer(width = 1f)
                                    }
                                }
                            }.onRightClick {
                                createPlayerContextMenu(it, this)
                                it.stopPropagation()
                            }
                        }
                    }
                }
            }
        }

        val isServerOnline = memo {
            worldManager.localWorldOpen()
        }
        val playing = memo {
            when {
                worldsManager.integratedServerWorld() == worldManager ->
                    worldsManager.integratedServerManager()?.connectedPlayers()?.toSet()
                else -> null
            } ?: emptySet()
        }
        val maxPlayers = memo { worldsManager.integratedServerManager()?.maxPlayers() ?: 8 }
        val notPlaying = memo { worldManager.members() - playing().toSet() }

        column(Modifier.fillWidth().childBasedHeight(padding = 10f), Arrangement.spacedBy(7f)) {
            section({ "Playing [${playing().size}/${maxPlayers()}]" }, playing)
            section({ "Members List" }, notPlaying, isServerOnline)
        }
    }

    private fun LayoutScope.layoutGameRulesContent() {
        fun i18nOrNull(key: String): String? =
            UI18n.i18n(key).takeIf { it != key }
        fun i18nName(id: UIdentifier, rule: CWGameRule) =
            i18nOrNull(id.toTranslationKey("gamerule")) ?: rule.nameFallback
        fun i18nDescription(id: UIdentifier, rule: CWGameRule) =
            i18nOrNull(id.toTranslationKey("gamerule", "description")) ?: rule.descriptionFallback
        fun i18nCategory(name: String, categoryFallback: String) =
            i18nOrNull(name) ?: categoryFallback

        column(Modifier.fillWidth()) {
            column(Modifier.fillWidth(), Arrangement.spacedBy(3f)) {
                val list = memo {
                    val search = gameRuleSearch()
                    worldManager.gameRules().gamerules
                        .asSequence()
                        .filter { (name, rule) ->
                            search.isEmpty()
                                    || name.toLegacyString().contains(search, true)
                                    || i18nName(name, rule).contains(search, true)
                                    || i18nDescription(name, rule).contains(search, true)
                        }
                        .groupBy { it.value.category }
                        .entries.sortedBy {
                            i18nCategory(it.key, it.value.firstOrNull()?.value?.categoryFallback ?: it.key)
                        }
                }.toListState()
                val categories = list.mapEach { it.key }
                forEach(categories) { category ->
                    val categoryList = memo { list().filter { it.key == category }.flatMap { it.value } }.toListState()
                    val categoryName = memo { categoryList().firstOrNull()?.let { i18nCategory(category, it.value.categoryFallback) } }
                    val categoryLayouts: ListState<LayoutScope.(Style) -> Unit> = memo {
                        categoryList().map { (name, rule) ->
                            val layout: LayoutScope.(Style) -> Unit = { _ ->
                                row(Modifier.fillWidth(padding = 7f)) {
                                    box(Modifier.fillRemainingWidth().childBasedHeight(9f)) {
                                        wrappedText(
                                            i18nName(name, rule),
                                            lineSpacing = 11f,
                                            modifier = Modifier.shadow(EssentialPalette.BLACK).alignHorizontal(Alignment.Start).hoverScope().hoverTooltip(EssentialTooltip.Position.MOUSE) {
                                                column(
                                                    Modifier.childBasedHeight(4f).childBasedMaxWidth(5f).color(EssentialPalette.COMPONENT_BACKGROUND).outline(EssentialPalette.BLACK, 1f),
                                                    Arrangement.spacedBy(3f),
                                                    Alignment.Start(5f)
                                                ) {
                                                    text(name.toLegacyString(), Modifier.shadow(EssentialPalette.BLACK))
                                                    wrappedText(i18nDescription(name, rule), lineSpacing = 11f, modifier = Modifier.width(200f).color(EssentialPalette.TEXT_MID_GRAY).shadow(EssentialPalette.BLACK))
                                                    text("Default value: ${rule.defaultValue}", Modifier.color(EssentialPalette.TEXT_MID_GRAY).shadow(EssentialPalette.BLACK))
                                                }
                                            }
                                        )
                                    }
                                    spacer(width = 15f, height = 26f)
                                    box {
                                        val valueState = mutableStateOf(rule.value)
                                        when (rule.type) {
                                            CWGameRule.Type.Boolean -> FullEssentialToggle(valueState.bimap({ it.toBoolean() }, { it.toString() }))()
                                            CWGameRule.Type.Int -> essentialIntInput(valueState.bimap({ it.toIntOrNull() ?: 0 }, { it.toString() }), Modifier.width(30f).shadow(Color.BLACK))
                                            CWGameRule.Type.Unknown -> essentialStringInput(valueState, Modifier.width(60f).shadow(Color.BLACK))
                                        }
                                        valueState.onChange(stateScope) { value ->
                                            worldManager.updateGameRules { it + (name to value) }
                                        }
                                    }
                                }
                            }
                            layout
                        }
                    }.toListState()
                    ifNotNull(categoryName) { categoryName ->
                        val menu = expandableMenu(stateOf(categoryName), categoryLayouts)
                        effect(stateScope) {
                            menu.setExpanded(gameRuleSearch().isNotEmpty())
                        }
                    }
                }
            }
            spacer(height = 10f)
        }
    }

    private val defaultPackIcon = modalManager.coroutineScope.async(start = CoroutineStart.LAZY) {
        val resource = platform.bitmapFromMinecraftResource(UIdentifier("minecraft", "textures/misc/unknown_pack.png"))
        val uiImage = UIImage(CompletableFuture.completedFuture(null))
        uiImage.applyTexture(resource?.toTexture() ?: ReleasedDynamicTexture(1, 1))
        uiImage.toImageFactory()
    }

    private fun LayoutScope.layoutPacksSection(
        title: State<String>,
        allPacks: State<Map<Sha256, Pair<ResourcePackName, Deferred<Path>>>>,
        availablePacks: State<List<Sha256>>,
        appliedPacks: MutableState<List<Sha256>>,
        isAppliedList: Boolean,
    ) {
        val draggedSha256: MutableState<Sha256?> = mutableStateOf(null)
        val draggedOffset = mutableStateOf(0f)
        val displayedList = if (isAppliedList) appliedPacks else availablePacks

        fun LayoutScope.packItem(sha256: Sha256, name: ResourcePackName, path: Deferred<Path>): UIComponent {
            val image = coroutineScope.async(Dispatchers.IO) {
                try {
                    val bufferedImage = FileSystems.newFileSystem(path.await(), null as ClassLoader?).use { fs ->
                        fs.getPath("pack.png").inputStream().use { ImageIO.read(it) ?: throw IOException("Unsupported image format") }
                    }
                    loadUIImage(bufferedImage).toImageFactory()
                } catch (e: Exception) {
                    if (e !is NoSuchFileException) {
                        LOGGER.error("Unable to load resource pack icon for $name.", e)
                    }
                    defaultPackIcon.await()
                }
            }
            return box(Modifier.fillWidth().height(28f).color(EssentialPalette.COMPONENT_BACKGROUND).hoverScope()) {
                if (isAppliedList) {
                    floatingBox(Modifier.alignHorizontal(Alignment.Start(-6f)).effect { ScissorEffect(mainPane) }) {
                        val hoveredOrParentHovered = Modifier.then {
                            makeHoverScope(hoveredStateV2() or hoverScopeV2(parentOnly = true));
                            {}
                        }
                        icon(EssentialPalette.DRAG_ICON, Modifier.color(EssentialPalette.TEXT_DISABLED)
                            .hoverColor(EssentialPalette.TEXT_MID_GRAY).then(hoveredOrParentHovered))
                    }
                }
                row(Modifier.fillWidth(padding = 5f).fillHeight()) {
                    row(Modifier.fillHeight().fillRemainingWidth()) {
                        box(Modifier.width(19f).heightAspect(1f)) {
                            ifNotNull(image.asCompletableFuture().toState()) { image ->
                                icon(image, Modifier.fillParent())
                            } `else` {
                                LoadingIcon(2.0)()
                            }
                        }
                        spacer(width = 6f)
                        box(Modifier.fillRemainingWidth()) {
                            text(name, Modifier.alignHorizontal(Alignment.Start).color(EssentialPalette.TEXT).shadow(Color.BLACK), truncateIfTooSmall = true)
                        }
                        spacer(width = 6f)
                    }
                    button(
                        OutlineButtonStyle.GRAY,
                        { appliedPacks.set { if (sha256 in it) it - sha256 else it + sha256 } },
                        Modifier.height(13f).widthAspect(1f).hoverScope().hoverTooltip(appliedPacks.map { if (it.contains(sha256)) "Remove" else "Add" }, position = EssentialTooltip.Position.ABOVE)
                    ) { style ->
                        if_({ sha256 in appliedPacks() }) {
                            box(Modifier.width(5f).height(1f).textStyle(style).shadow(Color.BLACK)) // Minus icon
                        } `else` {
                            icon(EssentialPalette.PLUS_5X, Modifier.textStyle(style).shadow(Color.BLACK))
                        }
                    }
                    spacer(width = 1f)
                }
            }.apply {
                if (isAppliedList) {
                    onLeftClick {
                        draggedOffset.set(UMouse.Scaled.y.toFloat() - containerDontUseThisUnlessYouReallyHaveTo.getTop())
                        draggedSha256.set(sha256)
                    }
                    onMouseRelease {
                        Window.enqueueRenderOperation {
                            draggedOffset.set(0f)
                            draggedSha256.set(null)
                        }
                    }
                }
            }
        }

        val packEntries = mutableMapOf<Pair<Sha256, ResourcePackName>, DetachedLayout>()
        fun LayoutScope.packEntry(sha: Sha256, name: ResourcePackName, path: Deferred<Path>) {
            val entry = packEntries.getOrPut(Pair(sha, name)) {
                detachedLayout {
                    packItem(sha, name, path)
                }
            }
            entry()
        }
        val displayListIndices = memo { displayedList().indices.toList() }.toListState()

        if_({ displayListIndices().isNotEmpty() }) {
            box(Modifier.fillWidth().childBasedMaxHeight()) {
                lateinit var listComponents: UIComponent
                column(Modifier.fillWidth(), Arrangement.spacedBy(5f)) {
                    row(Modifier.fillWidth(), Arrangement.spacedBy(3f)) {
                        text(title, Modifier.color(EssentialPalette.TEXT_DISABLED), shadow = false)
                        box(Modifier.fillRemainingWidth().height(1f).color(EssentialPalette.DIVIDER))
                    }
                    listComponents = column(Modifier.fillWidth(), Arrangement.spacedBy(3f)) {
                        forEach(displayListIndices) { slotIndex ->
                            val entry = memo {
                                val sha = displayedList().getOrNull(slotIndex) ?: return@memo null
                                if (draggedSha256() == sha) return@memo null
                                val pack = allPacks()[sha] ?: return@memo null
                                Triple(sha, pack.first, pack.second)
                            }

                            box(Modifier.fillWidth().childBasedHeight().tag(IndexTag(slotIndex))) {
                                ifNotNull(entry) { (sha, name, path) ->
                                    packEntry(sha, name, path)
                                } `else` {
                                    spacer(height = 28f)
                                }
                            }
                        }
                    }
                }

                if (isAppliedList) {
                    val draggedEntry = memo {
                        val sha = draggedSha256() ?: return@memo null
                        val pack = allPacks()[sha] ?: return@memo null
                        Triple(sha, pack.first, pack.second)
                    }
                    ifNotNull(draggedEntry) { (sha, name, path) ->
                        val yModifier = Modifier.then(State {
                            val offset = draggedOffset()
                            BasicYModifier { MousePositionConstraint() - offset.pixels }
                        })
                        val draggedSlot = floatingBox(Modifier.fillWidth()
                            .effect { ScissorEffect(mainPane) }
                            .effect { AlphaEffect(0.7f.state()) }
                            .then(yModifier)
                        ) {
                            packEntry(sha, name, path)
                        }

                        var lastDraggedTop = 0f
                        draggedSlot.addUpdateFunc onAnimationFrame@{ _, _ ->
                            val draggedTop = draggedSlot.getTop()
                            if (lastDraggedTop == draggedTop) {
                                return@onAnimationFrame
                            }
                            lastDraggedTop = draggedTop

                            val slots = listComponents.findChildrenByTag<IndexTag>()
                            val closestItem = slots.minBy { abs(draggedTop - it.getTop()) }
                            val insertIndex = closestItem.getTag<IndexTag>()?.index ?: return@onAnimationFrame
                            appliedPacks.set { list ->
                                list.toMutableList().apply {
                                    val item = find { it == sha } ?: return@apply
                                    val currentIndex = indexOf(item)
                                    if (currentIndex != insertIndex) {
                                        remove(item)
                                        add(insertIndex, item)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun LayoutScope.layoutPacksContent() {
        column(Modifier.fillWidth().childBasedHeight(10f), Arrangement.spacedBy(10f)) {
            layoutPacksResourcePacks()
        }
    }

    private fun LayoutScope.layoutPacksResourcePacks() {
        setting("Share Resource Packs") {
            val state = memo { worldManager.shareResourcePacks() }
            var updateJob: Job? = null
            val toggle = FullEssentialToggle(
                state.withSetter { mapper ->
                    val oldValue = getUntracked()
                    val newValue = mapper(oldValue)
                    if (oldValue == newValue) return@withSetter
                    updateJob?.cancel()
                    updateJob = coroutineScope.launch {
                        worldManager.updateShareResourcePacks(newValue)
                    }
                }
            )
            toggle()
        }

        platform.localResourcePackIndex.update()
        val indexedPacks = platform.localResourcePackIndex.resourcePacks
        val allPacks = memo {
            val availablePacks = indexedPacks().entries.map { (sha, pathAndName) ->
                val (path, name) = pathAndName
                sha to (name to CompletableDeferred(path))
            }
            val appliedPacks = (worldManager.resourcePackFiles() ?: emptyList()).map {
                it.first to (it.second to it.third)
            }
            (availablePacks + appliedPacks).toMap()
        }
        val appliedResourcePacks = mutableStateOf(worldManager.resourcePackFiles.getUntracked()?.map { it.first } ?: emptyList())
        val availableResourcePacks = memo {
            allPacks().map { it.key } - appliedResourcePacks().toSet()
        }
        if_({ allPacks().isEmpty() }) {
            text("None found...", Modifier.color(EssentialPalette.TEXT_DISABLED))
        } `else` {
            layoutPacksSection({ "Selected Resource Packs" }, allPacks, availableResourcePacks, appliedResourcePacks, true)
            layoutPacksSection({ "Resource Packs" }, allPacks, availableResourcePacks, appliedResourcePacks, false)
        }
        val delayedResourcePackUpdatingState = platform.localResourcePackIndex.isUpdating
            .asyncMap(coroutineScope) { loading -> if (loading) delay(500); loading }
            .letState { it ?: false }
        if_(delayedResourcePackUpdatingState) {
            image(EssentialPalette.LOADING_ANIMATION, Modifier.color(EssentialPalette.TEXT_HIGHLIGHT).shadow(Color.BLACK))
        }
        appliedResourcePacks.onChange(this@WorldHostingModal) { changedPacks ->
            commitResourcePacks = {
                GlobalScope.launch {
                    worldManager.updateResourcePacks(changedPacks
                            .mapNotNull { sha -> allPacks.getUntracked()[sha] }
                            .map { it.second.await() to it.first }
                    )
                }
            }
        }
    }

    private fun LayoutScope.layoutTopButtons() {
        bind(activeTab) { tab ->
            when (tab) {
                Tab.GameRules -> {
                    val input = UITextInput("Search")
                    gameRuleSearchSource.set(input.textState)
                    box(Modifier.fillWidth().height(18f).shadow(EssentialPalette.BLACK)) {
                        essentialInput(input, Modifier.shadow(Color.BLACK), icon = EssentialPalette.SEARCH_7X, iconAndInputPadding = 3f, outlineColor = EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT)
                    }
                }

                else -> {}
            }
        }
    }

    override fun LayoutScope.layoutButtons() {
        bind(activeTab) { tab ->
            when (tab) {
                Tab.Debug -> {
                }
                Tab.Members -> {
                    fun hostingAndMemberButtons() {
                        row(Modifier.fillWidth(), Arrangement.equalWeight(5f)) {
                            if_({ worldManager.host() != null }) {
                                button(OutlineButtonStyle.RED, "Stop Hosting", ::stopLocalHosting)
                            } `else` {
                                button(OutlineButtonStyle.GREEN, "Start Hosting", ::startLocalHosting)
                            }
                            button(OutlineButtonStyle.BLUE, "Add Members", ::addMembers)
                        }
                    }
                    hostingAndMemberButtons()
                }
                Tab.General -> {}
                Tab.GameRules -> {}
                Tab.Packs -> {}
            }
        }
    }

    private fun LayoutScope.button(
        style: StyledButton.Style,
        action: suspend () -> Unit,
        modifier: Modifier = Modifier,
        disabled: State<Boolean> = stateOf(false),
        content: LayoutScope.(style: State<MenuButton.Style>) -> Unit,
    ) = button(stateOf(style), action, modifier, disabled, content)

    private fun LayoutScope.button(
        style: State<StyledButton.Style>,
        action: suspend () -> Unit,
        modifier: Modifier = Modifier,
        disabled: State<Boolean> = stateOf(false),
        content: LayoutScope.(style: State<MenuButton.Style>) -> Unit,
    ): OutlineButton {
        val actionRunning = mutableStateOf(false)
        val effectiveDisabled = State { disabled() || actionRunning() }

        return outlineButton(Modifier.shadow().then(modifier), style, disabled) { currentStyle ->
            content(currentStyle)
        }.onLeftClick { event ->
            if (effectiveDisabled.getUntracked()) {
                return@onLeftClick
            }
            USound.playButtonPress()
            event.stopPropagation()
            coroutineScope.launch {
                actionRunning.set(true)
                try {
                    action()
                } finally {
                    actionRunning.set(false)
                }
            }
        }
    }

    private fun LayoutScope.button(
        style: StyledButton.Style,
        label: String,
        action: suspend () -> Unit,
        modifier: Modifier = Modifier,
        disabled: State<Boolean> = stateOf(false),
    ) {
        button(style, action, modifier, disabled) { currentStyle ->
            text(label, Modifier.alignVertical(Alignment.Center(true)).textStyle(currentStyle))
        }
    }

    private suspend fun startLocalHosting() {
        val world = worldsManager.integratedServerWorld.getUntracked()
        val currentServer = ServerType.current()

        modalFlow(platform.createModalManager()) {
            if (currentServer != null && (currentServer !is ServerType.Singleplayer || (world != null && world != worldManager))) {
                if (!continueModal("You'll be moved to this world.\nDo you want to continue?")) {
                    throw CancellationException("User does not want to continue")
                }
            }

            if (worldManager.localWorldInfo.getUntracked().invited.isEmpty()) {
                addMembersModal("Add members to your world\nbefore you start hosting")
            }
        }

        if (world != worldManager) {
            worldsManager.startLocalWorld(worldManager)
        }

        worldManager.localShareSession.set(UUID.randomUUID())
    }

    private suspend fun stopLocalHosting() = modalFlow(platform.createModalManager()) {
        if (worldsManager.integratedServerManager.getUntracked()?.connectedGuests?.getUntracked()?.isNotEmpty() == true) {
            disconnectAllPlayersWarningModal()
        }

        worldManager.localShareSession.set(null)
    }

    private fun addMembers() = launchModalFlow(platform.createModalManager()) { addMembersModal() }

    private suspend fun ModalFlow.addMembersModal(title: String = "Select friends to invite\nto ${worldManager.name.getUntracked()}") {
        val socialStates = platform.createSocialStates()
        val friends = socialStates.relationships.friends.mapList { list ->
            list.filter { !socialStates.isSuspended(it)() && it !in worldManager.members() }
        }
        val addedUsers = selectPlayersModal(title, friends) ?: throw CancellationException()

        worldManager.updateLocalWorldInfo { it.copy(invited = it.invited + addedUsers) }
    }

    private suspend fun toggleOp(uuid: UUID) {
        worldManager.updateGameSettings { it.copy(ops = if (uuid in it.ops) it.ops - uuid else it.ops + uuid) }
    }

    private suspend fun removeMember(uuid: UUID) {
        modalFlow(platform.createModalManager()) {
            removeMemberConfirmationModal(uuid)
        }
        worldManager.updateLocalWorldInfo { it.copy(
            invited = it.invited - uuid,
            gameSettings = it.gameSettings.copy(ops = it.gameSettings.ops - uuid),
        ) }
    }

    override fun close() {
        if (suppressClose) {
            return
        }
        super.close()
    }

    private val keyEventListener: UIComponent.(Char, Int) -> Unit = keyListener@{ _, keyCode ->
        when (keyCode) {
            UKeyboard.KEY_F3 -> isDebugEnabled.set { !it }
        }
    }

    override fun onOpen() {
        super.onOpen()
        Window.of(this).onKeyType(keyEventListener)
    }

    override fun onClose() {
        Window.of(this).keyTypedListeners.remove(keyEventListener)
        commitResourcePacks()
        commitResourcePacks = {}
        super.onClose()
    }

    enum class Tab(val displayName: String, val hasTopButtons: Boolean = false, val hasBottomButtons: Boolean = true) {
        Debug("Debug"),
        Members("Members"),
        General("General"),
        GameRules("Game Rules", true, false),
        Packs("Packs", hasBottomButtons = false),
    }

    private data class IndexTag(val index: Int) : Tag

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WorldHostingModal::class.java)

        private val isDebugEnabled = mutableStateOf(false)
    }
}

