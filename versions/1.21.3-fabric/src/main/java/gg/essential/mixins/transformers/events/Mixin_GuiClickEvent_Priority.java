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
// 1.21.2 and above
package gg.essential.mixins.transformers.events;

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.Essential;
import gg.essential.event.gui.GuiClickEvent;
import gg.essential.universal.UScreen;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC>=12109
//$$ import net.minecraft.client.gui.Click;
//#endif

@Mixin(value = Mouse.class, priority = 500)
public abstract class Mixin_GuiClickEvent_Priority  {
    //#if MC>=12109
    //#if FORGE
    //$$ private static final String MOUSE_CLICKED = "Lnet/minecraftforge/client/event/ForgeEventFactoryClient;onScreenMouseClicked(Lnet/minecraft/client/gui/screens/Screen;DDLnet/minecraft/client/input/MouseButtonEvent;Z)Z";
    //#else
    //$$ private static final String MOUSE_CLICKED = "Lnet/minecraft/client/gui/screen/Screen;mouseClicked(Lnet/minecraft/client/gui/Click;Z)Z";
    //#endif
    //#else
    //#if FORGE
    //$$ private static final String MOUSE_CLICKED = "Lnet/minecraftforge/client/event/ForgeEventFactoryClient;onScreenMouseClicked(Lnet/minecraft/client/gui/screens/Screen;DDI)Z";
    //#else
    private static final String MOUSE_CLICKED = "Lnet/minecraft/client/gui/screen/Screen;mouseClicked(DDI)Z";
    //#endif
    //#endif

    @Inject(method = "onMouseButton", at = @At(value = "INVOKE", target = MOUSE_CLICKED), cancellable = true)
    private void onMouseClicked(
        CallbackInfo ci,
        @Local(ordinal = 0) Screen screen,
        //#if MC>=12109
        //$$ @Local Click click
        //#else
        @Local(ordinal = 0, argsOnly = true) int mouseButton,
        @Local(ordinal = 0) double mouseX,
        @Local(ordinal = 1) double mouseY
        //#endif
    ) {
        //#if MC>=12109
        //$$ GuiClickEvent event = new GuiClickEvent.Priority(click.x(), click.y(), click.button(), screen);
        //#else
        GuiClickEvent event = new GuiClickEvent.Priority(mouseX, mouseY, mouseButton, screen);
        //#endif
        Essential.EVENT_BUS.post(event);
        if (event.isCancelled() || UScreen.getCurrentScreen() != screen) {
            ci.cancel();
        }
    }
}
