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
package gg.essential.mixins.transformers.cosmetics.capes;

import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.mod.cosmetics.CosmeticOutfit;
import gg.essential.mod.cosmetics.CosmeticSlot;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.cosmetics.OutfitManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EnumPlayerModelParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static gg.essential.mod.cosmetics.CapeDisabledKt.CAPE_DISABLED_COSMETIC_ID;

@Mixin(GameSettings.class)
public class Mixin_RedirectVanillaCapeSetting {
    //#if MC>=11700
    //#if MC>=12102
    //$$ @Inject(method = "setPlayerModelPart", at = @At("HEAD"), cancellable = true)
    //#else
    //$$ @Inject(method = "togglePlayerModelPart", at = @At("HEAD"), cancellable = true)
    //#endif
    //$$ private void redirectCapeChangesToOutfit(PlayerModelPart part, boolean shouldBeEnabled, CallbackInfo ci) {
    //#else
    @Inject(method = "switchModelPartEnabled", at = @At("HEAD"), cancellable = true)
    private void redirectCapeChangesToOutfit(EnumPlayerModelParts part, CallbackInfo ci) {
    //#endif
        if (part != EnumPlayerModelParts.CAPE) {
            return; // not applicable
        }

        ConnectionManager connectionManager = Essential.getInstance().getConnectionManager();
        if (!connectionManager.isOpen()) {
            return; // not authed, don't mess with it
        }

        if (EssentialConfig.INSTANCE.getDisableCosmetics()) {
            return;
        }

        OutfitManager manager = connectionManager.getOutfitManager();
        CosmeticOutfit outfit = manager.getSelectedOutfit();
        if (outfit == null) {
            return;
        }

        boolean isEnabled = !CAPE_DISABLED_COSMETIC_ID.equals(outfit.getEquippedCosmetics().get(CosmeticSlot.CAPE));
        //#if MC>=11700
        //$$ if (isEnabled == shouldBeEnabled) {
        //$$     return; // correct as is, just pass through the call (this is likely CosmeticsManager calling)
        //$$ }
        //#else
        boolean shouldBeEnabled = !isEnabled; // prior to 1.17, the method we inject into always flips the state
        //#endif

        // They clicked the Cape visibility button and are using Essential, we'll take care of it from here
        ci.cancel();

        if (shouldBeEnabled) {
            // They currently have the Cape Disabled cosmetic equipped, un-equip it
            manager.updateEquippedCosmetic(outfit.getId(), CosmeticSlot.CAPE, null);
        } else {
            // They currently have capes enabled but don't want them, equip the Cape Disabled cosmetics
            manager.updateEquippedCosmetic(outfit.getId(), CosmeticSlot.CAPE, CAPE_DISABLED_COSMETIC_ID);
        }
    }
}
