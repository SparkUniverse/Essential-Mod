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
package gg.essential.gui

import gg.essential.config.LoadsResources
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.font.BasicFontRenderer
import gg.essential.elementa.font.data.Font
import gg.essential.elementa.state.BasicState
import gg.essential.elementa.state.State
import gg.essential.elementa.utils.withAlpha
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.friends.message.v2.SendState
import gg.essential.gui.image.AnimatedResourceImageFactory
import gg.essential.gui.image.ImageFactory
import gg.essential.gui.image.ImageGeneratorSettings
import gg.essential.gui.image.ResourceImageFactory
import gg.essential.util.UIdentifier
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

import gg.essential.gui.elementa.state.v2.State as StateV2

object EssentialPalette {

    /* Messaging */
    @JvmField
    val SENT_MESSAGE_TEXT: Color = Color(0xE5E5E5)

    @JvmField
    val PENDING_MESSAGE_TEXT: Color = Color(0x969696)

    @JvmField
    val FAILED_MESSAGE_TEXT: Color = Color(0XF5534F)

    @JvmField
    val RECEIVED_MESSAGE_TEXT: Color = Color(0xE5E5E5)

    @JvmField
    val SENT_MESSAGE_BACKGROUND: Color = Color(0x0A82FD)

    @JvmField
    val RECEIVED_MESSAGE_BACKGROUND: Color = Color(0x333333)

    /* Status Indicator */
    @JvmField
    val ONLINE: Color = Color(0x01A552)

    /* Onboarding and General Use */
    @JvmField
    val ESSENTIAL_RED: Color = Color(0xFF4F51)

    @JvmField
    val ESSENTIAL_BLUE: Color = Color(0x1299FF)

    @JvmField
    val ESSENTIAL_YELLOW: Color = Color(0xFFEE3E)

    @JvmField
    val ESSENTIAL_GOLD: Color = Color(0xFFB73E)

    @JvmField
    val ESSENTIAL_GREEN: Color = Color(0x02D98E)

    @JvmField
    val ESSENTIAL_PUKE_GREEN: Color = Color(0x4FCD46)

    @JvmField
    val ESSENTIAL_DARK_BLUE_OR_MAYBE_PURPLE_IDK: Color = Color(0x4F37DB)

    @JvmField
    val ACCENT_HOVER: Color = Color(0x00D469)

    @JvmField
    val MESSAGE_UNREAD: Color = Color(0x132B1F)

    @JvmField
    val MESSAGE_UNREAD_HOVER: Color = Color(0X14412a)

    @JvmField
    val MENU_BACKGROUND: Color = Color(0x000000);

    @JvmField
    val LIGHTEST_BACKGROUND: Color = Color(0x474747)

    @JvmField
    val LIGHT_DIVIDER: Color = Color(0x303030)

    @JvmField
    val DIVIDER: Color = Color(0x474747)

    @JvmField
    val LIGHT_SCROLLBAR: Color = Color(0x555555)

    @JvmField
    val GREEN: Color = Color(0x2BC553)

    @JvmField
    val BLACK: Color = Color(0x000000)

    @JvmField
    val WHITE: Color = Color(0xFFFFFF)

    @JvmField
    val BUTTON_HIGHLIGHT: Color = Color(0x474747)

    @JvmField
    val BUTTON: Color = Color(0x323232)

    @JvmField
    val TEXT: Color = Color(0xBFBFBF)

    @JvmField
    val TEXT_HIGHLIGHT: Color = Color(0xE5E5E5)

    @JvmField
    val TEXT_WARNING: Color = Color(0xCC2929)

    @JvmField
    val LINK: Color = Color(0x3282F5)

    @JvmField
    val LINK_HIGHLIGHT: Color = Color(0x5FA3F8)

    @JvmField
    val COMPONENT_BACKGROUND: Color = Color(0x232323)

    @JvmField
    val COMPONENT_BACKGROUND_HIGHLIGHT: Color = Color(0x323232)

    @JvmField
    val GUI_BACKGROUND: Color = Color(0x181818)

    @JvmField
    val MODAL_BACKGROUND: Color = GUI_BACKGROUND

    @JvmField
    val INPUT_MODAL_BACKGROUND: Color = Color(0X1E1E1E)

    @JvmField
    val MODAL_OUTLINE: Color = Color(0X3F3F3F)

    @JvmField
    val MODAL_WARNING: Color = Color(0xFFAA2B)

    @JvmField
    val TEXT_RED: Color = Color(0xE52222)

    @JvmField
    val TEXT_BLUE: Color = Color(0x5555ff)

    @JvmField
    val GRAY_OUTLINE: Color = Color(0x424242)

    @JvmField
    val TEXT_SHADOW: Color = Color(0x181818)

