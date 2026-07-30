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
package gg.essential;

import com.google.common.net.InetAddresses;
import gg.essential.api.EssentialAPI;
import gg.essential.api.gui.EssentialComponentFactory;
import gg.essential.commands.EssentialCommandRegistry;
import gg.essential.compatibility.vanilla.difficulty.Net;
import gg.essential.config.AccessedViaReflection;
import gg.essential.config.EssentialConfig;
import gg.essential.config.EssentialConfigApiImpl;
import gg.essential.config.McEssentialConfig;
import gg.essential.cosmetics.IngameEquippedOutfitsUpdateDispatcher;
import gg.essential.cosmetics.PlayerWearableManager;
import gg.essential.cosmetics.events.CosmeticEventEmitter;
import gg.essential.data.OnboardingData;
import gg.essential.elementa.components.image.FileImageCache;
import gg.essential.elementa.components.image.ImageCache;
import gg.essential.elementa.effects.StencilEffect;
import gg.essential.elementa.font.ElementaFonts;
import gg.essential.event.EventHandler;
import gg.essential.event.client.InitializationEvent;
import gg.essential.event.client.PostInitializationEvent;
import gg.essential.event.client.PreInitializationEvent;
import gg.essential.event.client.ReAuthEvent;
import gg.essential.event.gui.GuiDrawScreenEvent;
import gg.essential.event.render.RenderTickEvent;
import gg.essential.forge.EssentialForgeMod;
import gg.essential.gui.EssentialPalette;
import gg.essential.gui.account.factory.*;
import gg.essential.gui.api.ComponentFactory;
import gg.essential.gui.common.UI3DPlayer;
import gg.essential.gui.elementa.state.v2.MutableState;
import gg.essential.gui.elementa.state.v2.StateScheduler;
import gg.essential.gui.image.ResourceImageFactory;
import gg.essential.gui.notification.Notifications;
import gg.essential.handlers.OptionsScreenOverlay;
import gg.essential.gui.overlay.OverlayManager;
import gg.essential.gui.overlay.OverlayManagerImpl;
import gg.essential.handlers.*;
import gg.essential.handlers.discord.DiscordIntegration;
import gg.essential.key.EssentialKeybindingRegistry;
import gg.essential.lib.gson.Gson;
import gg.essential.lib.gson.GsonBuilder;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.skins.PlayerSkinLookup;
import gg.essential.network.connectionmanager.telemetry.FeatureSessionTelemetry;
import gg.essential.network.mojang.ManagedMojangProfileApi;
import gg.essential.sps.McIntegratedServerManager;
import gg.essential.sps.WindowTitleManager;
import gg.essential.universal.UMinecraft;
import gg.essential.util.*;
import gg.essential.util.crash.StacktraceDeobfuscator;
import gg.essential.util.lwjgl3.Lwjgl3Loader;
import gg.essential.util.swing.SwingUtil;
import me.kbrewster.eventbus.Subscribe;
import me.kbrewster.eventbus.invokers.InvokerType;
import me.kbrewster.eventbus.invokers.LMFInvoker;
import me.kbrewster.eventbus.invokers.ReflectionInvoker;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//#if MC == 26.2
//$$ import org.lwjgl.opengl.ARBClipControl;
//$$ import org.lwjgl.opengl.GL;
//#endif

//#if MC>=11400
//#else
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
//#endif

import static gg.essential.gui.elementa.state.v2.StateKt.mutableStateOf;

public class Essential implements EssentialAPI {
    public static final String MODID = "essential";
    public static final String NAME = "Essential";
    public static final String VERSION = "1.0.0";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Logger logger = LogManager.getLogger("Essential Logger");
    public static final Logger debug = LogManager.getLogger("Essential Logger - Debug");
    private static final InvokerType invoker = determineBestInvokerType();
    public static final EventBus EVENT_BUS = new EventBus(invoker, e -> logger.error("Error occurred in method: {}", e.getMessage(), e));
    private static Essential instance;
    private static boolean initialized = false;
    private static boolean getInstanceIsLocked = false;

    static {
        if (MinecraftUtils.INSTANCE.isDevelopment()) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration conf = ctx.getConfiguration();
            conf.getLoggerConfig("Essential Logger - Debug").setLevel(Level.ALL);
            ctx.updateLoggers(conf);
        }

