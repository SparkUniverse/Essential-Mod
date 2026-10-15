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

import gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticsUserUnlockedPacket;
import gg.essential.cosmetics.model.CosmeticUnlockData;
import gg.essential.gui.common.CosmeticToastsKt;
import gg.essential.network.connectionmanager.cosmetics.CosmeticsManager;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.cosmetics.Cosmetic;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

import static gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticsUserUnlockedPacket.suppressNotifications;

public class ServerCosmeticsUserUnlockedPacketHandler implements PacketHandler<ServerCosmeticsUserUnlockedPacket> {
    private final CosmeticsManager cosmeticsManager;

    public ServerCosmeticsUserUnlockedPacketHandler(CosmeticsManager cosmeticsManager) {
        this.cosmeticsManager = cosmeticsManager;
    }

    @Override
    public void handle(@NotNull final ServerCosmeticsUserUnlockedPacket packet) {
        final Set<String> currentUnlocks = cosmeticsManager.getUnlockedCosmetics().get();

        Map<String, CosmeticUnlockData> newUnlocks = packet.getUnlockedCosmetics();

        cosmeticsManager.addUnlockedCosmeticsData(newUnlocks);

        if (!packet.occurredFromPurchase()) {
            cosmeticsManager.getCapeManager().unlockMissingCapesAsync();
            return;
        }

        newUnlocks.entrySet().stream().filter(e -> !currentUnlocks.contains(e.getKey())).forEach(e -> {
            final String newCosmetic = e.getKey();
            final CosmeticUnlockData unlockData = e.getValue();
            if ((unlockData == null || unlockData.isWardrobeUnlock())) {
                return;
            }
            if (suppressNotifications.contains(newCosmetic)) {
                suppressNotifications.remove(newCosmetic);
                return;
            }
            final Cosmetic cosmetic = cosmeticsManager.getCosmetic(newCosmetic);
            if (cosmetic != null) {
                CosmeticToastsKt.sendCosmeticUnlockedToast(cosmetic);
            }
        });
    }
}
