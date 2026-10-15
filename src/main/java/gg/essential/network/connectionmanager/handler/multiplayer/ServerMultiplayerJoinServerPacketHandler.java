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
package gg.essential.network.connectionmanager.handler.multiplayer;

import gg.essential.connectionmanager.common.packet.multiplayer.ServerMultiplayerJoinServerPacket;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.universal.UMinecraft;
import gg.essential.util.MinecraftUtils;
import gg.essential.util.Multithreading;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ServerMultiplayerJoinServerPacketHandler implements PacketHandler<ServerMultiplayerJoinServerPacket> {
    private final Set<String> cooldowns = new HashSet<>();

    @Override
    public void handle(@NotNull final ServerMultiplayerJoinServerPacket packet) {
        String serverIP = packet.getAddress();
        if (cooldowns.contains(serverIP)) return;
        cooldowns.add(serverIP);
        Multithreading.scheduleOnMainThread(() -> cooldowns.remove(serverIP), 7, TimeUnit.SECONDS);
        //#if MC>=11602
        //$$ UMinecraft.getMinecraft().execute(() -> {
        //#else
        UMinecraft.getMinecraft().addScheduledTask(() -> {
        //#endif
            MinecraftUtils.INSTANCE.connectToServer(serverIP, serverIP, ServerData.ServerResourceMode.PROMPT);
        });
    }
}