    @JvmField
    val TEXT_SHADOW_LIGHT: Color = Color(0x3F3F3F)

    @JvmField
    val TEXT_DISABLED: Color = Color(0x757575)

    @JvmField
    val TEXT_DARK_DISABLED: Color = Color(0x5C5C5C)

    @JvmField
    val TEXT_MID_GRAY: Color = Color(0x999999)

    @JvmField
    val TEXT_MID_DARK: Color = Color(0x6A6A6A)

    @JvmField
    val ICON_SHADOW: Color = Color(0x333333)

    @JvmField
    val SCROLLBAR: Color = Color(0x5C5C5C)

    @JvmField
    val COMPONENT_HIGHLIGHT: Color = Color(0x303030)

    @JvmField
    val RED: Color = Color(0xCC2929)

    @JvmField
    val BACKGROUND_RED = Color(0x290F0F)

    @Deprecated("Originally meant for messages, but has since been abused in multiple places.")
    @JvmField
    val MESSAGE_SENT: Color = Color(0x0A82FD)

    @JvmField
    val MESSAGE_SENT_BACKGROUND: Color = Color(0x274673)

    @JvmField
    val MESSAGE_SENT_BACKGROUND_HOVER: Color = Color(0x2F5FA4)

    @JvmField
    val BLUE_SHADOW: Color = Color(0x0A253F)

    @JvmField
    val OTHER_BUTTON_ACTIVE: Color = Color(0x999999)

    @JvmField
    val INPUT_BACKGROUND: Color = Color(0x1C1C1C)

    @Deprecated("Originally meant for messages, but has since been abused in multiple places.")
    @JvmField
    val MESSAGE_SENT_HOVER: Color = Color(0x4BA4FF)

    @JvmField
    val TOAST_PROGRESS: Color = Color(0x999999)

    @JvmField
    val TOAST_BACKGROUND: Color = Color(0x181818)

    @JvmField
    val TOAST_BORDER: Color = Color(0x303030)

    @JvmField
    val TOAST_BORDER_HOVER: Color = Color(0x757575)

    @JvmField
    val MAIN_MENU_BLUE: Color = Color(0x2997FF)

    @JvmField
    val ACCENT_BLUE: Color = Color(0x2997FF)

    @JvmField
    val LOCKED_ORANGE: Color = Color(0xFA7C07)

    @JvmField
    val INFO_ELEMENT_UNHOVERED: Color = Color(0x999999)

    @JvmField
    val ITEM_PINNED: Color = Color(0x0A82FD)

    @JvmField
    val PINNED_COMPONENT_BACKGROUND: Color = Color(0x111E30)

    @JvmField
    val COMPONENT_SELECTED: Color = Color(0x121E30)

    @JvmField
    val COMPONENT_SELECTED_HOVER: Color = Color(0x1E2A3C)

    @JvmField
    val COMPONENT_SELECTED_HOVER_OUTLINE: Color = Color(0x4BA4FF)

    @JvmField
    val COMPONENT_SELECTED_OUTLINE: Color = Color(0x0A82FD)

    @JvmField
    val MESSAGE_HIGHLIGHT: Color = Color(0x333E49)

    @JvmField
    val DARK_TRANSPARENT_BACKGROUND: Color = Color(0xD9121212.toInt(), true)

    @JvmField
    val DARK_TRANSPARENT_BACKGROUND_HIGHLIGHTED: Color = Color(0xD9030C18.toInt(), true)

    @JvmField
    val CART_ACTIVE: Color = Color(0x0A82FD)

    @JvmField
    val CART_ACTIVE_HOVER: Color = Color(0x4BA4FF)

    @JvmField
    val SEARCHBAR_BACKGROUND: Color = Color(0x444444)

    @JvmField
    val SEARCHBAR_BLUE_OUTLINE: Color = Color(0x2D4670)

    /** Accent/Blue */
    @JvmField
    val FEATURED_BLUE: Color = Color(0x0A82FD)
    @JvmField
    val OUTFITS_AQUA: Color = Color(0x17C7FF)
    @JvmField
    val SKINS_GREEN: Color = Color(0x4FE03E)
    @JvmField
    val EMOTES_YELLOW: Color = Color(0xFFD600)
    @JvmField
    val COSMETICS_ORANGE: Color = Color(0xEC8001)

    @JvmField
    val COINS_BLUE: Color = Color(0x274673)

    @JvmField
    val COINS_BLUE_HOVER: Color = Color(0x2F5FA4)

    @JvmField
    val COINS_BLUE_BACKGROUND: Color = Color(0x1E2A3C)

    @JvmField
    val COINS_BLUE_BACKGROUND_HOVER: Color = COINS_BLUE

