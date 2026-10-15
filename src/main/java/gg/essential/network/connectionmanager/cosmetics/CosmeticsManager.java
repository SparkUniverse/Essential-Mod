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
package gg.essential.network.connectionmanager.cosmetics;

import com.sparkuniverse.toolbox.util.DateTime;
import gg.essential.Essential;
import gg.essential.config.EssentialConfig;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.connectionmanager.common.packet.checkout.ClientCheckoutCosmeticsPacket;
import gg.essential.connectionmanager.common.packet.cosmetic.*;
import gg.essential.connectionmanager.common.packet.cosmetic.categories.ServerCosmeticCategoriesPopulatePacket;
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket;
import gg.essential.cosmetics.EquippedCosmetic;
import gg.essential.cosmetics.EquippedCosmeticId;
import gg.essential.cosmetics.model.CosmeticUnlockData;
import gg.essential.connectionmanager.common.packet.wardrobe.ClientWardrobeSettingsPacket;
import gg.essential.connectionmanager.common.packet.wardrobe.ServerWardrobeSettingsPacket;
import gg.essential.connectionmanager.common.packet.wardrobe.ServerWardrobeStoreBundlePacket;
import gg.essential.elementa.state.v2.ReferenceHolder;
import gg.essential.event.client.ClientTickEvent;
import gg.essential.event.network.server.ServerJoinEvent;
import gg.essential.event.sps.PlayerJoinSessionEvent;
import gg.essential.gui.elementa.state.v2.MutableState;
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl;
import gg.essential.gui.elementa.state.v2.State;
import gg.essential.gui.elementa.state.v2.StateKt;
import gg.essential.gui.elementa.state.v2.collections.TrackedList;
import gg.essential.gui.notification.Notifications;
import gg.essential.gui.wardrobe.Wardrobe;
import gg.essential.mod.EssentialAsset;
import gg.essential.mod.cosmetics.*;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.NetworkedManager;
import gg.essential.network.connectionmanager.handler.cosmetics.*;
import gg.essential.network.connectionmanager.handler.wardrobe.ServerWardrobeStoreBundlePacketHandler;
import gg.essential.network.connectionmanager.queue.PacketQueue;
import gg.essential.network.connectionmanager.queue.SequentialPacketQueue;
import gg.essential.network.cosmetics.Cosmetic;
import gg.essential.network.cosmetics.cape.CapeCosmeticsManager;
import gg.essential.sps.SpsAddress;
import gg.essential.universal.UMinecraft;
import gg.essential.util.USession;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.collections.MapsKt;
import me.kbrewster.eventbus.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static gg.essential.gui.elementa.state.v2.combinators.StateKt.map;
import static gg.essential.network.connectionmanager.cosmetics.ConnectionManagerKt.*;
import static gg.essential.network.connectionmanager.cosmetics.CosmeticsManagerKt.onNewCosmetic;

public class CosmeticsManager implements NetworkedManager, ICosmeticsManager {

    public static final long LOAD_TIMEOUT_SECONDS = 60;

    @NotNull
    private final ConnectionManager connectionManager;

    @SuppressWarnings("FieldCanBeLocal")
    private final ReferenceHolder refHolder = new ReferenceHolderImpl();

    @NotNull
    private final PacketQueue updateQueue;
    @NotNull
    private final CapeCosmeticsManager capeManager;
    @NotNull
    private final WardrobeSettings wardrobeSettings;
    @NotNull
    private final InfraCosmeticsData infraCosmeticsData;
    @Nullable
    private final LocalCosmeticsData localCosmeticsData;
    @NotNull
    private final CosmeticsData cosmeticsData;
    @Nullable
    private final CosmeticsDataWithChanges cosmeticsDataWithChanges;
    @NotNull
    private final InfraEquippedOutfitsManager infraEquippedOutfitsManager;
    @NotNull
    private final MutableState<Map<String, CosmeticUnlockData>> unlockedCosmeticsData = StateKt.mutableStateOf(new HashMap<>());
    @NotNull
    private final State<Set<String>> unlockedCosmetics = map(unlockedCosmeticsData, Map::keySet);
    @NotNull
    private final AssetLoader assetLoader;
    @NotNull
    private final ModelLoader modelLoader;

