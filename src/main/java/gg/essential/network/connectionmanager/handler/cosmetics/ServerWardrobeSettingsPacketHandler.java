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

import gg.essential.connectionmanager.common.packet.wardrobe.ServerWardrobeSettingsPacket;
import gg.essential.model.EssentialAsset;
import gg.essential.network.connectionmanager.cosmetics.InfraCosmeticsData;
import gg.essential.network.connectionmanager.cosmetics.WardrobeSettings;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.network.cosmetics.ConversionsKt;
import org.jetbrains.annotations.NotNull;

public class ServerWardrobeSettingsPacketHandler implements PacketHandler<ServerWardrobeSettingsPacket> {

    private final InfraCosmeticsData infraCosmeticsData;
    private final WardrobeSettings settings;

    public ServerWardrobeSettingsPacketHandler(InfraCosmeticsData infraCosmeticsData, WardrobeSettings settings) {
        this.infraCosmeticsData = infraCosmeticsData;
        this.settings = settings;
    }

    @Override
    public void handle(@NotNull final ServerWardrobeSettingsPacket packet) {
        EssentialAsset currentFeaturedPageConfig = packet.getCurrentFeaturedPageConfig();
        if (currentFeaturedPageConfig != null) {
            infraCosmeticsData.addFeaturedPageCollection(ConversionsKt.toMod(currentFeaturedPageConfig));
        }
        infraCosmeticsData.addFeaturedPageCollection(ConversionsKt.toMod(packet.getFallbackFeaturedPageConfig()));
        settings.populateOutfitsLimit(packet.getOutfitsLimit());
        settings.populateSkinsLimit(packet.getSkinsLimit());
        settings.populateGiftingCoinSpendRequirement(packet.getGiftingCoinSpendRequirement());
    }

}