    @JvmField
    val COINS_BLUE_PRICE_BACKGROUND: Color = COINS_BLUE

    @JvmField
    val COINS_BLUE_PRICE_BACKGROUND_HOVER: Color = Color(0x3073D4)

    @JvmField
    val TEXT_TRANSPARENT_SHADOW: Color = Color(0, 0, 0, 127)

    @JvmField
    val TOGGLE_OFF_BACKGROUND: Color = Color(0x999999)

    @JvmField
    val TOGGLE_OFF_BACKGROUND_HOVERED: Color = Color(0xBFBFBF)

    @JvmField
    val TOGGLE_OFF_FOREGROUND: Color = Color(0x757575)

    @JvmField
    val TOGGLE_ON_BACKGROUND: Color = Color(0x0A82FD)

    @JvmField
    val TOGGLE_ON_BACKGROUND_HOVERED: Color = Color(0x4BA4FF)

    /** Accent/Blue */
    @JvmField
    val BANNER_BLUE: Color = Color(0x0A82FD)
    /** Accent/Red */
    @JvmField
    val BANNER_RED: Color = Color(0XCC2929)
    /** Accent/Green */
    @JvmField
    val BANNER_GREEN: Color = Color(0X2BC553)
    /** Gray/500 */
    @JvmField
    val BANNER_GRAY: Color = Color(0X474747)
    @JvmField
    val BANNER_YELLOW: Color = Color(0xFF8B20)
    @JvmField
    val BANNER_PURPLE: Color = Color(0x6F5CE5)
    @JvmField
    val BANNER_PURPLE_BACKGROUND: Color = Color(120, 86, 255).withAlpha(0.15f)

    @JvmField
    val TEXT_HIGHLIGHT_BACKGROUND: Color = Color(0x507BBA)

    @JvmField
    val OUTFIT_TAG: Color = Color(0X327B44)
    @JvmField
    val OUTFIT_TAG_SHADOW: Color = Color(0x193d23)

    /** Extended Blue/Blue Button */
    @JvmField
    val BLUE_BUTTON: Color = Color(0x274673)
    /** Extended Blue/Blue Button Hover Highlight */
    @JvmField
    val BLUE_BUTTON_HOVER: Color = Color(0x2F5FA4)
    /** Extended Blue/Blue Button Disabled */
    @JvmField
    val BLUE_BUTTON_DISABLED: Color = Color(0x1E2A3C)

    /** Extended Red/Red Button Hover Shadow */
    @JvmField
    val RED_BUTTON: Color = Color(0X642626)
    /** Extended Red/Red Button */
    @JvmField
    val RED_BUTTON_HOVER: Color = Color(0X9F4444)

    /** Extended Green/Green Button Shadow */
    @JvmField
    val GREEN_BUTTON: Color = Color(0X1D4728)
    /** Extended Green/Green Button */
    @JvmField
    val GREEN_BUTTON_HOVER: Color = Color(0X327B44)

    /** Gray/600 */
    @JvmField
    val GRAY_BUTTON: Color = Color(0X323232)
    /** Gray/500 */
    @JvmField
    val GRAY_BUTTON_HOVER: Color = Color(0X474747)
    /** Gray/100 */
    @JvmField
    val GRAY_BUTTON_HOVER_OUTLINE: Color = Color(0xBFBFBF)

    @JvmField
    val YELLOW_BUTTON: Color = Color(0x734317)
    @JvmField
    val YELLOW_BUTTON_HOVER: Color = Color(0xA36226)

    @JvmField
    val PURPLE_BUTTON: Color = Color(0x473999)
    @JvmField
    val PURPLE_BUTTON_HOVER: Color = Color(0x5947BF)

    /** Outline Buttons **/
    @JvmField
    val GREEN_OUTLINE_BUTTON: Color = Color(0x1D4728)
    @JvmField
    val GREEN_OUTLINE_BUTTON_OUTLINE: Color = Color(0x327B44)
    @JvmField
    val GREEN_OUTLINE_BUTTON_HOVER: Color = Color(0x276136)
    @JvmField
    val GREEN_OUTLINE_BUTTON_OUTLINE_HOVER: Color = Color(0x3E9252)

    @JvmField
    val RED_OUTLINE_BUTTON: Color = Color(0x461F1F)
    @JvmField
    val RED_OUTLINE_BUTTON_OUTLINE: Color = Color(0x8B3636)
    @JvmField
    val RED_OUTLINE_BUTTON_HOVER: Color = Color(0x642626)
    @JvmField
    val RED_OUTLINE_BUTTON_OUTLINE_HOVER: Color = Color(0x9F4444)

