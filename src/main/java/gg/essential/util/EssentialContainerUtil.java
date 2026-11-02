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
package gg.essential.util;

import gg.essential.Essential;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static gg.essential.Essential.logger;

public class EssentialContainerUtil {
    public static boolean isContainerPresent() {
        //#if FABRIC
        //$$ return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("essential-container");
        //#elseif FORGE && MC >= 1.17 || NEOFORGE && MC >= 1.17 && MC < 1.21.11
        //$$ return cpw.mods.modlauncher.Launcher.INSTANCE.findLayerManager()
        //$$     .flatMap(it -> it.getLayer(cpw.mods.modlauncher.api.IModuleLayerManager.Layer.SERVICE))
        //$$     .map(ModuleLayer::modules)
        //$$     .orElseGet(java.util.Collections::emptySet)
        //$$     .stream()
        //$$     .anyMatch(it -> it.getClassLoader().getResource("essential_container_marker.txt") != null);
        //#else
        return EssentialContainerUtil.class.getClassLoader().getResource("essential_container_marker.txt") != null;
        //#endif
    }

    public static void updateStage1IfOutdated(Path gameDir) throws Exception {
        //#if FABRIC
        //$$ String variant = "fabric";
        //#elseif NEOFORGE && MC >= 1.21.11
        //$$ String variant = "fml10";
        //#elseif MC>=11700
        //$$ String variant = "modlauncher"; // note: not using `modlauncher9` to stay compatible with old stage0
        //#elseif MC>=11600
        //$$ String variant = "modlauncher8";
        //#else
        String variant = "launchwrapper";
        //#endif

        Path dataDir = gameDir
            .resolve("essential")
            .resolve("loader")
            .resolve("stage0")
            .resolve(variant);
        Path updatePath = dataDir.resolve("stage1.update.jar");
        Path activePath = dataDir.resolve("stage1.jar");

        URL activeUrl = activePath.toUri().toURL();
        URL bundledUrl = Essential.class.getResource("loader-stage1.jar");

        if (bundledUrl == null) {
            throw new RuntimeException("Failed to retrieve bundled stage1 jar.");
        }

        int activeVersion = getStage1Version(activeUrl);
        int bundledVersion = getStage1Version(bundledUrl);

        if (activeVersion >= bundledVersion) {
            logger.debug("Stage1 appears to be up-to-date. Bundled update would be {}, active is {}.",
                bundledVersion, activeVersion);
            return;
        }

        logger.info("Updating stage1 jar from version {} to bundled version {}.",
            activeVersion, bundledVersion);

        Files.deleteIfExists(updatePath);

        Path tmpPath = Files.createTempFile(updatePath.getParent(), "update", ".jar");
        try {
            try (InputStream in = bundledUrl.openStream()) {
                Files.copy(in, tmpPath, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmpPath, updatePath);
        } finally {
            Files.deleteIfExists(tmpPath);
        }
    }

    private static int getStage1Version(URL file) throws IOException {
        try (JarInputStream in = new JarInputStream(file.openStream(), false)) {
            Manifest manifest = in.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            return Integer.parseInt(attributes.getValue("Implementation-Version"));
        }
    }
}
