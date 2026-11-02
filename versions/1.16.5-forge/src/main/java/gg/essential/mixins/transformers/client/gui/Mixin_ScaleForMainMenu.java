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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gg.essential.handlers.PauseMenuDisplay;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MainWindow.class)
public class Mixin_ScaleForMainMenu {
    @ModifyExpressionValue(method = "calcGuiScale", at = @At(value = "CONSTANT", args = "intValue=320"))
    private int modifyMinWidth(int original) {
        Screen screen = currentScreen();
        return screen != null && PauseMenuDisplay.canRescale(screen) ? PauseMenuDisplay.getMinWidth() : original;
    }

    // NeoForge may call our code way too early during boot, so we need to guard against `gui` still being null.
    // The offending call: https://github.com/neoforged/NeoForge/blob/95f9f88d79c57eee43a63151fbfbbcc827692106/src/client/java/net/neoforged/neoforge/client/loading/earlydisplay/EarlyWindowHandoff.java#L61
    // Patches for vanilla code which is also broken by that: https://github.com/neoforged/NeoForge/blob/95f9f88d79c57eee43a63151fbfbbcc827692106/patches/net/minecraft/client/Minecraft.java.patch#L217
    @SuppressWarnings("ConstantValue")
    @Unique
    private Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        //#if MC >= 26.2
        //$$ if (mc.gui == null) return null;
        //$$ return mc.gui.screen();
        //#else
        return mc.currentScreen;
        //#endif
    }
}
