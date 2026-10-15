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
package gg.essential.mixins.transformers.feature.sps;

import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import gg.essential.sps.IntegratedServerManager;
import gg.essential.sps.McIntegratedServerManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

//#if MC>=12004
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.util.UUID;
//#endif

@Mixin(MinecraftServer.class)
public class Mixin_IntegratedServerResourcePack {

    @Inject(method = "getResourcePackProperties", at = @At("HEAD"), cancellable = true)
    private void getResourcePackProperties(CallbackInfoReturnable<Optional<MinecraftServer.ServerResourcePackProperties>> info) {
        if (this instanceof IntegratedServerExt) {
            McIntegratedServerManager manager = ((IntegratedServerExt) this).getEssential$manager();
            Optional<IntegratedServerManager.ServerResourcePack> resourcePack = manager.getAppliedServerResourcePack();
            if (resourcePack != null && resourcePack.isPresent()) {
                String url = resourcePack.get().getUrl();
                String checksum = resourcePack.get().getChecksum();
                info.setReturnValue(Optional.of(new MinecraftServer.ServerResourcePackProperties(
                    //#if MC>=12004
                    //$$ // Server resource packs are now given a unique ID for caching purposes.
                    //$$ // Vanilla uses this fallback when the ID is not present.
                    //$$ UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)),
                    //#endif
                    url, checksum, false, null)));
            }
        }
    }
}
