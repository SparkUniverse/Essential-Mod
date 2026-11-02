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
package gg.essential.mixins.transformers.client.renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gg.essential.handlers.ZoomHandler;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class MixinEntityRenderer_Zoom {
    // NeoForge adds an extra `useFovSetting` argument
    //#if NEOFORGE && MC == 26.1.2
    //$$ private static final String TARGET =  "Lnet/minecraft/client/Camera;modifyFovBasedOnDeathOrFluid(FFZ)F";
    //#else
    private static final String TARGET =  "Lnet/minecraft/client/Camera;modifyFovBasedOnDeathOrFluid(FF)F";
    //#endif

    @ModifyExpressionValue(method = "calculateFov", at = @At(value = "INVOKE", target = TARGET))
    private float applyZoomModifiers(float f) {
        return ZoomHandler.getInstance().applyModifiers(f);
    }
}
