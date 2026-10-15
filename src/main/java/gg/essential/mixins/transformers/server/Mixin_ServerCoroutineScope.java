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
package gg.essential.mixins.transformers.server;

import gg.essential.mixins.ext.server.MinecraftServerExt;
import gg.essential.util.SingleThreadDispatcher;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static kotlinx.coroutines.CoroutineScopeKt.CoroutineScope;
import static kotlinx.coroutines.CoroutineScopeKt.cancel;
import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

@Mixin(MinecraftServer.class)
public abstract class Mixin_ServerCoroutineScope implements MinecraftServerExt {
    @Unique
    private SingleThreadDispatcher dispatcher;

    @Unique
    private CoroutineScope coroutineScope;

    //#if MC>=11200
    @Inject(method = "<init>", at = @At("RETURN"))
    //#else
    //$$ @Inject(method = "<init>(Ljava/io/File;Ljava/net/Proxy;Ljava/io/File;)V", at = @At("RETURN"))
    //#endif
    private void init(CallbackInfo ci) {
        // FIXME mixin doesn't emit the first of these two lines if I make them field initializers, why?
        dispatcher = new SingleThreadDispatcher("MinecraftServer.dispatcher", null);
        coroutineScope = CoroutineScope(SupervisorJob(null).plus(dispatcher));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        dispatcher.runTasks();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void cancelCoroutineScope(CallbackInfo ci) {
        cancel(coroutineScope, null);

        dispatcher.runTasks();
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void shutdownDispatcher(CallbackInfo ci) {
        dispatcher.shutdown();
    }

    @NotNull
    @Override
    public CoroutineDispatcher getEssential$dispatcher() {
        return dispatcher;
    }

    @NotNull
    @Override
    public CoroutineScope getEssential$coroutineScope() {
        return coroutineScope;
    }
}