    @NotNull
    private MutableState<Boolean> cosmeticsLoaded = StateKt.mutableStateOf(false);
    private boolean receivedUnlockPacket = false;

    // If we've warned the user about cosmetics not working on offline mode servers
    private boolean shownOfflineModeWarning = false;

    public CosmeticsManager(@NotNull final ConnectionManager connectionManager, File baseDir) {
        this.connectionManager = connectionManager;

        this.assetLoader = new AssetLoader(baseDir.toPath().resolve("cosmetic-cache"));
        this.modelLoader = new ModelLoader(assetLoader);

        this.updateQueue = new SequentialPacketQueue.Builder(connectionManager)
                .onTimeoutRetransmit()
                .create();

        this.capeManager = new CapeCosmeticsManager(connectionManager, this);

        this.wardrobeSettings = new WardrobeSettings();

        this.infraCosmeticsData = new InfraCosmeticsData(connectionManager, assetLoader);

        Path repoPath = baseDir.toPath().resolve("configuration");
        if (Files.notExists(repoPath)) {
            repoPath = baseDir.toPath().resolve("cosmetics");
        }
        if (Files.exists(repoPath)) {
            this.localCosmeticsData = new LocalCosmeticsData(repoPath, assetLoader);
            this.cosmeticsDataWithChanges = new CosmeticsDataWithChanges(localCosmeticsData);
            this.cosmeticsData = cosmeticsDataWithChanges;
        } else {
            this.localCosmeticsData = null;
            this.cosmeticsData = this.infraCosmeticsData;
            this.cosmeticsDataWithChanges = null;
        }

        this.infraEquippedOutfitsManager = new InfraEquippedOutfitsManager(
            this.connectionManager,
            this.connectionManager.getSubscriptionManager(),
            (hash) -> {
                this.capeManager.queueCape(hash);
                // If we're still in the Wardrobe, we'll delay flushing until it's closed, so we don't spam Mojang too much
                if (Wardrobe.getInstance() == null) {
                    this.capeManager.flushCapeUpdatesAsync();
                }
                return Unit.INSTANCE;
            },
            this.cosmeticsData,
            this.infraCosmeticsData,
            (enabled) -> {
                EquippedCosmeticsManagerMcKt.setCapeModelPartEnabled(enabled);
                return Unit.INSTANCE;
            }
        );

        onNewCosmetic(this.cosmeticsData, refHolder, cosmetic -> {
            primeCache(modelLoader, assetLoader, cosmetic);

            // If we're side-loading, auto-unlock all cosmetics
            if (localCosmeticsData != null) {
                unlockedCosmeticsData.set(set ->
                    MapsKt.plus(set, new Pair<>(cosmetic.getId(), new CosmeticUnlockData(new DateTime(), null, true)))
                );
            }
        });

        connectionManager.registerPacketHandler(ServerCosmeticsPopulatePacket.class, new ServerCosmeticsPopulatePacketHandler(infraCosmeticsData));
        connectionManager.registerPacketHandler(ServerCosmeticTypesPopulatePacket.class, new ServerCosmeticTypesPopulatePacketHandler(infraCosmeticsData));
        connectionManager.registerPacketHandler(ServerCosmeticCategoriesPopulatePacket.class, new ServerCosmeticCategoriesPopulatePacketHandler(infraCosmeticsData));
        connectionManager.registerPacketHandler(ServerWardrobeStoreBundlePacket.class, new ServerWardrobeStoreBundlePacketHandler(infraCosmeticsData));
        connectionManager.registerPacketHandler(ServerWardrobeSettingsPacket.class, new ServerWardrobeSettingsPacketHandler(infraCosmeticsData, wardrobeSettings));
        connectionManager.registerPacketHandler(ServerCosmeticsUserUnlockedPacket.class, new ServerCosmeticsUserUnlockedPacketHandler(this));
        connectionManager.registerPacketHandler(ServerCosmeticsRevokePurchasePacket.class, new ServerCosmeticsRevokePurchasePacketHandler(this));
        connectionManager.registerPacketHandler(ServerCosmeticAnimationTriggerPacket.class, new ServerCosmeticAnimationTriggerPacketHandler());
        connectionManager.registerPacketHandler(ServerCosmeticsUserEquippedVisibilityPacket.class, new ServerCosmeticsUserEquippedVisibilityPacketHandler());

        Essential.EVENT_BUS.register(this);
    }

