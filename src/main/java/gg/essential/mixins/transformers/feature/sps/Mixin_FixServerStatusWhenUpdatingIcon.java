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

import gg.essential.mixins.ext.server.MinecraftServerExt;
import gg.essential.mixins.transformers.server.MinecraftServerAccessor;
import gg.essential.universal.UMinecraft;
import kotlin.coroutines.EmptyCoroutineContext;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class Mixin_FixServerStatusWhenUpdatingIcon {

    //#if MC >= 1.12
    @Inject(
        method = {
            // FIXME for unknown reasons archloom's mappings for this method are incorrect on NeoForge 1.21.7-11
            //#if NEOFORGE && MC == 1.21.11
            //$$ "lambda$takeAutoScreenshot$9",
            //#elseif NEOFORGE && MC >= 1.21.7 && MC <= 1.21.8
            //$$ "lambda$takeAutoScreenshot$6",
            //#else
            //#if MC >= 1.16.5
            //$$ "func_215310_a",
            //#else
            "createWorldIcon",
            //#endif
            //#endif

            // OptiFine
            //#if FORGE
            //#if MC >= 1.20.1
            //$$ "lambda$takeAutoScreenshot$68",
            //#elseif MC >= 1.19.4
            //$$ "lambda$takeAutoScreenshot$66",
            //#elseif MC >= 1.19
            //$$ "lambda$takeAutoScreenshot$64",
            //#elseif MC >= 1.17
            //$$ "lambda$takeAutoScreenshot$63",
            //#elseif MC >= 1.16
            //$$ "lambda$createWorldIcon$5",
            //#endif
            //#endif
        },
            at = @At(
                    value = "INVOKE",
                    //#if MC >= 1.17.1
                    //$$ target = "Lnet/minecraft/client/texture/NativeImage;writeFile(Ljava/nio/file/Path;)V",
                    //#elseif MC >= 1.16.5
                    //$$ target = "Lnet/minecraft/client/renderer/texture/NativeImage;write(Ljava/io/File;)V",
                    //#else
                    target = "Ljavax/imageio/ImageIO;write(Ljava/awt/image/RenderedImage;Ljava/lang/String;Ljava/io/File;)Z",
                    //#endif
                    shift = At.Shift.AFTER
            )
    )
    private
    //#if MC >= 1.17.1
    //$$ static
    //#endif
    void essential$onCreateWorldIcon(CallbackInfo ci) {
        IntegratedServer integratedServer = UMinecraft.getMinecraft().getIntegratedServer();
        if (integratedServer == null) {
            return;
        }
        MinecraftServerExt minecraftServerExt = (MinecraftServerExt) integratedServer;
        minecraftServerExt.getEssential$dispatcher().dispatch(EmptyCoroutineContext.INSTANCE, () -> {
            MinecraftServerAccessor accessor = (MinecraftServerAccessor) integratedServer;
            //#if MC >= 1.19.4
            //$$ accessor.setFavicon(accessor.invokeLoadFavicon().orElse(null));
            //$$ accessor.setMetadata(accessor.invokeCreateMetadata());
            //#else
            accessor.invokeApplyServerIconToResponse(integratedServer.getServerStatusResponse());
            //#endif
            minecraftServerExt.essential$updateServerStatus();
        });
    }
    //#else
    //$$ // There is no icon system in 1.8.9
    //#endif

}