    @JvmField
    val BLUE_OUTLINE_BUTTON: Color = Color(0x223F69)
    @JvmField
    val BLUE_OUTLINE_BUTTON_OUTLINE: Color = Color(0x3671C7)
    @JvmField
    val BLUE_OUTLINE_BUTTON_HOVER: Color = Color(0x2A5695)
    @JvmField
    val BLUE_OUTLINE_BUTTON_OUTLINE_HOVER: Color = Color(0x5490E8)

    @JvmField
    val GRAY_OUTLINE_BUTTON: Color = Color(0x323232)
    @JvmField
    val GRAY_OUTLINE_BUTTON_OUTLINE: Color = Color(0x5C5C5C)
    @JvmField
    val GRAY_OUTLINE_BUTTON_HOVER: Color = Color(0x474747)
    @JvmField
    val GRAY_OUTLINE_BUTTON_OUTLINE_HOVER: Color = Color(0x757575)

    @JvmField
    val YELLOW_OUTLINE_BUTTON: Color = Color(0x583E14)
    @JvmField
    val YELLOW_OUTLINE_BUTTON_OUTLINE: Color = Color(0x8F621F)
    @JvmField
    val YELLOW_OUTLINE_BUTTON_HOVER: Color = Color(0x74521D)
    @JvmField
    val YELLOW_OUTLINE_BUTTON_OUTLINE_HOVER: Color = Color(0xBA8537)

    @JvmField
    val PURPLE_OUTLINE_BUTTON: Color = Color(0x292063)
    @JvmField
    val PURPLE_OUTLINE_BUTTON_OUTLINE: Color = Color(0x5947BF)
    @JvmField
    val PURPLE_OUTLINE_BUTTON_HOVER: Color = Color(0x352A7A)
    @JvmField
    val PURPLE_OUTLINE_BUTTON_OUTLINE_HOVER: Color = Color(0x6F5CE5)

    /** Gray/900 */
    @JvmField
    val INVALID_SCREENSHOT_TEXT: Color = Color(0x999999)

    /** Extended Green / Green Button Highlight */
    @JvmField
    val UPDATE_AVAILABLE_GREEN: Color = Color(0x3BBD5B)

    @JvmField
    val TOAST_BODY_COLOR: Color = Color(0xBFBFBF)

    @JvmField
    val NEW_TOAST_PROGRESS: Color = Color(0x474747)

    @JvmField
    val NEW_TOAST_PROGRESS_HOVER: Color = Color(0x5C5C5C)

    @JvmField
    val BLACK_SHADOW: Color = Color.BLACK.withAlpha(0.5f)

    @JvmField
    val LEGACY_ICON_YELLOW: Color = Color(0xFBFF4C)

    val LOCKED_ICON: Color = Color(0xF68822)

    val BONUS_COINS_COLOR: Color = Color(0xFDC80A)

    @JvmField
    val PURCHASE_CONFIRMATION_MODAL_SECONDARY: Color = Color(0x232323)

    @JvmField
    val MODAL_TITLE_BLUE: Color = Color(0x0A82FD)

    @JvmField
    val SERVER_DOWNLOAD_ICON: Color = Color(0x1D6AFF)

    @JvmField
    val GRADIENT_MODAL_BORDER_START: Color = Color(0x474747)

    @JvmField
    val GRADIENT_MODAL_BORDER_END: Color = Color(0x4A84D9)

    @JvmField
    val GRADIENT_MODAL_BACKGROUND_START: Color = Color(0x181818)

    @JvmField
    val GRADIENT_MODAL_BACKGROUND_END: Color = Color(0x10161E)

    /** Gray/gray600 */
    @JvmField
    val CHECKBOX_BACKGROUND: Color = Color(0x323232)
    /** Gray/gray500 */
    @JvmField
    val CHECKBOX_BACKGROUND_HOVER: Color = Color(0x474747)
    /** Accent/Blue */
    @JvmField
    val CHECKBOX_SELECTED_BACKGROUND: Color = Color(0x0A82FD)
    /** Accent/Blue Hover */
    @JvmField
    val CHECKBOX_SELECTED_BACKGROUND_HOVER: Color = Color(0x4BA4FF)
    /** Gray/300 */
    @JvmField
    val CHECKBOX_OUTLINE: Color = Color(0x757575)

    fun getMessageColor(hovered: State<Boolean>, sentByClient: Boolean, sendState: SendState): State<Color> {
        return hovered.map {
            if (sendState is SendState.Blocked) {
                COMPONENT_BACKGROUND
            } else if (it) {
                if (sentByClient) {
                    MESSAGE_SENT_BACKGROUND_HOVER
                } else {
                    BUTTON_HIGHLIGHT
                }
            } else {
                if (sentByClient) {
                    MESSAGE_SENT_BACKGROUND
                } else {
                    COMPONENT_BACKGROUND_HIGHLIGHT
                }
            }
        }
    }