        dispatchIndependentStaticInitializers();
    }

    private final File baseDir = createEssentialDir();
    public final boolean isNewInstallation = !new File(baseDir, "config.toml").exists();

    private final Lwjgl3Loader lwjgl3 = new Lwjgl3Loader(baseDir.toPath().resolve("lwjgl3-natives"), GLUtil.INSTANCE.isGL30());
    private final MutableState<@Nullable McIntegratedServerManager> integratedServerManager = mutableStateOf(null);
    @NotNull
    private final ConnectionManager connectionManager = new ConnectionManager(new NetworkHook(), baseDir, lwjgl3, integratedServerManager);
    private final List<SessionFactory> sessionFactories = new ArrayList<>();
    private ImageCache imageCache;

    private PlayerWearableManager playerWearableManager;
    private final MojangSkinManager skinManager = new McMojangSkinManager();
    private CosmeticEventEmitter cosmeticEventEmitter;
    private Map<Object, Boolean> dynamicListeners = new HashMap<>();
    private EssentialGameRules gameRules;

    public static Essential getInstance() {
        if (instance != null) {
            return instance;
        }

        synchronized (Essential.class) {
            if (instance != null) {
                return instance;
            }

            // Sometimes, `getInstance()` may be called before the previous call has completed. For example, where a class
            // which is initialized in `Essential#<init>` uses `Essential.getInstance()` in its constructor.
            // This can cause issues with classes that are sensitive in their construction (e.g. LWJGL3Loader)
            if (getInstanceIsLocked) {
                throw new RuntimeException("A class is attempting to call `Essential.getInstance()` during a call to `Essential#<init>`. See the stacktrace for the culprit.");
            }

            getInstanceIsLocked = true;
            instance = new Essential();
            getInstanceIsLocked = false;

            return instance;
        }
    }

    @NotNull
    public MutableState<@Nullable McIntegratedServerManager> getIntegratedServerManager() {
        return this.integratedServerManager;
    }

    @NotNull
    public ConnectionManager getConnectionManager() {
        return this.connectionManager;
    }

    @NotNull
    public EssentialKeybindingRegistry getKeybindingRegistry() {
        return EssentialKeybindingRegistry.getInstance();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Subscribe
    public void initialize(InitializationEvent event) {
        if (initialized) return;
        initialized = true;
        init();
    }

    @Subscribe
    public void preinit(PreInitializationEvent event) {
        DI.INSTANCE.startDI();

        EssentialConfig config = EssentialConfig.INSTANCE;
        config.initialize(new File(baseDir, "config.toml"));

        loadSessionFactories();
        this.connectionManager.start();

        PlayerSkinLookup.INSTANCE.supplySkinFromGame(USession.Companion.activeNow().getUuid(), SkinKt.getSkinFromMinecraft());

        dispatchStaticInitializers();

    }

    @SuppressWarnings({
        "Convert2MethodRef", // that would initialize them on the main thread
        "ResultOfMethodCallIgnored" // we want the static initializer to run, don't care about the result
    })
    private void dispatchStaticInitializers() {
        Multithreading.runAsync(() -> DiscordIntegration.INSTANCE.getClass());
        Multithreading.runAsync(() -> ElementaFonts.INSTANCE.getClass());
        Multithreading.runAsync(() -> EssentialAPI.Companion.getClass());
        Multithreading.runAsync(() -> AutoUpdate.INSTANCE.getClass());
        Multithreading.runAsync(() -> {
            EssentialPalette.INSTANCE.getClass();
            ResourceImageFactory.Companion.preload();
        });
    }

    @SuppressWarnings({
        "Convert2MethodRef", // that would initialize them on the main thread
        "ResultOfMethodCallIgnored" // we want the static initializer to run, don't care about the result
    })
    private static void dispatchIndependentStaticInitializers() {
        Multithreading.runAsync(() -> EssentialConfig.INSTANCE.getClass());
    }

    @Subscribe
    public void postInit(PostInitializationEvent event) {
        gameRules = new EssentialGameRules();

        // Workaround for Iris 1.11.1 on 26.2 not properly setting clip control on boot when a shader pack is active
        EVENT_BUS.register(new Object() {
            @Subscribe
            public void handleDraw(GuiDrawScreenEvent.Priority event) {
                //#if MC == 26.3
                //$$ TODO check if this is still needed
                //#else
                //#if MC == 26.2
                //$$ if (ModLoaderUtil.INSTANCE.isModLoaded("iris") && !GuiEssentialPlatform.Companion.getPlatform().isZZeroToOne() && GL.getCapabilities().GL_ARB_clip_control) {
                //$$     ARBClipControl.glClipControl(ARBClipControl.GL_LOWER_LEFT, ARBClipControl.GL_NEGATIVE_ONE_TO_ONE);
                //$$ }
                //#endif
                //#endif
                EVENT_BUS.unregister(this);
            }
        });
    }

    public void registerListener(Object o) {
        EVENT_BUS.register(o);
    }

    public void registerListenerRequiresEssential(Object o) {
        if (EssentialConfig.INSTANCE.getEssentialEnabled()) {
            EVENT_BUS.register(o);
            dynamicListeners.put(o, true);
        } else {
            dynamicListeners.put(o, false);
        }
    }

    public void checkListeners() {
        for (Map.Entry<Object, Boolean> entry : dynamicListeners.entrySet()) {
            if (!EssentialConfig.INSTANCE.getEssentialEnabled()) {
                if (entry.getValue()) {
                    EVENT_BUS.unregister(entry.getKey());
                    entry.setValue(false);
                }
            } else {
                if (!entry.getValue()) {
                    EVENT_BUS.register(entry.getKey());
                    entry.setValue(true);
                }
            }
        }
    }

    public MojangSkinManager getSkinManager() {
        return skinManager;
    }

    private void init() {
        EssentialConfig essentialConfig = EssentialConfig.INSTANCE;
        try {
            if (Sk1erModUtils.isOldModCorePresent() && essentialConfig.getModCoreWarning()) {
                logger.error("Old ModCore has been found!! Uh oh!");
                SwingUtil.showOldModCorePopup();
            }
        } catch (Exception ignored) {
            // it's *probably* fine, so we can keep going.
        }

        EventHandler.init();
        StencilEffect.Companion.enableStencil();
        McEssentialConfig.INSTANCE.hookUp();
        //#if MC<11400
        createStacktraceDeobfuscator();
        //#endif

        imageCache = new FileImageCache(new File(getBaseDir(), "image-cache"), 1, TimeUnit.HOURS, true);
        PlayerSkinLookup.INSTANCE.loadCache(getBaseDir().toPath().resolve("cache"));

        EVENT_BUS.register(EssentialCommandRegistry.INSTANCE);
        getKeybindingRegistry().refreshBinds(); // config is ready now, time to refresh which bindings we actually want
        registerListener(getKeybindingRegistry());
        registerListenerRequiresEssential(new NetworkSubscriptionStateHandler());
        registerListener(MinecraftUtils.INSTANCE);
        registerListenerRequiresEssential(new ServerStatusHandler());
        registerListener(GuiUtil.INSTANCE);
        //#if MC>=12106
        //$$ registerListener(AdvancedDrawContext.INSTANCE);
        //#endif
        registerListener(new PauseMenuDisplay());
        registerListenerRequiresEssential(DiscordIntegration.INSTANCE);
        registerListener(new OptionsScreenOverlay());
        registerListener(connectionManager);
        registerListener(new WindowedFullscreenHandler());
        registerListener(connectionManager.getSpsManager());
        registerListener(connectionManager.getSocialManager());
        registerListenerRequiresEssential(cosmeticEventEmitter = new CosmeticEventEmitter());
        registerListener(playerWearableManager = new PlayerWearableManager(connectionManager, connectionManager.getCosmeticsManager()));
        WikiToastListener.INSTANCE.register();
        if (!OptiFineUtil.isLoaded()) {
            registerListenerRequiresEssential(ZoomHandler.getInstance());
        }
        registerListener(new IngameEquippedOutfitsUpdateDispatcher(
            connectionManager.getSubscriptionManager().getSubscriptionsAndSelf(),
            connectionManager.getCosmeticsManager().getInfraEquippedOutfitsManager()
        ));

        Net.INSTANCE.init();
        Multithreading.runAsync(() -> {
            try {
                EssentialContainerUtil.updateStage1IfOutdated(UMinecraft.getMinecraft().mcDataDir.toPath());
            } catch (Exception e) {
                logger.error("Failed to update loader stage1! Auto-update may not behave as expected!", e);
            }
        });

        registerListener(Notifications.INSTANCE);
        registerListener(new ReAuthChecker());
        registerListener(UI3DPlayer.Companion);
        WindowTitleManager.INSTANCE.register();

        //#if MC<11400
        // Patcher screenshot manager conflicts with ours, so we disable it
        ModContainer patcher = Loader.instance().getIndexedModList().get("patcher");
        if (patcher != null) {
            try {
                Version version = new Version(patcher.getVersion());
                if (version.compareTo(new Version("1.8.2")) < 1) { // if the version is less than or equal to 1.8.2
                    Class<?> patcherConfig = Class.forName("club.sk1er.patcher.config.PatcherConfig");
                    Field screenshotManager = patcherConfig.getDeclaredField("screenshotManager");
                    screenshotManager.setBoolean(null, false);
                }
            } catch (Exception e) {
                logger.error("Failed to disable Patcher screenshot manager", e);
            }
        }
        //#endif

        // Workaround for https://github.com/McModLauncher/securejarhandler/issues/37
        // For the specific case where MC interrupts its lan server broadcast listener thread after it found its first
        // broadcast (net.minecraft.client.server.LanServerDetection.LanServerList.addServer).
        try {
            // This call using InetAddresses was added by a Forge patch in a later 1.18.2 Forge version.
            // https://github.com/MinecraftForge/MinecraftForge/blob/be584c54aa72ba091e0414ac564598328bd9f407/patches/minecraft/net/minecraft/client/server/LanServerDetection.java.patch
            //noinspection UnstableApiUsage
            InetAddresses.toAddrString(InetAddress.getByAddress(new byte[16]));
            // These are vanilla
            //#if MC>=11600
            //$$ net.minecraft.client.multiplayer.LanServerPingThread.class.getName();
            //$$ net.minecraft.client.network.LanServerInfo.class.getName();
            //#endif
        } catch (Throwable e) {
            e.printStackTrace();
        }

        EssentialChannelHandler.registerEssentialChannel();

        // NeoForge enforces use of their event as of 1.21.4 (specifically 21.4.84-beta), so we need to use it when it's
        // available
        // See https://github.com/neoforged/NeoForge/pull/1915
        if (EssentialForgeMod.USE_NEW_NEOFORGE_RESOURCE_EVENT) {
            // See EssentialForgeMod.<init>
            // Can't be here because it's too late.
            // Can't be in our pre-init because it's too early (on some NeoForge versions).
        } else {
            ((SimpleReloadableResourceManager) UMinecraft.getMinecraft().getResourceManager())
                .registerReloadListener(ResourceManagerUtil.INSTANCE);
        }

        // Fetch update changelog now so it is preloaded for later use
        AutoUpdate.INSTANCE.getChangelog();

        FeatureSessionTelemetry.INSTANCE.start();
    }

    private File createEssentialDir() {
        final File baseDir = new File(UMinecraft.getMinecraft().mcDataDir, "essential");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }

    private void loadSessionFactories() {
        try {
            // In order of preference (earlier takes priority)
            Path savePath = MagicPathsKt.getGlobalEssentialDirectory().resolve("microsoft_accounts.json");
            Path oldSavePath = baseDir.toPath().resolve("microsoft_accounts.json");
            final MicrosoftAccountSessionFactory microsoftAccountSessionFactory = new MicrosoftAccountSessionFactory(savePath, oldSavePath);
            Multithreading.runAsync(microsoftAccountSessionFactory::refreshRefreshTokensIfNecessary);
            sessionFactories.add(microsoftAccountSessionFactory);
            // Official launcher factories are disabled for now because apparently having accounts listed which you
            // cannot use without log in is too confusing.
            //   sessionFactories.add(new OfficialLauncherSessionFactory(UMinecraft.getMinecraft().mcDataDir.toPath().resolve("launcher_accounts.json")));
            //   sessionFactories.add(new OfficialLauncherSessionFactory(ExtensionsKt.getMinecraftDirectory().toPath().resolve("launcher_accounts.json")));
            // The active session should only be used as a fallback, so it is last. Ideally we will already find it in
            // either our managed session factories or via the official launcher (counterexample would be e.g. people
            // using a third-party launcher).
            sessionFactories.add(new ActiveSessionFactory());
            sessionFactories.add(new InitialSessionFactory());
        } catch (Exception e) {
            logger.error("Failed to load accounts:", e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createStacktraceDeobfuscator() {
        Multithreading.runAsync(() -> {
            File mappingsFolder = new File(baseDir, "mappings");
            if (!mappingsFolder.exists()) mappingsFolder.mkdir();

            File mappings = new File(mappingsFolder, "mappings-" + UMinecraft.getMinecraft().getVersion() + ".csv");
            logger.info((mappings.exists() ? "Found MCP method mappings: " : "Downloading MCP method mappings to: ") + mappings.getName());
            StacktraceDeobfuscator.setup(mappings);
        });


    }

    private static InvokerType determineBestInvokerType() {
        if (System.getProperty("java.vm.name", "").contains("OpenJ9")) {
            // LMFInvoker doesn't currently support OpenJ9, so we won't bother trying.
            return new ReflectionInvoker();
        }

        class Dummy {
            @AccessedViaReflection("Essential.determineBestInvokerType")
            public void dummy(Object obj) {}
        }
        try {
            InvokerType lmfInvoker = new LMFInvoker();
            lmfInvoker.setup(new Dummy(), Dummy.class, Object.class, Dummy.class.getMethod("dummy", Object.class));
            return lmfInvoker;
        } catch (Throwable e) {
            logger.error("Could not set up LMFInvoker: ", e);
            return new ReflectionInvoker();
        }
    }

    @Subscribe
    public void preRenderTick(RenderTickEvent event) {
        if (!event.isPre()) return;

        StateScheduler.updateSystemTime(Instant.now());
    }

    @Subscribe(priority = 2000) // before any other event which might try to query the api
    public void invalidateCacheOnReAuth(ReAuthEvent event) {
        ManagedMojangProfileApi.Companion
            .forUser(event.getSession().getUuid())
            .invalidateProfile();
    }

    /**
     * Called when ESSENTIAL_DEBUG_KEY is pressed.
     *
     * @see EssentialKeybindingRegistry
     */
    public void debugKeyFunction() {
        // gg.essential.gui.notification.ExampleKt.sendTestNotifications();
    }

    public List<SessionFactory> getSessionFactories() {
        return sessionFactories;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public Lwjgl3Loader getLwjgl3() {
        return lwjgl3;
    }

    @NotNull
    @Override
    public gg.essential.api.commands.CommandRegistry commandRegistry() {
        return EssentialCommandRegistry.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.DI di() {
        return DI.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.gui.Notifications notifications() {
        return Notifications.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.config.EssentialConfig config() {
        return EssentialConfigApiImpl.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.utils.GuiUtil guiUtil() {
        return GuiUtil.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.utils.MinecraftUtils minecraftUtil() {
        return MinecraftUtils.INSTANCE;
    }


    @NotNull
    @Override
    public gg.essential.api.utils.ShutdownHookUtil shutdownHookUtil() {
        return ShutdownHook.INSTANCE;
    }

    @NotNull
    @Override
    public ImageCache imageCache() {
        return imageCache;
    }

    @NotNull
    @Override
    public gg.essential.api.utils.TrustedHostsUtil trustedHostsUtil() {
        return TrustedHostsUtil.INSTANCE;
    }

    @NotNull
    @Override
    public EssentialComponentFactory componentFactory() {
        return ComponentFactory.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.utils.mojang.MojangAPI mojangAPI() {
        return MojangAPI.INSTANCE;
    }

    @NotNull
    @Override
    public gg.essential.api.data.OnboardingData onboardingData() {
        return OnboardingData.INSTANCE;
    }

    public CosmeticEventEmitter getCosmeticEventEmitter() {
        return cosmeticEventEmitter;
    }

    public OverlayManager getOverlayManager() {
        return OverlayManagerImpl.INSTANCE;
    }

    @Nullable
    public EssentialGameRules getGameRules() {
        return gameRules;
    }
}
