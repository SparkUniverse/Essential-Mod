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
package gg.essential.config

import com.sparkuniverse.toolbox.relationships.enums.FriendRequestPrivacySetting
import gg.essential.gui.common.modal.DangerConfirmationEssentialModal
import gg.essential.gui.common.modal.configure
import gg.essential.gui.elementa.state.v2.*
import gg.essential.gui.elementa.state.v2.combinators.*
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.screenshot.toast.ScreenshotPreviewAction
import gg.essential.gui.screenshot.toast.ScreenshotPreviewActionSlot
import gg.essential.mod.vigilance2.Vigilant2
import gg.essential.util.GuiEssentialPlatform
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.vigilance.data.*
import kotlin.reflect.KProperty

object EssentialConfig : Vigilant2(), GuiEssentialPlatform.Config {

    var showEssentialIndicatorOnTabState = property("General.Online Status.Show Essential Indicator on Tab", true)
    var showEssentialIndicatorOnTab by showEssentialIndicatorOnTabState

    val showEssentialIndicatorOnNametagState = property("General.Online Status.Show Essential Indicator on Nametags", true)
    var showEssentialIndicatorOnNametag by showEssentialIndicatorOnNametagState

    val sendServerUpdatesState = property("Privacy.General.Display Current Server", false)
    var sendServerUpdates by sendServerUpdatesState

    val friendRequestPrivacyState = property("Privacy.General.Friend Privacy Settings", FriendRequestPrivacySetting.ANY_ONE)
    var friendRequestPrivacy by friendRequestPrivacyState

    val streamerModeState = property("General.General.Streamer Mode", false)
    var streamerMode by streamerModeState

    val modCoreWarningState = property("General.General.ModCore Warning", true)
    var modCoreWarning by modCoreWarningState

    val linkWarningState = property("Notifications.Warnings.Link opening warning", true)
    var linkWarning by linkWarningState

    val friendConnectionStatusState = property("Notifications.General.Online Friend Alert", true)
    var friendConnectionStatus by friendConnectionStatusState

    val disableAllNotificationsState = property("Notifications.General.Disable All Notifications", false)
    var disableAllNotifications by disableAllNotificationsState

    val messageReceivedNotificationsState = property("Notifications.General.Message Notifications", true)
    var messageReceivedNotifications by messageReceivedNotificationsState

    val groupMessageReceivedNotificationsState = property("Notifications.General.Group Message Notifications", true)
    var groupMessageReceivedNotifications by groupMessageReceivedNotificationsState

    val messageSoundState = property("Notifications.General.Message Sound", true)
    var messageSound by messageSoundState

    val updateModalState = property("Notifications.General.Update Notifications", true)
    var updateModal by updateModalState

    val spsIPWarningState = property("Notifications.General.Host World IP Warning", true)
    var spsIPWarning by spsIPWarningState

    enum class ScreenshotToastDuration(val seconds: Int) {
        SHORT(3),
        MEDIUM(5),
        LONG(7),
    }
    val screenshotToastDurationState = property("Notifications.General.Screenshot Toast Duration", ScreenshotToastDuration.MEDIUM)
    var screenshotToastDuration by screenshotToastDurationState

    val zoomSmoothCameraState = property("Quality of Life.Zoom.Smooth Camera when Zoomed", true)
    var zoomSmoothCamera by zoomSmoothCameraState

    val smoothZoomAnimationState = property("Quality of Life.Zoom.Smooth Zoom Animation", false)
    var smoothZoomAnimation by smoothZoomAnimationState

    //Removed
    var smoothZoomAlgorithm = 0


    val toggleToZoomState = property("Quality of Life.Zoom.Toggle to Zoom", false)
    var toggleToZoom by toggleToZoomState

    /**
     * current multiplayer gui tab the player is on:
     * 0 = favourites;
     * 1 = friends;
     * 2 = discover
     */
    var currentMultiplayerTab: Int = 0

