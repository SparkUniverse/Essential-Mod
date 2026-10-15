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
package gg.essential.network.connectionmanager.handler.cosmetics;

import gg.essential.Essential;
import gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticAnimationTriggerPacket;
import gg.essential.mod.cosmetics.CosmeticSlot;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

import static gg.essential.network.cosmetics.ConversionsKt.toMod;

public class ServerCosmeticAnimationTriggerPacketHandler implements PacketHandler<ServerCosmeticAnimationTriggerPacket> {

    @Override
    public void handle(@NotNull final ServerCosmeticAnimationTriggerPacket packet) {
        UUID userId = packet.getUserId();
        CosmeticSlot slot = toMod(packet.getCosmeticSlot());
        String triggerName = packet.getTriggerName();

        Essential.getInstance().getCosmeticEventEmitter().triggerEvent(userId, slot, triggerName);
    }

}
