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
package gg.essential.gui.vigilancev2

import gg.essential.Essential
import gg.essential.api.gui.Slot
import gg.essential.data.VersionData
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.CopyConstraintFloat
import gg.essential.elementa.constraints.MaxConstraint
import gg.essential.elementa.constraints.MinConstraint
import gg.essential.elementa.dsl.boundTo
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.gui.EssentialPalette
import gg.essential.gui.InternalEssentialGUI
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.Untracked.invoke
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.layoutdsl.*
import gg.essential.gui.modals.communityRulesModal
import gg.essential.gui.modals.updateAvailableModal
import gg.essential.gui.notification.Notifications
import gg.essential.gui.vigilancev2.components.vigilanceCategoryTextColor
import gg.essential.gui.vigilancev2.palette.VigilancePalette
import gg.essential.network.connectionmanager.telemetry.FeatureSessionTelemetry
import gg.essential.universal.UDesktop
import gg.essential.universal.UMinecraft
import gg.essential.universal.USound
import gg.essential.util.AutoUpdate
import gg.essential.util.GuiUtil
import gg.essential.util.GuiUtil.launchModalFlow
import gg.essential.util.onLeftClick
import gg.essential.util.openInBrowser
import gg.essential.vigilance.data.PropertyData
import java.awt.Color
import java.net.URI

const val bottomBarHeight = 30f

