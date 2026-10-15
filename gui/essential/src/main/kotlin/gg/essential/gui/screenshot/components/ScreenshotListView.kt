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
package gg.essential.gui.screenshot.components

import gg.essential.config.EssentialConfig
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.Effect
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.elementa.renderer.ElementaExtractor
import gg.essential.gui.EssentialPalette
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toListState
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.LayoutDslComponent
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignVertical
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.column
import gg.essential.gui.layoutdsl.fillHeight
import gg.essential.gui.layoutdsl.fillParent
import gg.essential.gui.layoutdsl.fillRemainingHeight
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.height
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.scrollable
import gg.essential.gui.layoutdsl.spacer
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.withHoverState
import gg.essential.gui.modals.ensurePrerequisites
import gg.essential.gui.overlay.launchModalFlow
import gg.essential.gui.screenshot.DateRange
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.ScreenshotInfo
import gg.essential.gui.screenshot.createDateOnlyCalendar
import gg.essential.gui.screenshot.providers.WindowedProvider
import gg.essential.gui.util.limitToRangeFast
import gg.essential.gui.util.walkTopMostNotNull
import gg.essential.handlers.screenshot.ClientScreenshotMetadata
import gg.essential.network.connectionmanager.features.Feature
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.universal.USound
import gg.essential.util.*
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.utils.onLeftClick
import java.util.*