    /* Utilities for colors */
    fun getTextColor(hovered: StateV2<Boolean>, enabled: StateV2<Boolean> = stateOf(true)): StateV2<Color> =
        StateV2 {
            if (enabled()) {
                if(hovered()) {
                    TEXT_HIGHLIGHT
                } else {
                    TEXT
                }
            } else {
                TEXT_DISABLED
            }
        }

    fun getTextColor(hovered: State<Boolean>, enabled: State<Boolean>): State<Color> {
        return hovered.zip(enabled).map { (hovered, enabled) ->
            if (enabled) {
                if (hovered) {
                    TEXT_HIGHLIGHT
                } else {
                    TEXT
                }
            } else {
                TEXT_DISABLED
            }
        }
    }

    fun getTextColor(hovered: State<Boolean>): State<Color> = getTextColor(hovered, BasicState(true))

    fun getButtonColor(hovered: State<Boolean>, enabled: State<Boolean>): State<Color> {
        return hovered.zip(enabled).map { (hovered, enabled) ->
            if (enabled) {
                if (hovered) {
                    BUTTON_HIGHLIGHT
                } else {
                    BUTTON
                }
            } else {
                COMPONENT_BACKGROUND
            }
        }
    }

    fun getButtonColor(hovered: State<Boolean>): State<Color> = getButtonColor(hovered, BasicState(true))


    /* Icons */
    @JvmField
    val SHOPPING_CART_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/cart.png")

    @JvmField
    val SHOPPING_CART_12X: ImageFactory = ResourceImageFactory("/assets/essential/textures/cart_12x12.png")

    @JvmField
    val SHOPPING_CART_8X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/cart_8x7.png")

    @JvmField
    val TURN_RIGHT_18X: ImageFactory = ResourceImageFactory("/assets/essential/textures/turn_right_18x18.png")

    @JvmField
    val TURN_LEFT_18X: ImageFactory = ResourceImageFactory("/assets/essential/textures/turn_left_18x18.png")

    @JvmField
    val CHECKMARK_8X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/checkmark_8x6.png")

    @JvmField
    val CHECKMARK_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/checkmark_7x5.png")

    @JvmField
    val PLUS_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/plus_5x5.png")

    @JvmField
    val PLUS_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/plus_7x7.png")

    @JvmField
    val PLUS_10X: ImageFactory = ResourceImageFactory("/assets/essential/textures/plus_10x10.png")

    @JvmField
    val PERSON_4X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/person_4x6.png")

    @JvmField
    val PLUS_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/plus.png")

    @JvmField
    val CANCEL_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/cancel_7x7.png")

    @JvmField
    val CANCEL_10X: ImageFactory = ResourceImageFactory("/assets/essential/textures/cancel_10x10.png")

    @JvmField
    val CANCEL_12X: ImageFactory = ResourceImageFactory("/assets/essential/textures/cancel_12x12.png")

    @JvmField
    val CANCEL_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/cancel.png")

    @JvmField
    val SEARCH_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/search.png")

    @JvmField
    val SEARCH_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/search_7x.png")

    @JvmField
    val BURGER_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/friends/burger.png")

    @JvmField
    val KICK_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/kick.png")

    @JvmField
    val FEATURED_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/featured.png")

    @JvmField
    val WIP_620X: ImageFactory = ResourceImageFactory("/assets/essential/textures/wip.png")

    @JvmField
    val HAT_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/hat.png")

    @JvmField
    val GRID_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/grid.png")

    @JvmField
    val ROW_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/row.png")

    @JvmField
    val SLIDERS_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/sliders.png")

    @JvmField
    val UPLOAD_SKIN_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/upload-skin.png")

    @JvmField
    val PARTNER_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/partner.png")

    @JvmField
    val PARTNER_SMALL_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/partner_small.png")

    @JvmField
    val GIFT_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/gift.png")

    @JvmField
    val SALE_20_PERCENT_25x11: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/20_percent_sale.png")

    @JvmField
    val SALE_INDICATOR_28x11: ImageFactory = ResourceImageFactory("/assets/essential/textures/sale_indicator.png")

    @JvmField
    val NEW_22x11: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/new_22x11.png")

    @JvmField
    val LOCK_OPEN_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/lock_open.png")

    @JvmField
    val LOCK_CLOSED_16x: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/lock_closed.png")

    @JvmField
    val PLAY_ARROW_4x5: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/play_arrow.png")

    @JvmField
    val COSMETICS_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/cosmetics.png")

    @JvmField
    val COSMETICS_OFF_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/cosmetics_off_10x7.png")

    @JvmField
    val COSMETICS_10X_ON: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/cosmetics_on.png")

