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
package gg.essential.mixins.transformers.client.gui;

import gg.essential.gui.proxies.ScreenWithProxiesHandler;
import gg.essential.gui.proxies.ScreenWithVanillaProxyElementsExt;
import gg.essential.handlers.PauseMenuDisplay;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu_ProxyButtons implements ScreenWithVanillaProxyElementsExt {

    @Unique
    private ScreenWithProxiesHandler proxyHandler;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void addProxyButtons(CallbackInfo ci) {
        if (PauseMenuDisplay.isEnabled()) {
            proxyHandler = ScreenWithProxiesHandler.forMainMenu((GuiScreen) (Object) this);
            proxyHandler.initGui();
        } else {
            proxyHandler = null;
        }
    }

    @Override
    public ScreenWithProxiesHandler essential$getProxyHandler() {
        return proxyHandler;
    }
}