    @Suppress("SuspiciousVarProperty") // part of ABI
    @Deprecated("", replaceWith = ReplaceWith("essentialEnabled"))
    var essentialFull = true
        get() = essentialEnabled

    val essentialEnabledState = property("General.Experience.Enable Essential", true)
    var essentialEnabled by essentialEnabledState

    val autoUpdateState = mutableStateOf(false)
    var autoUpdate by autoUpdateState

    enum class EssentialMenuLayout(val label: String) {
        FULL("Full"), MINIMAL("Minimal"), OFF("Off")
    }
    val essentialMenuLayoutState = property("General.Experience.Essential Menu Layout", EssentialMenuLayout.FULL)
    var essentialMenuLayout by essentialMenuLayoutState

    val closerMenuSidebarState = property("General.Experience.Closer placement of Minimal Essential Menu", true)
    var closerMenuSidebar by closerMenuSidebarState

    val autoRefreshSessionState = property("Quality of Life.General.Auto Refresh Session", true)
    var autoRefreshSession by autoRefreshSessionState

    val windowedFullscreenState = property("Quality of Life.Fullscreen.Windowed Fullscreen", false)
    var windowedFullscreen by windowedFullscreenState

    val disableCosmeticsState = property("Cosmetics.General.Disable cosmetics", false)
    var disableCosmetics: Boolean by disableCosmeticsState

    /** The cosmetics hidden state, as well as a flag on whether this was set by the user or the server/mod. */
    val ownCosmeticsVisibleStateWithSource = mutableStateOf(Pair(true, CosmeticsVisibilitySource.System))
    val ownCosmeticsVisibleState = ownCosmeticsVisibleStateWithSource.map { it.first }
    val ownCosmeticsHidden: Boolean
        get() = !ownCosmeticsVisibleState.getUntracked()

    val hideCosmeticsWhenServerOverridesSkinState = property("Cosmetics.General.Hide cosmetics on server skins", false)
    var hideCosmeticsWhenServerOverridesSkin: Boolean by hideCosmeticsWhenServerOverridesSkinState

    val discordRichPresenceState = property("Quality of Life.Discord Integration.Set activity status on Discord", true)
    var discordRichPresence: Boolean by discordRichPresenceState

    val discordAllowAskToJoinState = property("Quality of Life.Discord Integration.Allow Ask To Join", true)
    var discordAllowAskToJoin: Boolean by discordAllowAskToJoinState

    val discordShowUsernameAndAvatarState = property("Quality of Life.Discord Integration.Show username and avatar", true)
    var discordShowUsernameAndAvatar: Boolean by discordShowUsernameAndAvatarState

    val discordShowCurrentServerState = property("Quality of Life.Discord Integration.Show current server", false)
    var discordShowCurrentServer: Boolean by discordShowCurrentServerState

    private fun revokeTosButton() {
        val manager = platform.createModalManager()
        manager.queueModal(
            RevokeTOSModal(manager).onPrimaryAction {
                doRevokeTos()
            }
        )
    }
    lateinit var doRevokeTos: () -> Unit

    val disableCosmeticsInInventoryState = property("Cosmetics.General.Disable cosmetics in inventory", false)
    var disableCosmeticsInInventory: Boolean by disableCosmeticsInInventoryState

    enum class CosmeticOrArmor(val label: String) {
        ONLY_ARMOR("Only armor"),
        ONLY_COSMETICS("Only cosmetics"),
        BOTH("Cosmetics and armor"),
    }
    val cosmeticArmorSettingSelfState = property("Cosmetics.General.Cosmetic & Armor Conflict - On Myself", CosmeticOrArmor.ONLY_ARMOR)
    var cosmeticArmorSettingSelf by cosmeticArmorSettingSelfState

    val cosmeticArmorSettingOtherState = property("Cosmetics.General.Cosmetic & Armor Conflict - On Others", CosmeticOrArmor.ONLY_ARMOR)
    var cosmeticArmorSettingOther by cosmeticArmorSettingOtherState

