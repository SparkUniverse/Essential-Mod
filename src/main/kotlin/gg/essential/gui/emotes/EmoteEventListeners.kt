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
package gg.essential.gui.emotes

import gg.essential.Essential
import gg.essential.config.EssentialConfig
import gg.essential.cosmetics.CosmeticId
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.provideDelegate
import gg.essential.elementa.state.BasicState
import gg.essential.event.network.server.ServerLeaveEvent
import gg.essential.event.render.RenderTickEvent
import gg.essential.gui.common.UI3DPlayer
import gg.essential.gui.overlay.Layer
import gg.essential.gui.overlay.LayerPriority
import gg.essential.gui.overlay.OverlayManagerImpl
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.model.util.PlayerPoseManager
import gg.essential.universal.UScreen
import gg.essential.universal.wrappers.UPlayer
import gg.essential.util.*
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.gui.GuiChat
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.math.BlockPos
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

class EmoteEventListeners {

    private var lastPosition: BlockPos? = null
    private var lastAttacked: EntityLivingBase? = null
    private var hurtTime by Delegates.notNull<Int>()

    private val cosmeticsManager = Essential.getInstance().connectionManager.cosmeticsManager
    private var layer: Layer? = null
    private var emoteActiveSince: Pair<CosmeticId, Long>? = null
    private var mostRecentEmote: String? = null

    init {
        val player = UPlayer.getPlayer()
        if (player != null) {
            lastPosition = player.position
            lastAttacked = player.lastAttackedEntity
            hurtTime = player.hurtTime
        }
    }

    @Subscribe
    private fun onEmoteRenderTick(event: RenderTickEvent) {
        if (!event.isPre) return
        val player = UPlayer.getPlayer()
        val emote = Essential.getInstance().connectionManager.cosmeticsManager.equippedCosmetics[CosmeticSlot.EMOTE]?.id
        if (emote != null && mostRecentEmote != emote) {
            mostRecentEmote = emote
        }

        if (player == null || emote == null) {
            if (!EmoteWheel.emoteComing)  {
                stopEmote()
                Essential.EVENT_BUS.unregister(this)
            } else if (EssentialConfig.emotePreview){ // In the delay between activation and the emote actually starting
                showEmotePreview()
            }
            return
        }

        // Global cancellation triggers
        if (!EmoteWheel.canEmote(player)) {
            stopEmote()
        }

        val emoteActiveSince = emoteActiveSince?.takeUnless { (prevFrameEmote, _) -> prevFrameEmote != emote }
            ?: (emote to System.currentTimeMillis()).also { emoteActiveSince = it }

        // Cancellation triggers
        val triggers = cosmeticsManager.getCosmetic(emote)?.emoteInterruptionTriggers
        if (triggers != null) {
            if ((triggers.movement && lastPosition != player.position && System.currentTimeMillis() - emoteActiveSince.second > triggers.movementGraceTime)
                || (triggers.attack && lastAttacked != player.lastAttackedEntity)
                || (triggers.damaged && hurtTime > 0)
                || (triggers.armSwing && player.isSwingInProgress)
            ) {
                stopEmote()
            }
        }

        if (getPerspective() == 0) {
            if (EssentialConfig.thirdPersonEmotes) {
                setPerspective(1)
            } else if (EssentialConfig.emotePreview) {
                showEmotePreview()
            }
        }
    }

    @Subscribe
    private fun onQuit(event: ServerLeaveEvent) {
        stopEmote()
    }

    /** Show a preview of the player emoting in the top-left corner of the screen */
    private fun showEmotePreview() {
        if (layer != null || !EssentialConfig.emotePreview) return;

        layer = OverlayManagerImpl.addLayer(LayerPriority.BelowScreen).apply {
            val previewContainer by UIContainer().constrain {
                x = 17.pixels
                y = 20.pixels
                width = ChildBasedSizeConstraint()
                height = ChildBasedSizeConstraint()
            } childOf window

            val preview by UI3DPlayer(BasicState(true), BasicState(false), UPlayer.getPlayer()!!).constrain {
                height = 60.pixels
            } childOf previewContainer

            previewContainer.addUpdateFunc { _, _ ->
                if (!EssentialConfig.emotePreview) {
                    removeEmotePreview()
                } else if ((false) || UScreen.currentScreen != null && UScreen.currentScreen !is GuiChat) {
                    preview.hide()
                } else {
                    preview.unhide()
                }

                if (preview.player !== UPlayer.getPlayer()) {
                    preview.player = UPlayer.getPlayer()
                }
            }
        }
    }

    /** Clear the emote preview layer and remove it */
    private fun removeEmotePreview() {
        val emoteTransitionTime = mostRecentEmote?.let { cosmeticsManager.getCosmetic(it) }?.let { EmoteWheel.getEmoteTransitionTime(it) } ?: 0L
        val delay = (PlayerPoseManager.transitionTime * 1000).toLong() + emoteTransitionTime

        Multithreading.scheduleOnMainThread({
            layer?.let {
                OverlayManagerImpl.removeLayer(it)
                layer = null
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun stopEmote() {
        removeEmotePreview()
        EmoteWheel.unequipCurrentEmote()
    }
}
