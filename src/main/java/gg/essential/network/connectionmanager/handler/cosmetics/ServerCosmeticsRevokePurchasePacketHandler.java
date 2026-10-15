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

import gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticsRevokePurchasePacket;
import gg.essential.gui.notification.ExtensionsKt;
import gg.essential.gui.notification.Notifications;
import gg.essential.mod.cosmetics.settings.CosmeticProperty;
import gg.essential.network.connectionmanager.cosmetics.CosmeticsManager;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.cosmetics.Cosmetic;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ServerCosmeticsRevokePurchasePacketHandler implements PacketHandler<ServerCosmeticsRevokePurchasePacket> {

    private final CosmeticsManager cosmeticsManager;

    public ServerCosmeticsRevokePurchasePacketHandler(CosmeticsManager cosmeticsManager) {
        this.cosmeticsManager = cosmeticsManager;
    }

    @Override
    public void handle(@NotNull ServerCosmeticsRevokePurchasePacket packet) {
        cosmeticsManager.removeUnlockedCosmetics(packet.getRevokedIds());

        for (String revokedId : packet.getRevokedIds()) {
            Cosmetic cosmetic = cosmeticsManager.getCosmetic(revokedId);
            if (cosmetic != null) {

                // Check if the cosmetic required an external unlock action and send an alternate toast if that is the case.
                Optional<CosmeticProperty> requiresUnlockAction = cosmetic.getProperties().stream().filter(cosmeticSetting -> cosmeticSetting instanceof CosmeticProperty.RequiresUnlockAction).findFirst();
                if (requiresUnlockAction.isPresent()) {
                    sendExternalActionRevokedToast(cosmetic, (CosmeticProperty.RequiresUnlockAction) requiresUnlockAction.get());
                    return;
                }
                ExtensionsKt.warning(
                    Notifications.INSTANCE,
                    "Cosmetic revoked",
                    String.format("Access to %s has been revoked because of a refund or chargeback.", cosmetic.getDisplayName("en_us"))
                );

            }
        }
    }

    private void sendExternalActionRevokedToast(Cosmetic cosmetic, CosmeticProperty.RequiresUnlockAction property) {
        CosmeticProperty.RequiresUnlockAction.Data data = property.getData();

        if (data instanceof CosmeticProperty.RequiresUnlockAction.Data.OpenLink) {
            String linkAddress = ((CosmeticProperty.RequiresUnlockAction.Data.OpenLink) property.getData()).getLinkAddress();

            switch (cosmetic.getId()) {
                case "ESSENTIAL_DISCORD_CAPE": {
                    ExtensionsKt.warning(
                        Notifications.INSTANCE,
                        "Discord Cosmetics revoked",
                        "Access to Discord cosmetics have been revoked. You left or unlinked your account."
                    );
                    break;
                }
            }
        }
    }
}
