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
package gg.essential.network.connectionmanager.telemetry;

import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket;
import gg.essential.data.OnboardingData;
import gg.essential.elementa.state.v2.ReferenceHolder;
import gg.essential.event.gui.InitGuiEvent;
import gg.essential.event.network.server.ServerJoinEvent;
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl;
import gg.essential.gui.elementa.state.v2.StateKt;
import gg.essential.lib.gson.Gson;
import gg.essential.lib.gson.JsonElement;
import gg.essential.lib.gson.JsonObject;
import gg.essential.lib.gson.JsonPrimitive;
import gg.essential.network.connectionmanager.NetworkedManager;
import gg.essential.sps.SpsAddress;
import gg.essential.universal.UMinecraft;
import gg.essential.universal.UResolution;
import gg.essential.util.ModLoaderUtil;
import gg.essential.util.Multithreading;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;
import me.kbrewster.eventbus.Subscribe;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.SystemInfo;
//#if MC<11701
import oshi.hardware.Processor;
//#else
//$$ import oshi.hardware.CentralProcessor;
//#endif

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static gg.essential.network.connectionmanager.telemetry.TelemetryManagerExtKt.*;

public class TelemetryGatherer implements NetworkedManager {
    @NotNull
    private final TelemetryManager telemetryManager;
    @NotNull
    private final ReferenceHolder referenceHolder = new ReferenceHolderImpl();
    @Nullable
    private Function0<Unit> modPartnerEffect = null;
    @Nullable
    private Map<String, Object> previousDisplayMetadata = null;

    public TelemetryGatherer(@NotNull final TelemetryManager telemetryManager) {
        this.telemetryManager = telemetryManager;

        Essential.EVENT_BUS.register(this);
    }

    public void enqueue(@NotNull ClientTelemetryPacket packet) {
        telemetryManager.enqueue(packet);
    }

    @Override
    public void onConnected() {
        modPartnerEffect = StateKt.effect(referenceHolder, observer -> {
            List<String> loadedPartnerModIds = ModLoaderUtil.loadedPartnerModIds.get(observer);
            if (loadedPartnerModIds == null) {
                return Unit.INSTANCE;
            }
            enqueue(new ClientTelemetryPacket("PARTNERED_MODS", new HashMap<String, Object>() {{
                put("partnered_mod_ids", loadedPartnerModIds);
            }}));
            return Unit.INSTANCE;
        });
        sendInitialAutoUpdateState();
    }

    @Override
    public void onDisconnect() {
        if (modPartnerEffect != null) {
            modPartnerEffect.invoke();
            modPartnerEffect = null;
        }
    }

    public void hookUp(ReferenceHolder referenceHolder) {
        setupAbFeatureTracking(this, referenceHolder);
        setupSettingsTracking(this, referenceHolder);
        setupAutoUpdateTracking();
        ImpressionTelemetryManager.INSTANCE.initialize();
        FPSTelemetryManager.INSTANCE.initialize();

        enqueue(new ClientTelemetryPacket("LANGUAGE", new HashMap<String, Object>(){{
            put("lang", UMinecraft.getMinecraft().gameSettings.language);
        }}));
        queueInstallerTelemetryPacket();
        queueIntegrationModTelemetryPacket();
        queueHardwareTelemetry();
        queueUpdateDownloadSpeedTelemetry();
    }

    public void sendAutoUpdateTelemetry() {
        enqueue(new ClientTelemetryPacket("AUTO_UPDATE_STATE", new HashMap<String, Object>() {{
            put("state", EssentialConfig.INSTANCE.getAutoUpdate());
        }}));
    }

    @Subscribe
    public void onServerJoin(ServerJoinEvent event) {
        SpsAddress spsAddress = SpsAddress.parse(event.getServerData().serverIP);
        if (spsAddress != null) {
            enqueue(new ClientTelemetryPacket("SPS_JOIN", new HashMap<String, Object>() {{
                put("host", spsAddress.getHost());
            }}));
        }
    }

    private void queueHardwareTelemetry() {

        final Map<String, Object> hardwareMap = new HashMap<>();

        try {
            //#if MC>=11700
            //$$ CentralProcessor centralProcessor = new SystemInfo().getHardware().getProcessor();
            //$$ hardwareMap.put("cpu", centralProcessor.getProcessorIdentifier().getName());
            //#else
            Processor[] processors = new SystemInfo().getHardware().getProcessors();
            if (processors.length > 0) {
                hardwareMap.put("cpu", processors[0].getName());
            }
            //#endif
        } catch (Throwable e) {
            Essential.logger.warn("Failed to get CPU", e);
            hardwareMap.putIfAbsent("cpu", "UNKNOWN");
        }

        hardwareMap.put("allocatedMemory", Runtime.getRuntime().maxMemory() / 1024L / 1024L);

        enqueue(new ClientTelemetryPacket("HARDWARE_V2", hardwareMap));
    }