    @JvmField
    val COSMETICS_10X_OFF: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/cosmetics_off.png")

    @JvmField
    val EMOTES_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/emotes.png")

    @JvmField
    val ESSENTIAL_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/essential.png")

    @JvmField
    val ESSENTIAL_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/logo.png")

    @JvmField
    val FRIENDS_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/friends.png")

    @JvmField
    val MC_FOLDER_8X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/mc_folder.png")

    @JvmField
    val MC_FOLDER_7X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/mc_folder_7x6.png")

    @JvmField
    val MESSAGES_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/messages.png")

    @JvmField
    val MODS_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/mods.png")

    @JvmField
    val PICTURES_10X10: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/pictures.png")

    @JvmField
    val PICTURES_SHORT_9X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/pictures_short.png")

    @JvmField
    val RADIO_TICK_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/radio_tick.png")

    @JvmField
    val EXPAND_6X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/expand.png")

    @JvmField
    val SETTINGS_9X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/settings.png")

    @JvmField
    val SETTINGS_VERTICAL_10X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/settings_vertical.png")

    @JvmField
    val SETTINGS_9X8: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/settings_9x8.png")

    @JvmField
    val INFO_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/info_9x9.png")

    @JvmField
    val INFO_DARK_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/info_dark_9x9.png")

    @JvmField
    val NOTICE_11X: ImageFactory = ResourceImageFactory("/assets/essential/textures/notice_11x11.png")

    @JvmField
    val ARROW_UP_DOWN_5X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/arrow_up_down.png")

    @JvmField
    val ARROW_UP_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/arrow_up.png")

    @JvmField
    val ARROW_DOWN_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/arrow_down.png")

    @JvmField
    val ARROW_LEFT_4X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/arrow-left.png")

    @JvmField
    val ARROW_LEFT_5X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/arrow-left_5x7.png")

    @JvmField
    val ARROW_RIGHT_3X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/arrow-right_3x5.png")

    @JvmField
    val ARROW_RIGHT_4X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/arrow-right.png")

    @JvmField
    val ARROW_UP_RIGHT_5X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/arrow-up-right.png")

    @JvmField
    val SOCIAL_10X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/social.png")

    @JvmField
    val LINK_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/link.png")

    @JvmField
    val HEART_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/heart.png")

    @JvmField
    val HEART_7X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/heart_7x6.png")

    @JvmField
    val TRASH_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/trash.png")

    @JvmField
    val TRASH_CAN_7X11: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/trash_can_10x.png")

    @JvmField
    val TRASH_CAN_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/trash_can_16x.png")

    @JvmField
    val FOLDER_10X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/folder_10x.png")

    @JvmField
    val FOLDER_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/folder_16x.png")

    @JvmField
    val EDIT_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/edit.png")

    @JvmField
    val HEART_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/favorite.png")

    @JvmField
    val FILE_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/file.png")

    @JvmField
    val COPY_EXISTING_LINK_16X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/link.png")

    @JvmField
    val FULLSCREEN_11X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/fullscreen.png")

    @JvmField
    val FULLSCREEN_10X_ON: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/fullscreen_on.png")

    @JvmField
    val FULLSCREEN_10X_OFF: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/fullscreen_off.png")

    @JvmField
    val BELL_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/bell.png")

    @JvmField
    val NOTIFICATIONS_10X_ON: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/notifications_on.png")

    @JvmField
    val NOTIFICATIONS_10X_OFF: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/notifications_off.png")

    @JvmField
    val COPY_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/copy.png")

    @JvmField
    val EDIT_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/edit.png")

    @JvmField
    val FOLDER_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/folder.png")

    @JvmField
    val HEART_FILLED_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/heart_filled.png").withColor(TEXT_RED)

    @JvmField
    val HEART_EMPTY_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/heart_outline.png")

    @JvmField
    val IMAGE_SIZE_BIG_10X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/image_size_big.png")

    @JvmField
    val IMAGE_SIZE_SMALL_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/image_size_small.png")

    @JvmField
    val LINK_8X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/link.png")

    @JvmField
    val REDO_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/redo.png")

    @JvmField
    val SAVE_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/save.png")

    @JvmField
    val UNDO_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/undo.png")

    @JvmField
    val UPLOAD_9X: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/upload.png")

    @JvmField
    val DOWNLOAD_7x8: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/new/download.png")

    @JvmField
    val OPTIONS_8X2: ImageFactory = ResourceImageFactory("/assets/essential/textures/screenshots/options.png")

    @JvmField
    val ADD_TO_CART_17X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/add_to_cart.png")

    @JvmField
    val REMOVE_FROM_CART_17X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/remove_from_cart.png")

    @JvmField
    val ROTATE_RIGHT_7X9: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/rotate_right.png")

