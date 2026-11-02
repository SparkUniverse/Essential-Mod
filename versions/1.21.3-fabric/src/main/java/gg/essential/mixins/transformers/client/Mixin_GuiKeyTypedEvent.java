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
package gg.essential.mixins.transformers.client;

import com.llamalad7.mixinextras.sugar.Local;
import gg.essential.Essential;
import gg.essential.event.gui.GuiKeyTypedEvent;
import net.minecraft.client.Keyboard;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC>=12109
//$$ import net.minecraft.client.input.CharInput;
//$$ import net.minecraft.client.input.KeyInput;
//#endif

@Mixin(value = Keyboard.class, priority = 500)
public class Mixin_GuiKeyTypedEvent {
    //#if MC>=12109
    //#if FORGE
    //$$ private static final String KEY_PRESSED = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenKeyPressed(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/KeyEvent;)Z";
    //$$ private static final String CHAR_TYPED = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenCharTyped(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/input/CharacterEvent;)Z";
    //#else
    //$$ private static final String KEY_PRESSED = "Lnet/minecraft/client/gui/screen/Screen;keyPressed(Lnet/minecraft/client/input/KeyInput;)Z";
    //$$ private static final String CHAR_TYPED = "Lnet/minecraft/client/gui/screen/Screen;charTyped(Lnet/minecraft/client/input/CharInput;)Z";
    //#endif
    //#else
    //#if FORGE
    //$$ private static final String KEY_PRESSED = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenKeyPressed(Lnet/minecraft/client/gui/screens/Screen;III)Z";
    //$$ private static final String CHAR_TYPED = "Lnet/minecraftforge/client/ForgeHooksClient;onScreenCharTyped(Lnet/minecraft/client/gui/screens/Screen;CI)Z";
    //#else
    private static final String KEY_PRESSED = "Lnet/minecraft/client/gui/screen/Screen;keyPressed(III)Z";
    private static final String CHAR_TYPED = "Lnet/minecraft/client/gui/screen/Screen;charTyped(CI)Z";
    //#endif
    //#endif

    @Unique
    private static void keyTyped(Screen screen, char typedChar, int keyCode, int scancode, CallbackInfo ci) {
        GuiKeyTypedEvent event = new GuiKeyTypedEvent(screen, typedChar, keyCode, scancode);
        Essential.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Group(name = "onKeyTyped")
    @Inject(method = "onKey", at = @At(value = "INVOKE", target = KEY_PRESSED), cancellable = true)
    private void onKeyTyped(
        CallbackInfo ci,
        @Local(ordinal = 0) Screen screen,
        //#if MC>=12109
        //$$ @Local(ordinal = 0, argsOnly = true) KeyInput keyInput
        //#else
        @Local(ordinal = 0, argsOnly = true) int key,
        @Local(ordinal = 1, argsOnly = true) int scancode
        //#endif
    ) {
        //#if MC >= 26.3
        //$$ int key = keyInput.keycode();
        //$$ int scancode = keyInput.key();
        //#elseif MC >= 1.21.9
        //$$ int key = keyInput.key();
        //$$ int scancode = keyInput.scancode();
        //#endif
        keyTyped(screen, '\0', key, scancode, ci);
    }

    @Group(name = "onCharTyped")
    @Inject(method = "onChar", at = @At(value = "INVOKE", target = CHAR_TYPED), cancellable = true)
    private void onCharTyped(
        CallbackInfo ci,
        @Local(ordinal = 0) Screen screen,
        //#if MC>=12109
        //$$ @Local(ordinal = 0, argsOnly = true) CharInput charInput
        //#else
        @Local(ordinal = 0, argsOnly = true) int codePoint
        //#endif
    ) {
        //#if MC>=12109
        //$$ int codePoint = charInput.codepoint();
        //#endif
        if (Character.isBmpCodePoint(codePoint)) {
            keyTyped(screen, (char) codePoint, 0, -1, ci);
        } else if (Character.isValidCodePoint(codePoint)) {
            keyTyped(screen, Character.highSurrogate(codePoint), 0, -1, ci);
            keyTyped(screen, Character.lowSurrogate(codePoint), 0, -1, ci);
        }
    }

    //#if FORGE
    //$$ // Optifine Compat
    //$$
    //$$ private static final String OPTIFINE_ONSCREENKEYPRESSED = "Lnet/optifine/reflect/Reflector;ForgeHooksClient_onScreenKeyPressed:Lnet/optifine/reflect/ReflectorMethod;";
    //$$ private static final String OPTIFINE_CALL = "Lnet/optifine/reflect/Reflector;call(Lnet/optifine/reflect/ReflectorMethod;[Ljava/lang/Object;)Ljava/lang/Object;";
    //$$
    //$$ @Group(name = "onKeyTyped")
    //$$ @Dynamic("OptiFine calls ForgeHooksClient.onScreenKeyPressed via reflection. This is the second call in the method")
    //$$ @Inject(method = "keyPress", at = @At(value = "FIELD", target = OPTIFINE_ONSCREENKEYPRESSED, ordinal = 1, remap = false), cancellable = true)
    //#if MC>=12109
    //$$ private void optifineKeyPressed(CallbackInfo ci, @Local(ordinal = 0) Screen screen, @Local(ordinal = 0, argsOnly = true) KeyEvent keyInput) {
        //#if MC >= 26.3
        //$$ int key = keyInput.keycode();
        //$$ int scancode = keyInput.key();
        //#else
        //$$ int key = keyInput.key();
        //$$ int scancode = keyInput.scancode();
        //#endif
    //#else
    //$$ private void optifineKeyPressed(CallbackInfo ci, @Local(ordinal = 0) Screen screen, @Local(ordinal = 0, argsOnly = true) int key, @Local(ordinal = 1, argsOnly = true) int scancode) {
    //#endif
    //$$     keyTyped(screen, '\0', key, scancode, ci);
    //$$ }
    //$$
    //$$ @Group(name = "onCharTyped")
    //$$ @Dynamic("OptiFine calls ForgeHooksClient.onScreenCharTyped via reflection. It is called multiple times")
    //$$ @Inject(method = "charTyped", at = @At(value = "INVOKE", target = OPTIFINE_CALL, remap = false), cancellable = true)
    //#if MC>=12109
    //$$ private void optifineCharTyped(CallbackInfo ci, @Local(ordinal = 0) Screen screen, @Local(ordinal = 0, argsOnly = true) CharacterEvent charInput) {
    //$$     int codePoint = charInput.codepoint();
    //#else
    //$$ private void optifineCharTyped(CallbackInfo ci, @Local(ordinal = 0) Screen screen, @Local(ordinal = 0, argsOnly = true) int codePoint) {
    //#endif
    //$$     if (Character.isBmpCodePoint(codePoint)) {
    //$$         keyTyped(screen, (char) codePoint, 0, -1, ci);
    //$$     } else if (Character.isValidCodePoint(codePoint)) {
    //$$         keyTyped(screen, Character.highSurrogate(codePoint), 0, -1, ci);
    //$$         keyTyped(screen, Character.lowSurrogate(codePoint), 0, -1, ci);
    //$$     }
    //$$ }
    //#endif
}
