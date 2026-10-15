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
package gg.essential.mixins.transformers.client.network;

import gg.essential.Essential;
import gg.essential.mixins.ext.network.NetworkSystemExt;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.NetworkSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.util.List;

// Removed in 26.2 as IntegratedServer exposes unpublishServer which removes the need for this mixin
@Mixin(NetworkSystem.class)
public abstract class Mixin_RemoveLanEndpoint implements NetworkSystemExt {

    @Shadow
    @Final
    private List<ChannelFuture> endpoints;

    @Unique
    private ChannelFuture essential$lanEndpoint = null;

    @Inject(method = "addLanEndpoint(Ljava/net/InetAddress;I)V", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void captureLanEndpoint(InetAddress address, int port, CallbackInfo ci) {
        essential$lanEndpoint = this.endpoints.get(this.endpoints.size() - 1);
    }

    @Unique
    @Override
    public void essential$removeLanEndpoint() {
        if (essential$lanEndpoint != null) {
            try {
                essential$lanEndpoint.channel().close().sync();
                this.endpoints.remove(essential$lanEndpoint);
                essential$lanEndpoint = null;
            } catch (InterruptedException e) {
                Essential.logger.error("Unable to close lan endpoint.", e);
            }
        }
    }

}
