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
import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import gg.essential.sps.McIntegratedServerManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.network.status.server.SPacketServerInfo;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class Mixin_PublishServerStatusResponse implements MinecraftServerExt {
    @Shadow
    //#if MC<11904
    @Final
    //#endif
    private ServerStatusResponse statusResponse;

    @Inject(method = "tick",
        at = @At(
            //#if MC>=11904
            //$$ value = "FIELD",
            //$$ target = "Lnet/minecraft/server/MinecraftServer;metadata:Lnet/minecraft/server/ServerMetadata;",
            //#else
            value = "INVOKE",
            //#if MC==10809
            //$$ target = "Lnet/minecraft/network/ServerStatusResponse$PlayerCountData;setPlayers([Lcom/mojang/authlib/GameProfile;)V",
            //#else
            target = "Lnet/minecraft/network/ServerStatusResponse$Players;setPlayers([Lcom/mojang/authlib/GameProfile;)V",
            //#endif
            //#endif
            shift = At.Shift.AFTER
        )
    )
    private void publishUpdatedStatus(CallbackInfo ci) {
        essential$updateServerStatus();
    }

    @Override
    public void essential$updateServerStatus() {
        McIntegratedServerManager manager =
            this instanceof IntegratedServerExt ? ((IntegratedServerExt) this).getEssential$manager() : null;
        if (manager != null) {
            // Not using .getJson() directly cause that's Forge-only
            String response;
            try {
                PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
                SPacketServerInfo packet = new SPacketServerInfo(this.statusResponse);
                //#if MC>=12005
                //$$ QueryResponseS2CPacket.CODEC.encode(buf, packet);
                //#else
                packet.writePacketData(buf);
                //#endif
                response = buf.readString(Short.MAX_VALUE);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            manager.updateServerStatusResponse(response);
        }
    }
}
