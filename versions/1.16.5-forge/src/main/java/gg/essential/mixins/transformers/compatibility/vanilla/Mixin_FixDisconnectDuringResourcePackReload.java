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
package gg.essential.mixins.transformers.compatibility.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LoadingGui;
import net.minecraft.client.gui.ResourceLoadProgressGui;
import net.minecraft.util.Util;
import net.minecraft.util.concurrent.RecursiveEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.TimeUnit;

//#if MC >= 26.2
//$$ import net.minecraft.client.gui.Gui;
//$$ import org.spongepowered.asm.mixin.Final;
//#else
import org.jetbrains.annotations.Nullable;
//#endif

/**
 * Minecraft, when disconnecting, discards all queued tasks.
 * This can cause any currently-running resource reload to become live-locked waiting indefinitely for its task to
 * execute if that task was discarded.
 *
 * This mixin fixes that by blocking until the current resource reload is done, before letting MC discard any remaining
 * tasks.
 */
@Mixin(Minecraft.class)
public abstract class Mixin_FixDisconnectDuringResourcePackReload extends RecursiveEventLoop<Runnable> {
    //#if MC >= 26.2
    //$$ @Shadow public @Final Gui gui;
    //#else
    @Shadow public @Nullable LoadingGui loadingGui;
    //#endif

    @Inject(
        //#if MC >= 1.21.11
        //$$ method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V",
        //#elseif MC >= 1.20.5
        //$$ method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V",
        //#else
        method = "unloadWorld(Lnet/minecraft/client/gui/screen/Screen;)V",
        //#endif
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;dropTasks()V")
    )
    private void blockUntilResourceReloadDone(CallbackInfo ci) {
        while (getOverlay() instanceof ResourceLoadProgressGui) {
            long finishTime = Util.nanoTime() + TimeUnit.SECONDS.toNanos(1L) / 60L;

            //#if MC >= 1.21.9
            //$$ var overlay = getOverlay();
            //$$ if (overlay != null) {
            //$$     overlay.tick();
            //$$ }
            //#endif

            this.runGameLoop(false);

            this.drainTasks();
            this.driveUntil(() -> Util.nanoTime() > finishTime);
        }
    }

    @Unique
    private LoadingGui getOverlay() {
        //#if MC >= 26.2
        //$$ return this.gui.overlay();
        //#else
        return this.loadingGui;
        //#endif
    }

    @Shadow private void runGameLoop(boolean advanceGameTime) {}

    //#if MC >= 26.1
    //$$ Mixin_FixDisconnectDuringResourcePackReload() {super(null, false);}
    //#else
    Mixin_FixDisconnectDuringResourcePackReload() {super(null);}
    //#endif
}
