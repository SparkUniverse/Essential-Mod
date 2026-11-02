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
package gg.essential.gui.menu

import gg.essential.Essential
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import gg.essential.elementa.state.BasicState
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.*
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.flatten
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.systemTime
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.elementa.state.v2.withSystemTime
import gg.essential.gui.layoutdsl.BasicXModifier
import gg.essential.gui.layoutdsl.BasicYModifier
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.layout
import gg.essential.gui.proxies.ProxiesHandler
import gg.essential.gui.proxies.mountWithProxy
import gg.essential.gui.wardrobe.Wardrobe
import gg.essential.gui.wardrobe.WardrobeCategory
import gg.essential.util.GuiUtil
import gg.essential.util.bindEssentialTooltip
import gg.essential.gui.util.hoveredState
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.USession
import gg.essential.util.toShortString
import gg.essential.vigilance.utils.onLeftClick

class LeftSideBar(
    window: Window,
    private val proxyHandler: ProxiesHandler,
    private val topButtonAndMultiplayer: UIContainer,
    private val bottomButton: UIContainer,
    rightSideBar: UIContainer,
    leftContainer: UIContainer,
) : UIContainer() {

    private val connectionManager = Essential.getInstance().connectionManager

    // As a field so that there is a strong reference to it. Otherwise, it will be GC'd
    private val allSales = connectionManager.saleNoticeManager.saleState

    private val currentSale = memo {
        val sales = allSales().toList()
        if (sales.isEmpty()) {
            null
        } else {
            val nowMs = systemTime().toEpochMillis()
            val index = (nowMs / SALE_BANNER_CYCLE_TIME_MS).getValue() % sales.size
            sales[index.toInt()]
        }
    }
    private val isSale = currentSale.map { it != null }
    private val isFakeSale = currentSale.map { it?.discountPercent == 0 } // 0% off sales are used to show a notice without a sale

    private val playerWardrobeContainer by UIContainer().constrain {
        x = CenterConstraint()
        width = ChildBasedMaxSizeConstraint()
        height = ChildBasedSizeConstraint()
    }.constrain {
        y = (CenterConstraint() boundTo rightSideBar).coerceAtLeast(0.pixels(alignOpposite = true) boundTo bottomButton)
    } childOf this

    private val player by platform.newUIPlayer(
        camera = stateOf(null),
        profile = stateOf(null),
        cosmetics = USession.active
            .map { connectionManager.cosmeticsManager.infraEquippedOutfitsManager.getVisibleCosmeticsState(it.uuid) }
            .flatten(),
    ).constrain {
        // x set in init
        width = AspectConstraint()
        height = 120.pixels
    }

    private val wardrobeButton by MenuButton(BasicState("Wardrobe"), textAlignment = MenuButton.Alignment.LEFT) {
        openWardrobe()
    }.setIcon(
        BasicState(EssentialPalette.COSMETICS_10X7),
        rightAligned = true,
        iconWidth = 10f,
        iconHeight = 7f,
    ).constrain {
        width = 80.pixels
        height = 20.pixels
    }.setTooltip("Wardrobe")
        .bindCollapsed(BasicState(true), 20f)

    private val wardrobeButtonContainerModifier =
        BasicXModifier { CenterConstraint() }.then(
            BasicYModifier { SiblingConstraint(9f) }
        )

    init {
        val wardrobeContainer: UIComponent

        playerWardrobeContainer.layout {
            mountWithProxy(proxyHandler, "player") { player() }

            wardrobeContainer = box(wardrobeButtonContainerModifier) {
                mountWithProxy(proxyHandler, "wardrobe_2") { wardrobeButton() }
            }
        }

        constrain {
            x = basicXConstraint { (topButtonAndMultiplayer.getLeft() / 2) - (it.getWidth() / 2) }
                .coerceAtMost(17.pixels(alignOutside = true) boundTo topButtonAndMultiplayer)
            y = 0.pixels boundTo playerWardrobeContainer
            width = 100.percent boundTo playerWardrobeContainer
            height = ChildBasedSizeConstraint()
        }

        // we use a container here so the wardrobe button's vanilla proxy position changes do not affect this
        player.constrain { x = CenterConstraint() boundTo wardrobeContainer }

        val saleName = currentSale.map {
            (it?.name?.uppercase() ?: "")
        }
        val saleExpires = memo {
            val sale = currentSale() ?: return@memo ""
            withSystemTime { now ->
                val timeLeft = now.until(sale.expiration)
                "${timeLeft.toShortString(false)} left"
            }
        }

        val saleLines = currentSale.map {
            if (it?.displayRemainingTimeOnBanner == true) {
                listOf(saleName, saleExpires)
            } else {
                listOf(saleName)
            }
        }
        // Big sale flag
        TextFlag(
            isFakeSale.map { fakeSale ->
                if (fakeSale) MenuButton.NOTICE_GREEN else MenuButton.LIGHT_RED
            },
            text = saleLines,
        ).constrain {
            x = CenterConstraint() boundTo wardrobeButton
            height += 1.pixel
            y = SiblingConstraint(3f) boundTo wardrobeButton
            width = width.coerceIn(72.pixels, 78.pixels)
        }.onLeftClick {
            openWardrobe(WardrobeCategory.get(currentSale.get()?.category))
        }.bindParent(this, isSale).apply {
            bindEssentialTooltip(hoveredState() and currentSale.map { it?.tooltip != null }.toV1(this), currentSale.map { it?.tooltip ?: ""}.toV1(this), EssentialTooltip.Position.ABOVE)
        }

        val hasAnyNewCosmetics = connectionManager.cosmeticNotices.hasAnyNewCosmetics

        // New cosmetics flag
        TextFlag(
            stateOf(MenuButton.NOTICE_GREEN),
            MenuButton.Alignment.CENTER,
            stateOf("NEW"),
        ).constrain {
            y = CenterConstraint() boundTo wardrobeButton
            x = 3.pixels(alignOpposite = true, alignOutside = true) boundTo wardrobeButton
        }.onLeftClick { wardrobeButton.runAction() }.bindParent(
            leftContainer,
            hasAnyNewCosmetics,
            delayed = true,
        )

        addUpdateFunc { _, _ -> recordSaleBannerImpression() }
    }

    private fun openWardrobe(category: WardrobeCategory? = null) {
        GuiUtil.openScreen { Wardrobe(category) }
    }

    private fun recordSaleBannerImpression() {
        val currentSale = currentSale.get() ?: return
        if (bannerImpressions.add(currentSale.name)) {
            connectionManager.telemetryManager.enqueue(ClientTelemetryPacket("COSMETICS_SALE_BANNER_IMPRESSION", mapOf(
                "sale_name" to currentSale.name,
            )))
        }
    }

    companion object {
        const val SALE_BANNER_CYCLE_TIME_MS = 3000L
        private val bannerImpressions = mutableSetOf<String>() // Name of banners that have been shown to the user
    }
}
