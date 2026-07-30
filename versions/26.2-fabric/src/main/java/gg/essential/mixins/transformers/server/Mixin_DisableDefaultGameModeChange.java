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

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameModeCommand.class)
public class Mixin_DisableDefaultGameModeChange {

    // Prevent changing the world's default game mode when changing the host's game mode while hosting.
    @WrapWithCondition(
            method = {
                    "setMode",
                    "setGameMode(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/GameType;)Z"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;setDefaultGameType(Lnet/minecraft/world/level/GameType;)V"
            )
    )
    private static boolean essential$allowDefaultGameModeChange(MinecraftServer instance, GameType gameType) {
        return !((IntegratedServerExt) instance).getEssential$manager().getAppliedOpenToLan();
    }

}
