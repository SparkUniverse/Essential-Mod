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
package gg.essential.mixins.transformers.feature.cosmetics;

import gg.essential.cosmetics.WearablesManager;
import gg.essential.mixins.impl.client.entity.AbstractClientPlayerExt;
import gg.essential.model.backend.PlayerPose;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static gg.essential.cosmetics.events.CosmeticEventDispatcher.dispatchEvents;

@Mixin(RenderGlobal.class)
public abstract class Mixin_UpdateCosmetics {
    //#if NEOFORGE && MC < 1.21.11
    //#if MC>=12106
    //$$ private static final String MAIN_PASS_LAMBDA = "lambda$addMainPass$3";
    //#else
    //$$ private static final String MAIN_PASS_LAMBDA = "lambda$addMainPass$2";
    //#endif
    //#elseif FORGE && MC < 1.21.11
    //#if MC>=12106
    //$$ private static final String MAIN_PASS_LAMBDA = "lambda$addMainPass$2";
    //#else
    //$$ private static final String MAIN_PASS_LAMBDA = "lambda$addMainPass$1";
    //#endif
    //#else
    private static final String MAIN_PASS_LAMBDA = "method_62214";
    //#endif

    // FIXME need to figure out how to deal with this system once MC rendering goes off-thread
    //#if MC >= 26.2
    //$$ @Unique
    //$$ private ClientLevel world() {
    //$$     return net.minecraft.client.Minecraft.getInstance().level;
    //$$ }
    //#else
    @Shadow
    private WorldClient world;
    @Unique
    private WorldClient world() {
        return this.world;
    }
    //#endif

    //#if MC >= 26.2
    //$$ @Inject(method = "submitEntities", at = @At("HEAD"))
    //#elseif MC >= 26.1
    //$$ @Inject(method = "extractLevel", at = @At(value = "CONSTANT", args = "stringValue=entities"))
    //#elseif MC>=12109
    //$$ @Inject(method = "render", at = @At(value = "CONSTANT", args = "stringValue=entities"))
    //#elseif MC>=12102
    //$$ @Inject(method = MAIN_PASS_LAMBDA, at = @At(value = "CONSTANT", args = "stringValue=entities"))
    //#elseif MC>=11400
    //$$ @Inject(method = "updateCameraAndRender", at = @At(value = "CONSTANT", args = "stringValue=entities"))
    //#else
    @Inject(method = "renderEntities", at = @At("HEAD"))
    //#endif
    private void essential$updateCosmeticsPreRender(CallbackInfo ci) {
        //#if MC>=11400
        //$$ for (PlayerEntity player : world().getPlayers()) {
        //#else
        for (EntityPlayer player : world().playerEntities) {
        //#endif
            if (!(player instanceof AbstractClientPlayerExt)) {
                continue;
            }
            AbstractClientPlayerExt playerExt = (AbstractClientPlayerExt) player;

            // Set null so Mixin_RenderExtraClientCosmeticGeometryInFirstPerson can detect if the player was rendered already
            // during this pass by some other mod. E.G. a first person body mod
            playerExt.setRenderedPose(null);

            WearablesManager wearablesManager = playerExt.getWearablesManager();
            wearablesManager.update();
            playerExt.getPoseManager().update(wearablesManager);
        }
    }

    //#if MC >= 26.2
    //$$ @Inject(method = "submitEntities", at = @At("RETURN"))
    //#elseif MC >= 26.1
    //$$ @Inject(method = MAIN_PASS_LAMBDA, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V", shift = At.Shift.AFTER))
    //#elseif MC>=12109
    //$$ @Inject(method = MAIN_PASS_LAMBDA, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V", shift = At.Shift.AFTER))
    //#elseif MC>=12102
    //$$ @Inject(method = MAIN_PASS_LAMBDA, at = @At(value = "CONSTANT", args = "stringValue=blockentities"))
    //#elseif MC>=11400
    //$$ @Inject(method = "updateCameraAndRender", at = @At(value = "CONSTANT", args = "stringValue=blockentities"))
    //#else
    @Inject(method = "renderEntities", at = @At("RETURN"))
    //#endif
    private void essential$updateCosmeticsPostRender(CallbackInfo ci) {
        //#if MC>=11400
        //$$ for (PlayerEntity player : world().getPlayers()) {
        //#else
        for (EntityPlayer player : world().playerEntities) {
        //#endif
            if (!(player instanceof AbstractClientPlayerExt)) {
                continue;
            }
            AbstractClientPlayerExt playerExt = (AbstractClientPlayerExt) player;

            WearablesManager wearablesManager = playerExt.getWearablesManager();
            PlayerPose renderedPose = playerExt.getRenderedPose();
            if (renderedPose == null) {
                // No way for us to get the real pose if we didn't actually render, let's just use the neutral pose.
                renderedPose = PlayerPose.Companion.neutral();
                // Also no way to know if cape/elytra/etc. are visible (not if you consider modded items anyway),
                // so we'll place those far away as if they weren't visible so any events they spawn won't be visible.
                renderedPose = renderedPose.withoutAttachments();
                // Also apply any emotes to it, so that any particles that shoot where the emote points actually shoot
                // in that direction instead of just straight down.
                renderedPose = playerExt.getPoseManager().computePose(wearablesManager, renderedPose);
            }
            wearablesManager.updateLocators(renderedPose);
            dispatchEvents((AbstractClientPlayer) player, wearablesManager);
        }
    }
}