    @JvmField
    val ROTATE_LEFT_7X9: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/rotate_left.png")

    @JvmField
    val UPLOAD_SKIN_9X13: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/skin.png")

    @JvmField
    val CANCEL_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/cancel_5x5.png")

    @JvmField
    val ANIMATIONS_ON: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/animation_on.png")

    @JvmField
    val ANIMATIONS_OFF: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/animation_off.png")

    @JvmField
    val ELLIPSES_5X1: ImageFactory = ResourceImageFactory("/assets/essential/textures/ellipses_5x1.png")

    @JvmField
    val ARROW_DOWN_7X4: ImageFactory = ResourceImageFactory("/assets/essential/textures/dropdown/arrow_down.png")

    @JvmField
    val ARROW_UP_7X4: ImageFactory = ResourceImageFactory("/assets/essential/textures/dropdown/arrow_up.png")

    @JvmField
    val PROPERTIES_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/properties.png")

    @JvmField
    val POWER_BUTTON_7X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/power-button_7x7.png")

    @JvmField
    val JOIN_ARROW_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/friends/join_arrow.png")

    @JvmField
    val NONE: ImageFactory = ImageFactory {
        UIImage(CompletableFuture.completedFuture(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)))
    }.withSettings(ImageGeneratorSettings(autoSize = false))

    @JvmField
    val RETRY_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/retry_7x7.png")

    @JvmField
    val INVITE_10X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/invite.png")

    @JvmField
    val WORLD_8X: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/world_8x8.png")

    @JvmField
    val PACK_128X: ImageFactory = ResourceImageFactory("/assets/essential/textures/pack_128x128.png") // Greyscale

    @JvmField
    val PACK_COLOR_128X: ImageFactory = ResourceImageFactory("/assets/essential/textures/pack_color_128x128.png")

    @JvmField
    val ROUND_WARNING_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/round_warning.png")

    @JvmField
    val EMOTE_WHEEL_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/wardrobe/emote_wheel.png")

    @JvmField
    val CHARACTER_4X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/wardrobe/character_4x6.png")

    @JvmField
    val FILTER_6X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/filter.png")

    @JvmField
    val WARDROBE_GIFT_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/wardrobe/gift.png")

    @JvmField
    val LOCK_7X9: ImageFactory = ResourceImageFactory("/assets/essential/textures/studio/lock.png")

    @JvmField
    val LOCK_HOLLOW_7X9: ImageFactory = ResourceImageFactory("/assets/essential/textures/lock_hollow_7x9.png")

    @JvmField
    val CROWN_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/sps/crown.png")

    @JvmField
    val OP_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/sps/op.png")

    @JvmField
    val PINNED_8X: ImageFactory = ResourceImageFactory("/assets/essential/textures/sps/pinned.png")

    @JvmField
    val UNPINNED_8X: ImageFactory = ResourceImageFactory("/assets/essential/textures/sps/unpinned.png")

    @JvmField
    val REINVITE_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/sps/reinvite_5x5.png")

    @JvmField
    val ENVELOPE_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/envelope_10x7.png")

    @JvmField
    val ENVELOPE_9X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/envelope_9x7.png")

    @JvmField
    val STAR_4X3: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/star_4x3.png")

    @JvmField
    val STAR_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/wardrobe/star.png")

    @JvmField
    val REPLY_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/friends/reply.png")

    @JvmField
    val TOGGLE_ON: ImageFactory = ResourceImageFactory("/assets/essential/textures/toggle/on.png")

    @JvmField
    val TOGGLE_OFF: ImageFactory = ResourceImageFactory("/assets/essential/textures/toggle/off.png")

    @JvmField
    val BLOCK_7X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/block7x7.png")

    @JvmField
    val BLOCK_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/block.png")

    @JvmField
    val CUT_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/cut.png")

    @JvmField
    val PASTE_10X8: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/paste.png")

    @JvmField
    val EDIT_SHORT_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/edit_short.png")

    @JvmField
    val LEAVE_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/leave.png")

    @JvmField
    val MARK_UNREAD_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/mark_unread.png")

    @JvmField
    val PENCIL_7x7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/pencil.png")

    @JvmField
    val MUTE_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/mute.png")

    @JvmField
    val MUTE_8X9: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/mute_new.png")

    @JvmField
    val RENAME_10X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/rename.png")

    @JvmField
    val RENAME_8X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/rename_8x5.png")

    @JvmField
    val REPORT_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/report.png")

    @JvmField
    val UNMUTE_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/unmute.png")

    @JvmField
    val UNMUTE_8X9: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/unmute_new.png")

    @JvmField
    val MESSAGE_10X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/message.png")

    @JvmField
    val REMOVE_FRIEND_10X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/remove_friend.png")

    @JvmField
    val REMOVE_FRIEND_PLAYER_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/remove_friend_player.png")

    @JvmField
    val JOIN_ARROW_10X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/join_arrow.png")

    @JvmField
    val SOCIAL_10X6: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/social.png")

    @JvmField
    val LINK_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/link.png")

    @JvmField
    val COPY_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/copy.png")

    @JvmField
    val FOLDER_10X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/folder.png")

    @JvmField
    val PROPERTIES_10X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/properties.png")

    @JvmField
    val ANNOUNCEMENT_ICON_8X: ImageFactory = ResourceImageFactory("/assets/essential/textures/announcement_icon.png")

    @JvmField
    val REPLY_10X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/optionmenu/reply.png")

    @JvmField
    val REPLY_LEFT_7X5: ImageFactory = ResourceImageFactory("/assets/essential/textures/friends/reply_left.png")

    @JvmField
    val MAKE_GROUP_9X8: ImageFactory = ResourceImageFactory("/assets/essential/textures/friends/make_group.png")

    @JvmField
    val DOWNLOAD_7X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/download_7x7.png")

    @JvmField
    val DOWNLOAD_7X8: ImageFactory = ResourceImageFactory("/assets/essential/textures/download_7x8.png")

    @JvmField
    val DOWNLOAD_7X8_ID: UIdentifier = UIdentifier("essential", "textures/download_7x8.png")

    @JvmField
    val COIN_7X: ImageFactory = ResourceImageFactory("/assets/essential/textures/coin/coin_icon.png")

    @JvmField
    val COIN_BUNDLE_0_999: ImageFactory = ResourceImageFactory("/assets/essential/textures/coin/coin_bundle_0_999.png")

    @JvmField
    val HOST_5X: ImageFactory = ResourceImageFactory("/assets/essential/textures/host_5x.png")

    @JvmField
    val CROWN_ICON: ImageFactory = ResourceImageFactory("/assets/essential/textures/menu/crown.png")

    @JvmField
    val DRAG_ICON: ImageFactory = ResourceImageFactory("/assets/essential/textures/drag_icon.png")

    @JvmField
    val SMILEY_8X: ImageFactory = ResourceImageFactory("/assets/essential/textures/account/smiley_8x8.png")

    @JvmField
    val SIGN_OUT_8X7: ImageFactory = ResourceImageFactory("/assets/essential/textures/account/sign_out_8x7.png")

    @JvmField
    val DEFAULT_WORLD_ICON_64X: ImageFactory = ResourceImageFactory("/assets/essential/textures/sps/default_world_icon.png")

    val LOADING_ANIMATION = AnimatedResourceImageFactory("/assets/essential/textures/loading/loading_", ".png", 12, 80)

    val CLOCK_ANIMATION = AnimatedResourceImageFactory("/assets/essential/textures/studio/clock_", ".png", 4, 1000)

    /* Fonts */
    /*
       Minecraft-Ten was generated using https://github.com/Chlumsky/msdf-atlas-gen/
       More specifically, the following command:
       `.\msdf-atlas-gen.exe -font '.\Minecrafted Ten.ttf' -type hardmask -format png -imageout Minecraft-Ten.png -json Minecraft-Ten.json -fontname Minecraft-Ten -size 10 -pxalign on -pxrange 1 -pxpadding 0 -outerpxpadding 0 -fontscale 0.998`
       The extra parameters at the end ensure that the font id grid-aligned and doesn't have unnecessary padding in the right.
       Then, 3 extra properties (baseCharHeight, belowLineHeight, shadowHeight) were added to the .json, to fully configure it.
     */
    private val MINECRAFT_TEN_FONT by lazy { loadFont("Minecraft-Ten") }
    private val MINECRAFT_FIVE_FONT by lazy { loadFont("Minecraft-Five") }

    val MINECRAFT_TEN by lazy { BasicFontRenderer(MINECRAFT_TEN_FONT) }
    val MINECRAFT_FIVE by lazy { BasicFontRenderer(MINECRAFT_FIVE_FONT) }

    @LoadsResources("/assets/essential/fonts/%name%.(json|png)")
    private fun loadFont(name: String) = Font.fromResource(javaClass, "/assets/essential/fonts/$name")

    @LoadsResources("/assets/essential/textures/friends/group_[a-z]+.png")
    private fun createGroupIconFactory(name: String): ImageFactory =
        ResourceImageFactory("/assets/essential/textures/friends/group_$name.png")

    val GROUP_ICONS_8X: List<ImageFactory> = listOf("blue", "purple", "red", "yellow").map(::createGroupIconFactory)

    fun groupIconForChannel(channelId: Long): ImageFactory = GROUP_ICONS_8X.random(Random(channelId))
}
