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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gg.essential.mixins.ext.server.integrated.IntegratedServerExt;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
public abstract class Mixin_FixCheatsAndOpPermissions extends MinecraftServer implements IntegratedServerExt {
    @ModifyReturnValue(method = "getProfilePermissions", at = @At(value = "RETURN"))
    public LevelBasedPermissionSet modifyProfilePermissions(LevelBasedPermissionSet original, NameAndId nameAndId) {
        var ops = getEssential$manager().getAppliedOps();
        if (ops != null && ops.contains(nameAndId.id())) {
            var opPermissions = operatorUserPermissions();
            return original.level().isEqualOrHigherThan(opPermissions.level()) ? original : opPermissions;
        }
        return original;
    }

    Mixin_FixCheatsAndOpPermissions() { super(null, null, null, null, null, null, null, null, null, false, null); }
}