    private void queueUpdateDownloadSpeedTelemetry() {
        final String bytes = System.getProperty("essential.stage2.downloaded.bytes");
        final String ms = System.getProperty("essential.stage2.downloaded.millis");

        if (StringUtils.isNumeric(bytes) && StringUtils.isNumeric(ms)) {
            try {
                enqueue(new ClientTelemetryPacket(
                    "UPDATE_DOWNLOAD_SPEED",
                    new HashMap<String, Object>() {{
                        put("downloadBytes", Integer.parseInt(bytes));
                        put("downloadMs", Integer.parseInt(ms));
                    }}
                ));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendInitialAutoUpdateState() {
        if (!OnboardingData.hasSentAutoUpdateTelemetryBefore()) {
            sendAutoUpdateTelemetry();
            OnboardingData.setSentAutoUpdateTelemetry();
        }
    }

    private void setupAutoUpdateTracking() {
        StateKt.onChange(EssentialConfig.INSTANCE.getAutoUpdateState(), referenceHolder, (o, b)-> {
            sendAutoUpdateTelemetry();
            return Unit.INSTANCE;
        });
    }

    @Subscribe
    public void sendDisplayData(@NotNull final InitGuiEvent event) {
        final Map<String, Object> displayMetadata = new HashMap<>();
        displayMetadata.put("windowWidth", UResolution.getWindowWidth());
        displayMetadata.put("windowHeight", UResolution.getWindowHeight());
        displayMetadata.put("guiSize", UResolution.getScaleFactor());
        if (!displayMetadata.equals(previousDisplayMetadata)) {
            enqueue(new ClientTelemetryPacket("DISPLAY_DATA", displayMetadata));
            this.previousDisplayMetadata = displayMetadata;
        }
    }

    private void queueInstallerTelemetryPacket() {
        // We go async, since we are reading a file
        Multithreading.runAsync(() -> {
            try {
                Path installerMetadataPath = Essential.getInstance().getBaseDir().toPath().resolve("installer-metadata.json");

                if (Files.notExists(installerMetadataPath))
                    return;

                // Calculate the sha-1 checksum of the current game directory in the same way the installer does.
                byte[] pathBytes = installerMetadataPath.toRealPath().toString().getBytes(StandardCharsets.UTF_8);
                byte[] pathChecksumBytes = MessageDigest.getInstance("SHA-1").digest(pathBytes);
                StringBuilder pathChecksumBuilder = new StringBuilder();
                for (byte checksumByte : pathChecksumBytes) {
                    pathChecksumBuilder.append(String.format(Locale.ROOT, "%02x", checksumByte));
                }
                String pathChecksum = pathChecksumBuilder.toString();

                // Grab the raw JSON data from the telemetry file
                // This is to allow installer to add telemetry fields without having to update the mod
                HashMap<String, Object> telemetryMap = getHashMapFromJsonFile(installerMetadataPath);
                // Check if the game folder has been moved
                boolean hasBeenMoved = false;
                Object installPathChecksum = telemetryMap.get("installPathChecksum");
                if (installPathChecksum instanceof JsonPrimitive) {
                    String installerPathChecksum = ((JsonPrimitive) installPathChecksum).getAsString();
                    hasBeenMoved = !installerPathChecksum.equals(pathChecksum);
                }

                telemetryMap.put("installPathChecksum", pathChecksum);
                telemetryMap.put("hasBeenMoved", hasBeenMoved);
                // Then queue the packet on the main thread again
                Multithreading.scheduleOnMainThread(() -> enqueue(new ClientTelemetryPacket("INSTALLER", telemetryMap)), 0, TimeUnit.SECONDS);

            } catch (Exception e) {
                Essential.logger.warn("Error when trying to parse installer telemetry!", e);
            }
        });
    }

    private void queueIntegrationModTelemetryPacket() {
        // We go async, since we are reading a file
        Multithreading.runAsync(() -> {
            try {
                Path integrationModMetadataPath = Essential.getInstance().getBaseDir().toPath().resolve("partner-integration-mod-metadata.json");

                if (Files.notExists(integrationModMetadataPath))
                    return;

                // Send telemetry file as-is
                HashMap<String, Object> telemetryMap = getHashMapFromJsonFile(integrationModMetadataPath);
                // Then queue the packet on the main thread again
                Multithreading.scheduleOnMainThread(() -> enqueue(new ClientTelemetryPacket("PARTNER_INTEGRATION_MOD", telemetryMap)), 0, TimeUnit.SECONDS);
                // Delete file after enqueueing, to ensure we only send this packet once.
                Files.deleteIfExists(integrationModMetadataPath);
            } catch (Exception e) {
                Essential.logger.warn("Error when trying to parse partner integration mod telemetry!", e);
            }
        });
    }

    private HashMap<String, Object> getHashMapFromJsonFile(Path path) throws IOException {
        String rawFile = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        JsonObject telemetryObject = new Gson().fromJson(rawFile, JsonObject.class);
        // Convert to map
        HashMap<String, Object> telemetryMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : telemetryObject.entrySet()) {
            telemetryMap.put(entry.getKey(), entry.getValue());
        }
        return telemetryMap;
    }

}