    // Choose whether to use rear or front facing third person perspective when emoting.
    // options = ["Rear", "Front"]
    val emoteThirdPersonTypeState = property("Hidden.Hidden.Emote perspective", 1)
    var emoteThirdPersonType: Int by emoteThirdPersonTypeState

    val thirdPersonEmotesState = property("Emotes.General.Play emotes in third person view", true)
    var thirdPersonEmotes by thirdPersonEmotesState

    val emotePreviewState = property("Emotes.General.Emote Preview", false)
    var emotePreview by emotePreviewState

    val disableEmotesState = property("Emotes.General.Disable Emotes", false)
    var disableEmotes by disableEmotesState

    enum class AllowEmoteSounds(val label: String) {
        FROM_EVERYONE("From everyone"),
        FROM_MYSELF_AND_FRIENDS("From myself & friends"),
        FROM_MYSELF_ONLY("From myself only"),
        FROM_NOBODY("From nobody")
    }
    val allowEmoteSounds = property("Emotes.General.Allow emote sounds", AllowEmoteSounds.FROM_EVERYONE)

    val essentialScreenshotsState = property("Quality of Life.Screenshots.Essential Screenshots", true)
    var essentialScreenshots: Boolean
        get() = essentialEnabled && essentialScreenshotsState.get()
        set(value) = essentialScreenshotsState.set(value)

    val screenshotSoundsState = property("Quality of Life.Screenshots.Screenshot Sounds", true)
    var screenshotSounds by screenshotSoundsState


    val enableVanillaScreenshotMessageState = property("Quality of Life.Screenshots.Vanilla screenshot message", false)
    var enableVanillaScreenshotMessage by enableVanillaScreenshotMessageState

    //region Screenshots Toast Options

    /**
     * The value is the ordinal number of the ScreenshotPreviewAction enum
     * The order of these actions is used/replicated in the settings.
     * Remember to change in both places at once
     */

    val screenshotOverlayTopLeftActionState = property("Quality of Life.Screenshots.Screenshot Quick Action #1 - Top Left", ScreenshotPreviewActionSlot.TOP_LEFT.defaultAction)
    var screenshotOverlayTopLeftAction by screenshotOverlayTopLeftActionState

    val screenshotOverlayTopRightActionState = property("Quality of Life.Screenshots.Screenshot Quick Action #2 - Top Right", ScreenshotPreviewActionSlot.TOP_RIGTH.defaultAction)
    var screenshotOverlayTopRightAction by screenshotOverlayTopRightActionState

    val screenshotOverlayBottomLeftActionState = property("Quality of Life.Screenshots.Screenshot Quick Action #3 - Bottom Left", ScreenshotPreviewActionSlot.BOTTOM_LEFT.defaultAction)
    var screenshotOverlayBottomLeftAction by screenshotOverlayBottomLeftActionState

    val screenshotOverlayBottomRightActionState = property("Quality of Life.Screenshots.Screenshot Quick Action #4 - Bottom Right", ScreenshotPreviewActionSlot.BOTTOM_RIGHT.defaultAction)
    var screenshotOverlayBottomRightAction by screenshotOverlayBottomRightActionState

    //endregion

    enum class PostScreenshotAction(val label: String) {
        NOTHING("Nothing"),
        COPY_IMAGE("Copy Image"),
        COPY_URL("Copy URL"),
    }
    val postScreenshotActionState = property("Quality of Life.Screenshots.Post Screenshot Action", PostScreenshotAction.NOTHING)
    var postScreenshotAction by postScreenshotActionState

    override val useVanillaButtonForRetexturing = property("General.Experience.Use vanilla button texture", false)

    val shouldDarkenRetexturedButtonsState = property("General.Experience.Darken re-textured menu buttons", false)
    override var shouldDarkenRetexturedButtons by shouldDarkenRetexturedButtonsState