class VigilanceV2SettingsGui @JvmOverloads constructor(
    properties: ListState<PropertyData>,
    initialCategory: String? = null,
): InternalEssentialGUI(ElementaVersion.V10, "Essential Settings") {
    constructor(properties: List<PropertyData>, initialCategory: String? = null) : this(
        stateOf(properties).toListState(),
        initialCategory
    )

    private val reference = ReferenceHolderImpl()

    val categories = stateBy {
        properties()
            .groupBy { it.attributesExt.category }
            .map { (name, data) ->
                Category(
                    name,
                    data.groupBy { it.attributesExt.subcategory }
                        .map { (subName, subData) -> SubCategory(subName, subData) }
                )
            }
    }.toListState()

    val defaultCategoryName = initialCategory?.let { name ->
        categories.getUntracked().firstOrNull { it.name.equals(name, ignoreCase = true) }?.name
    } ?: categories.getUntracked().first().name

    val searchState: MutableState<String>
    val currentCategoryName: MutableState<String> = mutableStateOf(defaultCategoryName)

    init {
        container.constrain {
            width = MaxConstraint(MinConstraint(width, 595.pixels), 558.pixels)
        }
        scissorBox.constrain {
            width = CopyConstraintFloat() boundTo container
        }

        titleBar.layout {
            searchState = vigilanceTitleBar(outlineThickness, { leftTitleBarContent() })
        }

        val scrollbar: UIComponent
        rightDivider.layout {
            box(Modifier.width(outlineThickness).fillHeight(topPadding = 30f)) {
                scrollbar = box(Modifier.fillParent().color(VigilancePalette.SCROLLBAR))
            }
        }

        val sidebarScroller: UIComponent

        content.layout {
            sidebarScroller = vigilanceContent(
                outlineThickness,
                categories,
                currentCategoryName,
                searchState,
                scrollbar,
                sidebarSections = listOfNotNull<LayoutScope.() -> Unit>(
                    {
                        listOfNotNull(
                            "Changelog" to URI("https://essential.gg/changelog"),
                            null,
                            null,
                            ("Wiki" to URI("https://essential.gg/wiki")),
                            ("Discord" to URI("https://discord.gg/essential")),
                            ("Twitter" to URI("https://x.com/EssentialMod")),
                        ).forEach { (text, uri) -> sidebarLink(text, uri) }

                        val rulesManager = Essential.getInstance().connectionManager.rulesManager
                        if_(rulesManager.hasRules) {
                            sidebarElement("Community Rules") {
                                launchModalFlow {
                                    communityRulesModal(rulesManager, UMinecraft.getSettings().language, false)
                                }
                            }
                        }
                    },
                    null,
                    { versionInformation() },
                ),
                bottomSidebarContent = { bottomSidebarContent() },
            )
        }

        bottomDivider.layout {
            if_(AutoUpdate.updateAvailable) {
                row(
                    Modifier.fillWidth().height(bottomBarHeight).alignVertical(Alignment.End),
                    Arrangement.spacedBy(0f, FloatPosition.START)
                ) {
                    box(Modifier.width(outlineThickness).fillHeight().color(EssentialPalette.LIGHT_DIVIDER))

                    spacer(width = sidebarScroller)

                    box(Modifier.width(outlineThickness).fillHeight().color(EssentialPalette.LIGHT_DIVIDER))
                }
            }
        }

        var oldCategory: String? = null
        effect(reference) {
            val newCategory = currentCategoryName().takeIf { screenOpen() }
            oldCategory?.let { FeatureSessionTelemetry.endEvent("${this@VigilanceV2SettingsGui::class.qualifiedName}-$it") }
            newCategory?.let { FeatureSessionTelemetry.startEvent("${this@VigilanceV2SettingsGui::class.qualifiedName}-$it") }
            oldCategory = newCategory
        }
    }

    private fun LayoutScope.leftTitleBarContent() {

        iconButton(EssentialPalette.MC_FOLDER_8X7, tooltipText = "Open Minecraft Folder") {
            UDesktop.open(UMinecraft.getMinecraft().mcDataDir)
        }
    }

    private fun LayoutScope.bottomSidebarContent() {

        val updateButtonModifier = Modifier
            .childBasedWidth(10f)
            .childBasedHeight(5f)
            .color(EssentialPalette.GREEN_BUTTON)
            .hoverColor(EssentialPalette.GREEN_BUTTON_HOVER)
            .shadow(Color.BLACK)
            .onLeftClick {
                GuiUtil.launchModalFlow { updateAvailableModal() }
            }

        if_(AutoUpdate.updateAvailable) {
            bottomBar {
                box(Modifier.fillParent().color(EssentialPalette.COMPONENT_BACKGROUND)) {
                    row(Modifier.fillWidth(padding = 10f), Arrangement.SpaceBetween) {
                        text(
                            "Update Available!",
                            Modifier.color(EssentialPalette.UPDATE_AVAILABLE_GREEN).shadow(EssentialPalette.TEXT_SHADOW)
                        )

                        box(updateButtonModifier.hoverScope()) {
                            text(
                                "Update",
                                Modifier
                                    .color(EssentialPalette.TEXT_HIGHLIGHT)
                                    .shadow(EssentialPalette.TEXT_SHADOW)
                                    .alignBoth(Alignment.Center(true))
                            )
                        }
                    }
                }
            }
        }
    }

    private fun LayoutScope.bottomBar(block: LayoutScope.() -> Unit) {
        // This has been mostly copied from gui.about.components.LeftPane#bottomBar:
        // The design includes the bottom divider in the vertical space for the bottom bar. However, that divider is
        // outside the `content` component, and therefore this component must be $outlineThickness pixels smaller because
        // those pixels aren't inside `content`.
        // We'll then add them back inside the outer box (intentionally going out of bounds) so that the components
        // can be implemented as if the outline was part of the content all along.
        box(Modifier.fillWidth(rightPadding = outlineThickness).height(bottomBarHeight - outlineThickness)) {
            box(Modifier.fillWidth().height(bottomBarHeight).alignVertical(Alignment.Start)) {
                block()
            }
        }
    }

    private fun LayoutScope.versionInformation() {
        val versionText = listOf(
            "Essential Mod v${VersionData.essentialVersion}",
            "#${VersionData.essentialCommit}",
            VersionData.formatPlatform(VersionData.getEssentialPlatform())
        )

        wrappedText(
            versionText.joinToString("\n"),
            Modifier.color(EssentialPalette.TEXT_DISABLED).shadow(EssentialPalette.COMPONENT_BACKGROUND)
                .hoverScope().hoverColor(EssentialPalette.TEXT_MID_GRAY),
        ).onLeftClick {
            USound.playButtonPress()
            UDesktop.setClipboardString(versionText.joinToString(separator = "\n"))
            Notifications.push("Version information copied to clipboard", "") {
                withCustomComponent(Slot.ICON, EssentialPalette.COPY_10X7.create())
            }
        }
    }

    private fun LayoutScope.sidebarLink(text: String, uri: URI) {
        row(
            Modifier.hoverScope().onLeftClick {
                USound.playButtonPress()
                openInBrowser(uri)
            },
            Arrangement.spacedBy(4f)
        ) {
            text(text, vigilanceCategoryTextColor())
            icon(EssentialPalette.ARROW_UP_RIGHT_5X5, vigilanceCategoryTextColor())
        }
    }

    private fun LayoutScope.sidebarElement(text: String, onClick: () -> Unit) {
        row(
            Modifier.hoverScope().onLeftClick {
                USound.playButtonPress()
                onClick()
            }
        ) {
            text(text, vigilanceCategoryTextColor())
        }
    }

    override fun updateGuiScale() {
        newGuiScale = GuiUtil.getGuiScale()
        super.updateGuiScale()
    }
}
