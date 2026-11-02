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
package gg.essential.mixins.transformers.feature.core;

import gg.essential.Essential;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC >= 1.16 && MC < 26.2
//$$ import org.objectweb.asm.Opcodes;
//#endif

@Mixin(Minecraft.class)
public class Mixin_Shutdown_Late {
    //#if MC >= 26.2
    //$$ @Inject(
    //$$     method = "close",
        //#if MC >= 26.3
        //$$ at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shutdownExecutors()V", shift = At.Shift.AFTER)
        //#else
        //$$ at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwTerminate()V", shift = At.Shift.AFTER)
        //#endif
    //$$ )
    //#else
    @Inject(
        method = "shutdownMinecraftApplet",
        //#if MC >= 1.19.2
        //$$ at = @At(value = "FIELD", target = "Lnet/minecraft/util/Util;nanoTimeSupplier:Lnet/minecraft/util/TimeSupplier$Nanoseconds;", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER)
        //#elseif MC >= 1.16
        //$$ at = @At(value = "FIELD", target = "Lnet/minecraft/util/Util;nanoTimeSupplier:Ljava/util/function/LongSupplier;", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER)
        //#else
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;destroy()V", shift = At.Shift.AFTER)
        //#endif
    )
    //#endif
    private void shutdown(CallbackInfo info) {
        Essential.getInstance().shutdownLate();
    }
}