    enum class TimeFormat(val label: String) { AM_PM("12 Hour"), REGULAR("24 Hour") }
    val timeFormatState = property("General.General.Timestamps Format", TimeFormat.AM_PM)
    var timeFormat by timeFormatState

    val showQuickActionBarState = property("General.Experience.Quick Action Bar", true)
    var showQuickActionBar by showQuickActionBarState

    val replaceWindowTitleState = property("General.General.Replace Window Title", true)
    var replaceWindowTitle by replaceWindowTitleState

    val screenshotBrowserItemsPerRowState = property("Hidden.Hidden.screenshotBrowserItemsPerRow", 3)
    var screenshotBrowserItemsPerRow by screenshotBrowserItemsPerRowState

    val coinsSelectedCurrencyState = property("Hidden.Hidden.coinsSelectedCurrency", "USD")

    private val CREATOR_CODE_DEFAULT = "__default__" // toml does not support null, so we'll use a special value instead
    val coinsPurchaseCreatorCodeState = property("Hidden.Hidden.coinsPurchaseCreatorCode", CREATOR_CODE_DEFAULT)
        .bimap({ it.takeUnless { it == CREATOR_CODE_DEFAULT } }, { it ?: CREATOR_CODE_DEFAULT })


    val spsPinnedGameRulesState = property("Hidden.Hidden.spsPinnedGameRules", "")
    var spsPinnedGameRules by spsPinnedGameRulesState

    val enlargeSocialMenuChatMetadataState = property("Quality of Life.Accessibility.Enlarge chat metadata", false)
    var enlargeSocialMenuChatMetadata by enlargeSocialMenuChatMetadataState

    val showEmotePageKeybindBanner = property("Hidden.Hidden.showEmotePageKeybindBanner", true)

    enum class PreviouslyLaunchedWithContainer { Unknown, Yes, No  }
    val previouslyLaunchedWithContainer = property("Hidden.previously_launched_with_container", PreviouslyLaunchedWithContainer.Unknown)

    val playEmoteSoundsInWardrobe = property("Hidden.play_emote_sounds_in_wardrobe", true)

    val hideCosmeticParticlesInFirstPerson = property("Cosmetics.General.Hide cosmetic particles in first person", false)

    val showOwnNametag = property("Quality of Life.Nameplate.Show my nameplate in third-person", true)

    val appearOffline = mutableStateOf(/*state*/false to /*updateInfra*/false)

    val shareProfileLastOnline = mutableStateOf(/*state*/true to /*updateInfra*/false)

    val acknowledgedPermanentSuspension = property("Hidden.acknowledged_permanent_suspension", false)

    val collectOptionalTelemetryWithSource = mutableStateOf(/*state*/true to /*updateInfra*/false)

    val chatFilterWithSource = mutableStateOf(/*state*/true to /*updateInfra*/false)