abstract class ScreenshotListView(
    val screenshotManager: IScreenshotManager,
    val providerManager: ScreenshotProviderManager,
    private val searchText: State<String>,
    private val alwaysVisible: State<List<ScreenshotId>> = stateOf(emptyList()),
) : LayoutDslComponent {

    val numberOfItemsPerRow = EssentialConfig.screenshotBrowserItemsPerRowState

    private val selectedTab = mutableStateOf(Tab.ALL)

    private val emptyScreenshotText = selectedTab.map {
        when (it) {
            Tab.ALL -> "You have no screenshots"
            Tab.UPLOADED -> "No screenshots uploaded"
            Tab.LIKED -> "No favorite screenshots"
        }
    }

    private lateinit var navigation: UIComponent
    private lateinit var screenshotScissorBox: UIComponent
    lateinit var scroller: ScrollComponent
    private lateinit var container: UIComponent

    val items = State {
        val alwaysVisible = alwaysVisible()
        val tab = selectedTab()
        val searchText = searchText()
        screenshotManager.screenshots()
            .filter { info ->
                info.id in alwaysVisible ||
                        filter(info.metadata, tab)
                        && ScreenshotProperties(info.id, info.metadata).matchesSearch(searchText)
            }
    }.toListState()

    val groups = State { group(items()) }.toListState()

    override fun LayoutScope.layout(modifier: Modifier) {
        column(Modifier.fillWidth(padding = 11f).fillHeight().then(modifier)) {
            container = containerDontUseThisUnlessYouReallyHaveTo

            navigation = row(Modifier.fillWidth().height(28f), Arrangement.SpaceBetween) {
                row(Arrangement.spacedBy(12f)) {
                    for (value in Tab.entries) {
                        tabButton(value)
                    }
                }
                ScreenshotItemsSlider(numberOfItemsPerRow, ::scroller)()
            }

            box(Modifier.fillWidth().fillRemainingHeight()) {
                // Negative padding so the hover outline is not scissored out of existence when against the edge
                screenshotScissorBox = box(Modifier.fillParent(padding = -hoverOutlineWidth)) {
                    scroller = scrollable(Modifier.fillParent(padding = hoverOutlineWidth), vertical = true) {
                        layoutScrollerContent()
                    }
                }
            }
        }


        scroller.removeEffect<ScissorEffect>()
        scroller.enableEffect(ScissorEffect(screenshotScissorBox))

        val percentState = mutableStateOf(0f)
        val heightState = mutableStateOf(0f)
        val hidden = mutableStateOf(true)

        scroller.addScrollAdjustEvent(false) { percent, percentageOfParent ->
            percentState.set(percent)
            heightState.set((1f / percentageOfParent) * scroller.getHeight())
            hidden.set(percentageOfParent == 1f)
        }

        screenshotScissorBox.createGradient(true, 30.pixels, percentState = percentState, heightState = heightState, hidden = hidden)
        screenshotScissorBox.createGradient(false, 30.pixels, percentState = percentState, heightState = heightState, hidden = hidden)

        scroller.addUpdateFunc { _, _ ->
            updateVisibleTextures()
        }
        // FIXME Workaround for ScrollComponent not updating the position of its children until its `draw` method.
        //       Should only be needed until we switch to the V2 scroll component.
        scroller.enableEffect(object : Effect() {
            override fun extractBeforeChildren(extractor: ElementaExtractor) {
                updateVisibleTextures()
            }
        })
    }

    private fun LayoutScope.tabButton(tab: Tab) {
        val text = text(
            tab.niceName,
            Modifier.withHoverState { hovered ->
                Modifier.color {
                    when {
                        selectedTab() == tab -> EssentialPalette.ACCENT_BLUE
                        hovered() -> EssentialPalette.TEXT_HIGHLIGHT
                        else -> EssentialPalette.TEXT
                    }
                }
            }.hoverScope(),
        )
        text.bindShadowColor(selectedTab.map { if (it == tab) EssentialPalette.BLUE_SHADOW else EssentialPalette.COMPONENT_BACKGROUND }.toV1(text))
        text.onLeftClick {
            if (tab == Tab.UPLOADED) {
                launchModalFlow(platform.createModalManager()) {
                    USound.playButtonPress()
                    ensurePrerequisites(Feature.MEDIA)
                    selectedTab.set(tab)
                }
            } else {
                USound.playButtonPress()
                selectedTab.set(tab)
            }
        }
    }

    private fun LayoutScope.layoutScrollerContent() {
        if_({ groups().isNotEmpty() }) {
            // Intentionally reducing the apparent size of the content such that the header of the top-most group
            // ends up inside our navigation bar
            box(Modifier.fillWidth().childBasedHeight(-screenshotGroupHeaderHeight / 2).alignVertical(Alignment.Start)) {
                layoutGroups(Modifier.alignVertical(Alignment.End))
            }
        } `else` {
            column(Modifier.alignVertical(Alignment.Start)) {
                spacer(height = 4f)
                text(emptyScreenshotText)
            }
        }
    }

    private fun LayoutScope.layoutGroups(modifier: Modifier) {
        column(Modifier.fillWidth().then(modifier)) {
            forEach(groups.mapEach { Pair(it.first, it.second) }) { (range, time) ->
                screenshotDateGroup(range, time, navigation, container) {
                    val items = groups.map { groups ->
                        groups.find { it.first == range && it.second == time }?.third ?: emptyList()
                    }.toListState()
                    forEach(items) { id ->
                        layoutItem(id)
                    }
                }
            }
            spacer(height = screenshotPadding / 2)
        }
    }

    abstract fun LayoutScope.layoutItem(id: ScreenshotId)

    private fun filter(metadata: ClientScreenshotMetadata?, tab: Tab): Boolean {
        return when (tab) {
            Tab.ALL -> {
                true
            }
            Tab.UPLOADED -> {
                metadata?.ownedMediaId != null
            }
            Tab.LIKED -> {
                metadata?.favorite ?: false
            }
        }
    }

    private fun group(items: List<ScreenshotInfo>): List<Triple<DateRange, Long, List<ScreenshotId>>> {
        val predefinedGroups = TreeMap<Long, Triple<DateRange, Long, MutableList<ScreenshotId>>>()
        val otherGroups = mutableMapOf<Long, Triple<DateRange, Long, MutableList<ScreenshotId>>>()

        for (value in DateRange.entries) {
            if (value == DateRange.MONTH_OTHER) continue
            val startTime = value.getStartTime()
            if (value == DateRange.EARLIER_MONTH && startTime > DateRange.LAST_WEEK.getStartTime()) continue
            predefinedGroups[startTime] = Triple(value, startTime, mutableListOf())
        }

        for (item in items) {
            val imageTime = item.time
            val entry = predefinedGroups.floorEntry(imageTime.time)
            val group = if (entry != null) {
                entry.value
            } else {
                val time = createDateOnlyCalendar(imageTime.time)
                    .apply { set(Calendar.DAY_OF_MONTH, 1) }
                    .time.time
                otherGroups.getOrPut(time) { Triple(DateRange.MONTH_OTHER, time, mutableListOf()) }
            }
            group.third.add(item.id)
        }

        predefinedGroups.values.removeIf { it.third.isEmpty() }
        // otherGroups are non-empty by construction

        val result = mutableListOf<Triple<DateRange, Long, List<ScreenshotId>>>()
        result.addAll(predefinedGroups.values)
        result.sortBy { -it.first.ordinal }
        result.addAll(otherGroups.values)
        // otherGroups are sorted by construction
        return result
    }

    private fun updateVisibleTextures() {
        val visibleComponents = scroller
            .walkTopMostNotNull { it as? ScreenshotPreview }
            .limitToRangeFast(screenshotScissorBox.getTop(), screenshotScissorBox.getBottom())
            .toList()

        var window = WindowedProvider.Window(IntRange(Int.MAX_VALUE, Int.MIN_VALUE), false)
        for (component in visibleComponents) {
            val index = providerManager.indexById[component.screenshotId]
            if (index != null) {
                window = window.expandToInclude(index)
            }
        }
        val textures = providerManager.provide(window)

        for (component in visibleComponents) {
            component.imgTexture.set(textures[component.screenshotId])
        }
    }

    private enum class Tab(val niceName: String) {
        ALL("All"),
        LIKED("Favorites"),
        UPLOADED("Uploads"),
    }
}

