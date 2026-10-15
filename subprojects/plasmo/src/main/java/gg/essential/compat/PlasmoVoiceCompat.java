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
package gg.essential.compat;

import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.socket.UdpServer;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Compatibility with plasmovoice 2.x (which uses the modid <code>plasmovoice</code>).
 * Unless a port is set in the plasmo config, a random port is used by default, so we must
 * register an addon to detect the port.
 * Compatibility with 1.x (which uses the modid <code>plasmo_voice</code>) is handled in McIntegratedServerManager.
 */
public class PlasmoVoiceCompat {

    private static PlasmoCompatAddon addon;

    public static Optional<Integer> getPort() {
        if (addon == null) {
            addon = new PlasmoCompatAddon();
            PlasmoVoiceServer.getAddonsLoader().load(addon);
        }

        if (addon.server != null) {
            return addon.server.getUdpServer().flatMap(UdpServer::getRemoteAddress).map(InetSocketAddress::getPort);
        } else {
            return Optional.empty();
        }
    }

    // Separate class to so that javac from the main project doesn't try to resolve the AddonLoaderScope enum and print a warning
    @Addon(
        id = "essential-plasmo-compat",
        name = "Essential Plasmo Compatibility",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        license = "ARR",
        authors = "SparkUniverse"
    )
    private static class PlasmoCompatAddon {
        @Inject private PlasmoVoiceServer server;
    }
}
