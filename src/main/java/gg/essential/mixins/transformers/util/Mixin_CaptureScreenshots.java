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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.gui.screenshot.components.ScreenshotComponentsKt;
import gg.essential.handlers.screenshot.ClientScreenshotMetadata;
import gg.essential.network.connectionmanager.media.ScreenshotManager;
import gg.essential.util.Multithreading;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

@Mixin(ScreenShotHelper.class)
public abstract class Mixin_CaptureScreenshots {

    @WrapOperation(
        method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/text/ITextComponent;",
        at = @At(value = "INVOKE", target = "Ljavax/imageio/ImageIO;write(Ljava/awt/image/RenderedImage;Ljava/lang/String;Ljava/io/File;)Z")
    )
    private static boolean essential$saveScreenshot(
        final RenderedImage im,
        final String formatName,
        final File output,
        final Operation<Boolean> original
    ) throws IOException {
        final ScreenshotManager screenshotManager = Essential.getInstance().getConnectionManager().getScreenshotManager();
        final ClientScreenshotMetadata metadata = screenshotManager.getCurrentMetadata();

        if (EssentialConfig.INSTANCE.getEssentialScreenshots()) {
            // This is required to be done before we fork, because if another screenshot is taken in the same second,
            // Minecraft will assign the same file name, as it thinks that name is available.
            output.createNewFile();
            screenshotManager.saveScreenshotAsync(im, output, metadata);
            return true;
        }

        final boolean result = original.call(im, formatName, output);
        Multithreading.runAsync(() -> {
            final String checksum = screenshotManager.getChecksum(output);
            if (checksum == null) {
                Essential.logger.info("Unable to read checksum for screenshot '{}'", output.getAbsolutePath());
                return;
            }

            screenshotManager.handleNewScreenshot(
                output,
                ScreenshotComponentsKt.cloneWithNewChecksum(metadata, checksum),
                false
            );
        });
        return result;
    }

}