    override val migrations = listOf(
        Migration { config ->
            val overrideGuiScale = config.remove("general.general.gui_scale") as Boolean? ?: return@Migration
            val newKey = "general.general.essential_gui_scale"
            // if the user had the old override option disabled, set the new option to Minecraft
            if (!overrideGuiScale && newKey !in config) {
                config[newKey] = 5
            }
        },
        Migration { config ->
            val cosmeticArmorSetting  = config.remove("cosmetics.general.cosmetic_armor_handling") as Int? ?: return@Migration
            val newKeySelf = "cosmetics.general.cosmetic_&_armor_conflict_-_on_myself"
            val newKeyOther = "cosmetics.general.cosmetic_&_armor_conflict_-_on_others"
            // Import values based on previous setting
            val (self, other) = when (cosmeticArmorSetting) {
                1 -> Pair(2, 0)
                2 -> Pair(2, 2)
                else -> return@Migration
            }
            if (newKeySelf !in config) {
                config[newKeySelf] = self
                config[newKeyOther] = other
            }
        },
        Migration { config ->
            val keySelf = "cosmetics.general.cosmetic_&_armor_conflict_-_on_myself"
            val keyOther = "cosmetics.general.cosmetic_&_armor_conflict_-_on_others"
            // Remove `Always hide armor` option
            if (config[keySelf] == 3) {
                config[keySelf] = 1
            }
            if (config[keyOther] == 3) {
                config[keyOther] = 1
            }
        },
        Migration { config ->
            config.remove("hidden.hidden.wardrobesortoption")
            config.remove("hidden.hidden.wardrobesortoptionmigration")
        },
        Migration { config ->
            val menuVisible = config.remove("general.experience.show_menus") as Boolean? ?: return@Migration
            val newKey = "general.experience.essential_menu_layout"
            // Keep the menu hidden, if it was hidden by the old setting, otherwise default
            if (!menuVisible && newKey !in config) {
                config[newKey] = 2
            }
        },
        Migration { config ->
            config.remove("quality_of_life.discord_integration.discord_rich_presence") // renamed in 7c34456
            config.remove("general.experience.use_button_retexturing") // renamed in 87fb161
            config.remove("general.experience.use_button_re-texturing") // renamed in b3bba91
            config.remove("cosmetics.general.show_other_players'_armor") // renamed in 78c57e4
            config.remove("cosmetics.general.show_my_armor") // renamed in 78c57e4
            config.remove("cosmetics.general.show_my_cosmetics") // renamed in d017e43e
            config.remove("general.experience.essential_full") // removed in 5768b86
            config.remove("general.friends.invite_friends") // removed in da08ec1
            config.remove("cosmetics.general.hide_cosmetics_on_server_skin_override") // renamed in 8ac0eda
            config.remove("general.general.network_messages") // removed in c7faddb
            config.remove("general.experience.alternate_character_customizer") // removed in 9968ca21
            config.remove("general.general.multiplayer_tab") // removed in 547f15e
            // stopped at (exclusive) ede32c9
        },
        Migration { it.remove("general.experience.automatic_updates") },
        Migration { it.remove("cosmetics.general.hide_your_cosmetics") },
    ) + listOf(
        Migration { config ->
            val key = "general.general.essential_gui_scale"

            // The "Minecraft" option has been removed, this should be replaced with "Auto".
            if (config[key] == 5) {
                config[key] = 0
            }
        },
        Migration { config ->
            // This has been replaced by `notifications.warnings.link_opening_warning`, which has different functionality,
            // so there is no other reasonable migration here.
            config.remove("general.general.prompt_when_visiting_trusted_hosts")
        },
        Migration { config ->
            val emotesInThirdPerson = config["emotes.general.play_emotes_in_third_person_view"] as? Boolean ?: true
            val oldValue = config.remove("emotes.general.emote_preview_in_first_person") as? Boolean ?: return@Migration

            config["emotes.general.emotes_preview"] = if (emotesInThirdPerson) {
                false
            } else {
                oldValue
            }
        },
        Migration { it.remove("general.general.essential_gui_scale") },
    ) + listOf()

