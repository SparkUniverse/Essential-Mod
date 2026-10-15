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
package gg.essential.mixins.transformers.util;

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.gui.screenshot.components.ScreenshotComponentsKt;
import gg.essential.handlers.screenshot.ClientScreenshotMetadata;
import gg.essential.network.connectionmanager.media.ScreenshotManager;
import gg.essential.util.ExtensionsKt;
import gg.essential.util.Multithreading;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(ScreenShotHelper.class)
public class Mixin_CaptureScreenshots {
    @Inject(method = {
        //#if FABRIC || MC >= 26.1
        //#if MC >= 1.21.5
        //$$ "method_22691",
        //#else
        //$$ "method_1661"
        //#endif
        //#else
        //#if MC>=12105
        //$$ "lambda$grab$1",
        //#else
        "lambda$saveScreenshotRaw$2", // early 1.16
        "lambda$_grab$2", // late 1.16 and above
        //#endif
        //#endif
    }, at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/NativeImage;write(Ljava/io/File;)V",
        shift = At.Shift.AFTER,
        // we disable `remap` on the `Inject` target, because otherwise the mixin AP will qualify the reference, which
        // includes the type of its arguments, but optifine changes those
        remap = true
    ), remap = false)
    private static void essential$captureNewScreenshot(CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) File file) {
        ExtensionsKt.getExecutor(Minecraft.getInstance()).execute(() -> {
            final ScreenshotManager screenshotManager = Essential.getInstance().getConnectionManager().getScreenshotManager();

            ClientScreenshotMetadata metadata = screenshotManager.getCurrentMetadata();
            boolean essentialScreenshots = EssentialConfig.INSTANCE.getEssentialEnabled() && EssentialConfig.INSTANCE.getEssentialScreenshots();

            Multithreading.runAsync(() -> screenshotManager.handleNewScreenshot(file, ScreenshotComponentsKt.cloneWithNewChecksum(metadata, screenshotManager.getChecksum(file)), essentialScreenshots));
        });
    }
}
