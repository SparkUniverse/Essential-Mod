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
package gg.essential.mixins.transformers.events;

import gg.essential.Essential;
import gg.essential.event.gui.GuiDrawScreenEvent;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class Mixin_GuiDrawScreenEvent_Background {
    //#if MC >= 1.20.2
    //$$ @Inject(method = "renderInGameBackground", at = @At("HEAD"))
    //#else
    @Inject(
        // FIXME remap bug: complains that `renderBackground` is ambiguous because it doesn't consider the type given in extra mappings file
        //#if MC >= 1.16
        //$$ method = "renderBackground(Lcom/mojang/blaze3d/matrix/MatrixStack;I)V",
        //#else
        method = "drawWorldBackground",
        //#endif
        //#if MC >= 1.20
        //$$ at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fillGradient(IIIIII)V")
        //#else
        // FIXME remap bug: does remap to `fillGradient` but with the wrong signature
        //#if MC >= 1.16
        //$$ at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;fillGradient(Lcom/mojang/blaze3d/matrix/MatrixStack;IIIIII)V")
        //#else
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawGradientRect(IIIIII)V")
        //#endif
        //#endif
    )
    //#endif
    private void drawIngameBackground(CallbackInfo ci) {
        Essential.EVENT_BUS.post(new GuiDrawScreenEvent.Background((GuiScreen) (Object) this));
    }

    //#if MC >= 1.20.6
    //$$ @Inject(method = "applyBlur", at = @At("HEAD"))
    //#else
    @Inject(method = "drawBackground", at = @At("HEAD"))
    //#endif
    private void drawBackground(CallbackInfo ci) {
        Essential.EVENT_BUS.post(new GuiDrawScreenEvent.Background((GuiScreen) (Object) this));
    }
}