    @NotNull
    public AssetLoader getAssetLoader() {
        return this.assetLoader;
    }

    @NotNull
    public ModelLoader getModelLoader() {
        return this.modelLoader;
    }

    @NotNull
    public CapeCosmeticsManager getCapeManager() {
        return this.capeManager;
    }

    @NotNull
    public InfraEquippedOutfitsManager getInfraEquippedOutfitsManager() {
        return this.infraEquippedOutfitsManager;
    }

    @NotNull
    public State<TrackedList<Cosmetic>> getCosmetics() {
        return cosmeticsData.getCosmetics();
    }

    @NotNull
    public State<Set<String>> getUnlockedCosmetics() {
        return this.unlockedCosmetics;
    }

    public State<Map<String, CosmeticUnlockData>> getUnlockedCosmeticsData() {
        return this.unlockedCosmeticsData;
    }

    public void addUnlockedCosmeticsData(@NotNull final Map<String, CosmeticUnlockData> unlockedCosmeticsData) {
        this.infraCosmeticsData.requestCosmeticsIfMissing(unlockedCosmeticsData.keySet());
        this.unlockedCosmeticsData.set(set -> MapsKt.plus(set, unlockedCosmeticsData));
        this.receivedUnlockPacket = true;
    }

    public void unlockAllCosmetics() {
        CosmeticUnlockData unlockData = new CosmeticUnlockData(new DateTime(), null, true);
        this.unlockedCosmeticsData.set(set -> cosmeticsData.getCosmetics().get().stream().map(Cosmetic::getId)
            .collect(Collectors.toMap(k -> k, v -> unlockData)));
    }

    @NotNull
    public Map<CosmeticSlot, EquippedCosmeticId> getEquippedCosmetics() {
        return this.infraEquippedOutfitsManager.getEquippedCosmetics().getCosmetics();
    }

    @NotNull
    public Map<CosmeticSlot, EquippedCosmetic> getVisibleCosmetics(UUID playerId) {
        return this.infraEquippedOutfitsManager.getVisibleCosmeticsState(playerId).getUntracked();
    }

    @Nullable
    public Cosmetic getCosmetic(@NotNull final String cosmeticId) {
        return this.cosmeticsData.getCosmetic(cosmeticId);
    }

    public @NotNull CompletableFuture<byte[]> getAssetBytes(@NotNull EssentialAsset asset, @NotNull AssetLoader.Priority priority) {
        return assetLoader.getAssetBytes(asset, priority);
    }

    @Override
    public void clearUnlockedCosmetics() {
        clearUnlockedCosmetics(false);
    }

    private void clearUnlockedCosmetics(boolean allowAutoUnlockIfSideloading) {
        unlockedCosmeticsData.set(set -> new HashMap<>());

        // If we're side-loading, auto-unlock all cosmetics
        if (allowAutoUnlockIfSideloading && localCosmeticsData != null) {
            unlockAllCosmetics();
        }
    }

    @Override
    public void resetState() {
        this.updateQueue.reset();
        this.clearUnlockedCosmetics(true);
        this.infraCosmeticsData.resetState();
        this.cosmeticsLoaded.set(false);
        this.receivedUnlockPacket = false;
        connectionManager.send(new ClientWardrobeSettingsPacket());
    }

    @Override
    public void onConnected() {
        resetState();
    }

    @Subscribe
    public void onSpsJoin(PlayerJoinSessionEvent event) {
        // Unlock SPS cosmetics if players join an SPS session that the user is hosting
        unlockSpsCosmetics(connectionManager);
    }

