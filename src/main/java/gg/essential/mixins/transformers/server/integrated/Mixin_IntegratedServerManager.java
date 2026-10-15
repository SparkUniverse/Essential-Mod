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
package gg.essential.mixins.transformers.server.integrated;

import gg.essential.mixins.ext.network.NetworkSystemExtKt;
import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import gg.essential.sps.McIntegratedServerManager;
import net.minecraft.client.multiplayer.ThreadLanServerPing;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

import static gg.essential.util.HelpersKt.textTranslatable;

//#if MC < 1.12
//$$ import net.minecraft.client.resources.I18n;
//#endif

@Mixin(IntegratedServer.class)
public abstract class Mixin_IntegratedServerManager implements IntegratedServerExt {
    @Shadow
    //#if MC >= 1.16
    //$$ private int serverPort;
    //#else
    private boolean isPublic;
    //#endif
    @Shadow
    private ThreadLanServerPing lanServerPing;
    @Unique
    private McIntegratedServerManager manager;

    // Note: Needs to be initialized after `this.mc` is set.
    //#if MC>=11200
    @Inject(method = "<init>", at = @At("RETURN"))
    //#else
    //$$ // 1.8.9 in its Minecraft constructor for some reason creates an IntegratedServer without any folder.
    //$$ // It doesn't appear to use it for anything and it's gone it 1.12.2, so we'll just skip creating our manager
    //$$ // in that case by targeting the regular constructor only.
    //$$ @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V", at = @At("RETURN"))
    //#endif
    private void initIntegratedServerManager(CallbackInfo ci) {
        manager = new McIntegratedServerManager((IntegratedServer) (Object) this);
    }

    @NotNull
    @Override
    public McIntegratedServerManager getEssential$manager() {
        return manager;
    }

    //#if MC < 26.2
    // Removed in 26.2 as IntegratedServer exposes unpublishServer which removes the need for undoLan
    @Override
    public void essential$undoLan(@NotNull UUID host) {
        MinecraftServer minecraftServer = (MinecraftServer) (Object) this;
        for (EntityPlayerMP entity : ((LanConnectionsAccessor) minecraftServer.getPlayerList()).getPlayerEntityList()) {
            if (!host.equals(entity.getUniqueID())) {
                //#if MC >= 1.12
                entity.connection.disconnect(textTranslatable("multiplayer.disconnect.server_shutdown"));
                //#else
                //$$ entity.playerNetServerHandler.kickPlayerFromServer(
                //$$     I18n.format("multiplayer.disconnect.server_shutdown")
                //$$ );
                //#endif
            }
        }
        NetworkSystemExtKt.removeLanEndpoint(minecraftServer.getNetworkSystem());
        //#if MC >= 1.16
        //$$ this.serverPort = -1;
        //#else
        this.isPublic = false;
        //#endif
        if (this.lanServerPing != null) {
            this.lanServerPing.interrupt();
            this.lanServerPing = null;
        }
    }
    //#endif
}