    val gui by lazyBuildGui("Essential Settings") {
        category("General") {
            subcategory("Essential") {
                switch(essentialEnabledState) {
                    name = "Essential"
                    description = "Essential adds features that make playing Minecraft easier."
                }

                switch(autoUpdateState) {
                    name = "Automatic Essential updates"
                    description = "Downloads and installs new Essential updates on launch, when available."
                }
            }

            subcategory("Session") {
                switch(autoRefreshSessionState) {
                    name = "Automatically refresh session"
                    description = "Automatically refreshes your Minecraft session when it’s expired, upon connecting to a server."
                }
            }

            subcategory("Date & Time") {
                selector(timeFormatState) {
                    name = "Timestamps format"
                    description = "Choose between using 12 or 24 hour time for dates/timestamps in Essential menus."
                    options = TimeFormat.entries.map { it.label }
                }
            }
        }

        category("Friends") {
            subcategory("Activity Sharing") {
                switch(appearOffline.bimapState({ it.first }, { it to true})) {
                    name = "Appear offline"
                    description = "Hides your online and activity status."
                }
                dynamic {
                    if (!appearOffline().first) {
                        switch(sendServerUpdatesState) {
                            name = "Share game activity with friends"
                            description = "Displays the server or world you are currently playing on to your friends."
                        }
                    }
                }
                switch(discordRichPresenceState) {
                    name = "Share activity status on Discord"
                    description = "Display Essential as your current activity on Discord."
                }
                switch(shareProfileLastOnline.bimap({ it.first }, { it to true })) {
                    name = "Share last online time with friends"
                    description = "Displays the last time you were online to your friends."
                }
            }

            subcategory("Friend Requests") {
                selector(friendRequestPrivacyState) {
                    name = "Friend request permission"
                    description = "Determines who can send you a friend request on Essential."
                    options = FriendRequestPrivacySetting.entries.map { option ->
                        when (option) {
                            FriendRequestPrivacySetting.ANY_ONE -> "Everyone"
                            FriendRequestPrivacySetting.FRIEND_OF_FRIENDS -> "Friends of friends"
                            FriendRequestPrivacySetting.NO_ONE -> "Nobody"
                        }
                    }
                }
            }

            subcategory("Chat Safety") {
                switch(chatFilterWithSource.bimap({ it.first }, { it to true })) {
                    name = "Chat filter"
                    description = "Prevents inappropriate or offensive language from appearing in chat"
                }
            }
        }

        category("Appearance") {
            subcategory("Main & Pause Menu") {
                selector(essentialMenuLayoutState) {
                    name = "Essential menu layout"
                    description = "Choose the layout of the Essential buttons on the main and pause menu."
                    options = EssentialMenuLayout.entries.map { it.label }
                }
                switch(useVanillaButtonForRetexturing) {
                    name = "Use Minecraft button texture"
                    description = "Uses Minecraft’s button texture on Essential main and pause menu buttons. If you have a resource pack equipped, it will use the resource pack’s button texture."
                }
                switch(shouldDarkenRetexturedButtonsState) {
                    name = "Darken Minecraft button texture"
                    description = "Tints Essential main and pause menu buttons darker to increase read-ability."
                    visible = useVanillaButtonForRetexturing
                }
            }

            subcategory("Nameplates") {
                switch(showOwnNametag) {
                    name = "Nameplate in third-person"
                    description = "Shows your own nameplate when in third-person perspective."
                }
                switch(showEssentialIndicatorOnNametagState) {
                    name = "Essential icon on nameplates"
                    description = "Shows the Essential icon on Essential players’ nameplates."
                }
            }

            subcategory("Tab-list") {
                switch(showEssentialIndicatorOnTabState) {
                    name = "Essential icon in tab-list"
                    description = "Shows the Essential icon on Essential players in the tab-list."
                }
            }

            if (platform.mcVersion < 11400) {
                subcategory("Fullscreen") {
                    switch(windowedFullscreenState) {
                        name = "Windowed fullscreen"
                        description = "Enables windowed fullscreen, allowing focusing on other windows."
                    }
                }
            }

            subcategory("Accessibility") {
                switch(enlargeSocialMenuChatMetadataState) {
                    name = "Enlarge social menu chat metadata"
                    description = "Uses a larger font for usernames and timestamps in the social menu’s chat."
                }
            }
        }

        category("Notifications") {
            subcategory("Notifications") {
                switch(!disableAllNotificationsState) {
                    name = "Notifications"
                    description = "Notifications appear in the bottom right corner."
                }
                dynamic {
                    if (!disableAllNotificationsState()) {
                        switch(friendConnectionStatusState) {
                            name = "Friend online alert"
                            description = "Receive a notification when a friend comes online."
                        }
                        switch(messageReceivedNotificationsState) {
                            name = "Direct message notifications"
                            description = "Receive a notification when you get a direct message in Essential chat."
                        }
                        switch(groupMessageReceivedNotificationsState) {
                            name = "Group message notifications"
                            description = "Receive a notification when you get a group message in Essential chat."
                        }
                        switch(messageSoundState) {
                            name = "Message received sound"
                            description = "Plays a sound when receiving a message."
                        }
                        switch(updateModalState) {
                            name = "Essential update notifications"
                            description = "Displays a notification modal after Essential has been updated."
                        }
                    }
                }
            }

            subcategory("Warnings") {
                switch(linkWarningState) {
                    name = "Link opening warning"
                    description = "Show a confirmation modal before opening any third-party link."
                }

                switch(spsIPWarningState) {
                    name = "World hosting IP visibility warning"
                    description = "Show an IP warning modal when hosting a world."
                }
            }
        }

        category("Quality of Life") {
            subcategory("Screenshots") {
                switch(essentialScreenshotsState) {
                    name = "Screenshot preview"
                    description = "Shows a screenshot preview with quick-actions on capture."
                }

                val quickActionOptions = ScreenshotPreviewAction.DISPLAY_ORDER
                selector(screenshotOverlayTopLeftActionState, quickActionOptions) {
                    name = "Screenshot quick-action #1"
                    description = "Select an action for the top left screenshot quick-action slot."
                    options = quickActionOptions.map { it.displayName }
                }
                selector(screenshotOverlayTopRightActionState, quickActionOptions) {
                    name = "Screenshot quick-action #2"
                    description = "Select an action for the top right screenshot quick-action slot."
                    options = quickActionOptions.map { it.displayName }
                }
                selector(screenshotOverlayBottomLeftActionState, quickActionOptions) {
                    name = "Screenshot quick-action #3"
                    description = "Select an action for the bottom left screenshot quick-action slot."
                    options = quickActionOptions.map { it.displayName }
                }
                selector(screenshotOverlayBottomRightActionState, quickActionOptions) {
                    name = "Screenshot quick-action #4"
                    description = "Select an action for the bottom right screenshot quick-action slot."
                    options = quickActionOptions.map { it.displayName }
                }
                selector(postScreenshotActionState) {
                    name = "Post screenshot action"
                    description = "Automatically trigger an action after taking a screenshot."
                    options = PostScreenshotAction.entries.map { it.label }
                }
                selector(screenshotToastDurationState) {
                    name = "Screenshot preview duration"
                    description = "Control for how long the screenshot preview will be shown."
                    options = ScreenshotToastDuration.entries.map { "${it.seconds} seconds" }
                }
                switch(screenshotSoundsState) {
                    name = "Screenshot sounds"
                    description = "Plays a capture sound when taking a screenshot."
                }
                switch(enableVanillaScreenshotMessageState) {
                    name = "Screenshot message"
                    description = "Shows the vanilla screenshot capture message in in-game chat."
                }
            }
            if (!platform.isOptiFineInstalled) {
                subcategory("Zoom") {
                    switch(zoomSmoothCameraState) {
                        name = "Smooth zoomed camera"
                        description = "Enables smooth camera while zooming."
                    }
                    switch(smoothZoomAnimationState) {
                        name = "Smooth zooming"
                        description = "Uses a smooth animation when zooming in and out."
                    }
                    switch(toggleToZoomState) {
                        name = "Toggle to zoom"
                        description = "Press the zoom key to toggle zoom, rather than hold."
                    }
                }
            }
        }

        category("Emotes") {
            subcategory("General") {
                switch(!disableEmotesState) {
                    name = "Emotes"
                    description = "Better express yourself with emotes."
                }
                dynamic {
                    if (!disableEmotesState()) {
                        selector(allowEmoteSounds) {
                            name = "Allow emote sounds"
                            description = "Select who you can hear emote sounds from."
                            options = AllowEmoteSounds.entries.map { it.label }
                        }

                        switch(thirdPersonEmotesState) {
                            name = "Play emotes in third person view"
                            description = "Emotes will be shown in third-person view. You can still toggle between front and back view."
                        }

                        switch(emotePreviewState) {
                            name = "Emote preview"
                            description = "When playing emotes, show a model of your character performing the emote in the upper left corner of the screen."
                        }
                    }
                }
            }
        }

        category("Cosmetics") {
            subcategory("General") {
                switch(!disableCosmeticsState) {
                    name = "Cosmetics"
                    description = "Enhance your Minecraft character with cosmetics."
                }
                dynamic {
                    if (!disableCosmeticsState()) {
                        switch(ownCosmeticsVisibleStateWithSource.bimap({ !it.first }, { !it to CosmeticsVisibilitySource.UserWithoutNotification })) {
                            name = "Hide your cosmetics"
                            description = "Hide your equipped cosmetics for everyone."
                        }

                        switch(hideCosmeticParticlesInFirstPerson) {
                            name = "Hide cosmetic particles in first person"
                            description = "Hide particle effects coming from your equipped cosmetics in first person."
                        }

                        val cosmeticArmorSettingsOrdered = listOf(
                            CosmeticOrArmor.ONLY_COSMETICS,
                            CosmeticOrArmor.ONLY_ARMOR,
                            CosmeticOrArmor.BOTH,
                        )

                        selector(cosmeticArmorSettingSelfState, cosmeticArmorSettingsOrdered) {
                            name = "Cosmetics & armor visibility on me"
                            description = "Cosmetics and armor may conflict with each other on your player. This setting does not affect what other players see."
                            options = cosmeticArmorSettingsOrdered.map { it.label }
                        }

                        selector(cosmeticArmorSettingOtherState, cosmeticArmorSettingsOrdered) {
                            name = "Cosmetics & armor visibility on others"
                            description = "Cosmetics and armor may conflict with each other on other players. This setting does not affect what other players see."
                            options = cosmeticArmorSettingsOrdered.map { it.label }
                        }

                        switch(disableCosmeticsInInventoryState) {
                            name = "Hide cosmetics in inventory"
                            description = "Hides your equipped cosmetics on the player preview inside your inventory."
                        }

                        switch(hideCosmeticsWhenServerOverridesSkinState) {
                            name = "Hide cosmetics on server skins"
                            description = "Hides cosmetics on players when the joined server modifies the user’s skins."
                        }
                    }
                }

            }
        }

        category("Privacy & Data") {
            subcategory("Privacy & Data") {
                switch(collectOptionalTelemetryWithSource.bimap({ it.first }, { it to true })) {
                    name = "Collect optional telemetry data"
                    description = "Give Essential permission to collect additional telemetry data to help improve your experience."
                }

                button(::revokeTosButton) {
                    name = "Terms of Service and Privacy Policy"
                    description = "Deny the Essential terms of service and privacy policy. Warning! This will prevent the use of Essential features."
                    label = "Deny TOS & PP"
                }
            }
        }
    }

    // These are intentionally not public/global because subscribing to updated a backing field might generally be
    // preferable, especially if State becomes lazy.
    private operator fun <T> MutableState<T>.getValue(essentialConfig: EssentialConfig, property: KProperty<*>): T {
        return get()
    }
    private operator fun <T> MutableState<T>.setValue(essentialConfig: EssentialConfig, property: KProperty<*>, value: T) {
        set(value)
    }

    class RevokeTOSModal(manager: ModalManager) : DangerConfirmationEssentialModal(manager, "Confirm", false) {
        init {
            configure {
                titleText = "Revoking Essential's Terms of Service and Privacy Policy will cause Essential features not to work. Are you sure you want to proceed?"
            }
        }
    }

    enum class CosmeticsVisibilitySource {
        System, // Set by Infra, or mod for internal reasons (e.g. undoing failed infra changed)
        UserWithNotification,
        UserWithoutNotification
    }

}