    @Subscribe
    public void onWorldJoin(ServerJoinEvent event) {
        if (!connectionManager.isAuthenticated()) return;

        // Show a notification about cosmetics not working on offline-mode servers on the first join of an offline-mode server.
        if (!EssentialConfig.INSTANCE.getDisableCosmetics() && !shownOfflineModeWarning) {
            NetHandlerPlayClient handler = UMinecraft.getMinecraft().getConnection();
            //#if MC >= 26.2
            //$$ if (handler != null && !handler.onlineMode()) {
            //#else
            if (handler != null && !handler.getNetworkManager().isEncrypted() && !Minecraft.getMinecraft().isIntegratedServerRunning()) {
            //#endif
                gg.essential.gui.notification.ExtensionsKt.warning(
                    Notifications.INSTANCE,
                    "Wardrobe items unavailable",
                    "Wardrobe items are not visible on offline-mode servers."
                );

                shownOfflineModeWarning = true;
            }
        }

        // Unlock SPS cosmetics if user is joining, not hosting, an SPS session
        SpsAddress spsAddress = SpsAddress.parse(event.getServerData().serverIP);
        if (spsAddress != null && !spsAddress.getHost().equals(USession.Companion.activeNow().getUuid())) {
            unlockSpsCosmetics(connectionManager);
        }

        // Get any cosmetics that should be unlocked for joining the server
        unlockServerCosmetics(connectionManager, event.getServerData().serverIP);
    }

    public CompletableFuture<Boolean> claimFreeItems(@NotNull Set<String> ids) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        connectionManager.send(new ClientCheckoutCosmeticsPacket(ids, null), it -> {
            if (it.isPresent()) {
                Packet packet = it.get();
                if (packet instanceof ResponseActionPacket) {
                    // ResponseActionPacket(true) means that no error occurred, but no cosmetics were unlocked
                    if (!((ResponseActionPacket) packet).isSuccessful()) {
                        Essential.debug.error("ClientCosmeticCheckoutPacket did give an expected response");
                    }
                } else if (packet instanceof ServerCosmeticsUserUnlockedPacket) {
                    future.complete(true);
                    ServerCosmeticsUserUnlockedPacket unlockedPacket = (ServerCosmeticsUserUnlockedPacket) packet;
                    Essential.debug.debug(String.format("Automatically unlocked %d free cosmetics.", unlockedPacket.getUnlockedCosmetics().size()));
                }
            } else {
                Essential.debug.error("ClientCosmeticCheckoutPacket did not give a response");
            }

            if (!future.isDone()) {
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Returns true if the client has received cosmetics, categories, and types from infra, and is not waiting for any further responses (or requests have timed out).
     */
    private boolean cosmeticDataLoadedFromInfra() {
        long timeout = LOAD_TIMEOUT_SECONDS * 1000;
        return (wardrobeSettings.isSettingsLoaded())
                && receivedUnlockPacket
                && !cosmeticsData.getCosmetics().get().isEmpty()
                && !cosmeticsData.getCategories().get().isEmpty()
                && (cosmeticsData != infraCosmeticsData || !infraCosmeticsData.hasActiveRequests(timeout));
    }

    /**
     * Returns a boolean state which is true when client has received all cosmetic data from infra.
     */
    public @NotNull State<Boolean> getCosmeticsLoaded() {
        return cosmeticsLoaded;
    }

    @Subscribe
    public void tick(ClientTickEvent tickEvent) {
        if (!this.cosmeticsLoaded.getUntracked() && cosmeticDataLoadedFromInfra()) {
            this.cosmeticsLoaded.set(true);
        }
    }

    /**
     * Notify the mod that the contained ids are no longer unlocked by the user. The Connection Manager
     * will send updated variants of the users outfits if one or more contained revoked cosmetics.
     *
     * @param revokedIds IDs of cosmetics no longer unlocked
     */
    public void removeUnlockedCosmetics(List<String> revokedIds) {
        this.unlockedCosmeticsData.set(set -> set.entrySet().stream()
                .filter(e -> !revokedIds.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @NotNull
    public WardrobeSettings getWardrobeSettings() {
        return wardrobeSettings;
    }

    @NotNull
    public CosmeticsData getCosmeticsData() {
        return cosmeticsData;
    }

    @NotNull
    public InfraCosmeticsData getInfraCosmeticsData() {
        return infraCosmeticsData;
    }

    @Nullable
    public LocalCosmeticsData getLocalCosmeticsData() {
        return localCosmeticsData;
    }

    @Nullable
    public CosmeticsDataWithChanges getCosmeticsDataWithChanges() {
        return cosmeticsDataWithChanges;
    }
}
