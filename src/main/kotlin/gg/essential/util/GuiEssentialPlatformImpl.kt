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
package gg.essential.util

import com.google.common.collect.ImmutableMap
import com.google.common.net.HostAndPort
import com.google.common.net.InetAddresses
import com.mojang.authlib.GameProfile
import gg.essential.Essential
import gg.essential.api.profile.wrapped
import gg.essential.config.AccessedViaReflection
import gg.essential.config.EssentialConfig
import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.cosmetics.EquippedCosmetic
import gg.essential.elementa.components.Window
import gg.essential.event.client.ReAuthEvent
import gg.essential.gui.common.EmulatedUI3DPlayer
import gg.essential.gui.common.UIPlayer
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.elementa.essentialmarkdown.EssentialMarkdown
import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.elementa.state.v2.toV1
import gg.essential.gui.friends.SocialMenu
import gg.essential.gui.friends.message.v2.MessageRef
import gg.essential.gui.friends.state.IMessengerStates
import gg.essential.gui.friends.state.IRelationshipStates
import gg.essential.gui.friends.state.IStatusStates
import gg.essential.gui.friends.state.MessengerStateManagerImpl
import gg.essential.gui.friends.state.RelationshipStateManagerImpl
import gg.essential.gui.friends.state.SocialStates
import gg.essential.gui.friends.state.StatusStateManagerImpl
import gg.essential.gui.modals.McModalPrerequisites
import gg.essential.gui.notification.NotificationsImpl
import gg.essential.gui.notification.NotificationsManager
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.overlay.ModalManagerImpl
import gg.essential.gui.overlay.OverlayManager
import gg.essential.gui.overlay.OverlayManagerImpl
import gg.essential.gui.screenshot.bytebuf.LimitedAllocator
import gg.essential.gui.screenshot.components.ScreenshotBrowser
import gg.essential.gui.screenshot.providers.MinecraftWindowedTextureProvider
import gg.essential.gui.screenshot.providers.WindowedImageProvider
import gg.essential.gui.screenshot.providers.WindowedTextureProvider
import gg.essential.gui.wardrobe.ItemId
import gg.essential.gui.wardrobe.Wardrobe
import gg.essential.gui.wardrobe.WardrobeCategory
import gg.essential.handlers.EssentialSoundManager
import gg.essential.handlers.GameProfileManager
import gg.essential.handlers.MojangSkinManager
import gg.essential.handlers.account.WebAccountManager
import gg.essential.handlers.discord.DiscordIntegration
import gg.essential.key.EssentialKeybindingRegistry
import gg.essential.mod.Skin
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.preview.PerspectiveCamera
import gg.essential.model.backend.RenderBackend
import gg.essential.model.backend.minecraft.MinecraftRenderBackend
import gg.essential.model.util.Color
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.cosmetics.AssetLoader
import gg.essential.network.connectionmanager.cosmetics.ICosmeticsManager
import gg.essential.network.connectionmanager.cosmetics.ModelLoader
import gg.essential.network.connectionmanager.cosmetics.WardrobeSettings
import gg.essential.network.connectionmanager.features.DisabledFeaturesManager
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.network.connectionmanager.notices.INoticesManager
import gg.essential.network.connectionmanager.skins.SkinsManager
import gg.essential.network.connectionmanager.social.ProfileSuspension
import gg.essential.network.connectionmanager.social.RulesManager
import gg.essential.network.connectionmanager.suspension.SuspensionManager
import gg.essential.sps.SpsAddress
import gg.essential.universal.UGraphics
import gg.essential.universal.UImage
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UMinecraft
import gg.essential.universal.UResolution
import gg.essential.universal.UScreen
import gg.essential.universal.USound
import gg.essential.universal.render.UGpuFormat
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.utils.ReleasedDynamicTexture
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.universal.vertex.UVertexConsumer
import gg.essential.util.image.GpuTexture
import gg.essential.util.image.bitmap.Bitmap
import gg.essential.util.image.bitmap.MutableBitmap
import gg.essential.util.image.bitmap.forEachPixel
import gg.essential.util.lwjgl3.Lwjgl3Loader
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.CoroutineDispatcher
import me.kbrewster.eventbus.Subscribe
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.vertex.VertexFormat
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.nio.file.Path
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.isRegularFile
import kotlin.jvm.Throws

//#if MC >= 26.2
//$$ import com.mojang.blaze3d.GpuFormat
//#else
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.renderer.vertex.VertexFormatElement
//#endif

//#if MC>=12105
//$$ import net.minecraft.client.texture.GlTexture
//$$ import com.mojang.blaze3d.opengl.GlStateManager
//#endif

//#if MC == 1.21.5
//$$ import gg.essential.mixins.impl.client.MinecraftExt
//$$ import net.minecraft.client.gl.Framebuffer;
//#endif

//#if MC >= 1.21.2
//$$ import com.mojang.blaze3d.systems.ProjectionType
//#elseif MC >= 1.20
//$$ import com.mojang.blaze3d.systems.VertexSorter
//#endif

//#if MC>=11200
import net.minecraft.init.SoundEvents
//#else
//$$ import net.minecraft.util.ResourceLocation
//#endif

//#if MC >= 1.17
//$$ import net.minecraft.util.math.Matrix4f
//#else
import org.lwjgl.opengl.GL11
//#endif

@AccessedViaReflection("GuiEssentialPlatform")
class GuiEssentialPlatformImpl : GuiEssentialPlatform {
    override val mcVersion: Int
        get() = BuildInfo.TARGET_MC_VERSION

    override val clientThreadDispatcher: CoroutineDispatcher
        get() = MinecraftCoroutineDispatchers.clientThread

    override val renderBackend: RenderBackend
        get() = MinecraftRenderBackend

    override val overlayManager: OverlayManager
        get() = OverlayManagerImpl

    override val assetLoader: AssetLoader
        get() = Essential.getInstance().connectionManager.cosmeticsManager.assetLoader

    override val modelLoader: ModelLoader
        get() = Essential.getInstance().connectionManager.cosmeticsManager.modelLoader

    override val lwjgl3: Lwjgl3Loader
        get() = Essential.getInstance().lwjgl3

    override val cmConnection: CMConnection
        get() = Essential.getInstance().connectionManager

    override val notifications: NotificationsManager
        get() = NotificationsImpl

    override fun createModalManager(): ModalManager {
        return ModalManagerImpl(OverlayManagerImpl)
    }

    override fun onResourceManagerReload(runnable: Runnable) {
        ResourceManagerUtil.onResourceManagerReload { runnable.run() }
    }

    @Throws(IOException::class)
    override fun bitmapFromMinecraftResource(identifier: UIdentifier): MutableBitmap? {
        val resource = ResourceManagerUtil.getResource(identifier.toMC()) ?: return null
        resource.inputStream.use {
            return bitmapFromInputStream(it)
        }
    }

    @Throws(IOException::class)
    override fun bitmapFromInputStream(inputStream: InputStream): MutableBitmap {
        val image = UImage.read(inputStream)
        val bitmap = Bitmap.ofSize(image.getWidth(), image.getHeight())
        bitmap.forEachPixel { _, x, y ->
            bitmap[x, y] = Color(image.getPixelRGBA(x, y).toUInt())
        }
        //#if MC>=11600
        //$$ image.nativeImage.close()
        //#endif
        return bitmap
    }

    override fun uImageIntoReleasedDynamicTexture(uImage: UImage): ReleasedDynamicTexture {
        return ReleasedDynamicTexture(uImage.nativeImage)
    }

    override fun identifierFromTexture(texture: RenderBackend.Texture): UIdentifier {
        return (texture as MinecraftRenderBackend.MinecraftTexture).identifier.toU()
    }

    override fun getGlId(identifier: UIdentifier): Int {
        val textureManager = Minecraft.getMinecraft().textureManager
        //#if MC<16000
        @Suppress("USELESS_ELVIS") // method inappropriately marked as non-null by Forge
        //#endif
        val texture = textureManager.getTexture(identifier.toMC())
            //#if MC<11700
            ?: net.minecraft.client.renderer.texture.SimpleTexture(identifier.toMC()).also {
                textureManager.loadTexture(identifier.toMC(), it)
            }
            //#endif
        //#if MC>=12105
        //$$ return (texture.glTexture as GlTexture).glId
        //#else
        return texture.glTextureId
        //#endif
    }

    override fun playSound(identifier: UIdentifier) {
        EssentialSoundManager.playSound(identifier.toMC())
    }

    override fun playNoteHatSound(volume: Float, pitch: Float) {
        //#if MC>=11200
        USound.playSoundStatic(SoundEvents.BLOCK_NOTE_HAT, .25f, 0.75f)
        //#else
        //$$ USound.playSoundStatic(ResourceLocation("note.hat"), .25f, 0.75f)
        //#endif
    }

    override fun registerCosmeticTexture(name: String, texture: ReleasedDynamicTexture): UIdentifier {
        return MinecraftRenderBackend.CosmeticTexture(name, texture).identifier.toU()
    }

    override fun dismissModalOnScreenChange(modal: Modal, dismiss: () -> Unit) {
        var screen = UScreen.currentScreen
        modal.addUpdateFunc { _, _ ->
            val newScreen = UScreen.currentScreen
            if (newScreen == null || newScreen == screen || newScreen is OverlayManagerImpl.OverlayInteractionScreen)
                return@addUpdateFunc

            screen = UScreen.currentScreen
            Window.enqueueRenderOperation {
                dismiss()
            }
        }
    }

    override fun <T> registerEventBusListener(cls: Class<T>, listener: (T) -> Unit, priority: Int) {
        Essential.EVENT_BUS.register(cls, listener, priority)
    }

    override fun <T> unregisterEventBusListener(cls: Class<T>, listener: (T) -> Unit) {
        Essential.EVENT_BUS.unregister(cls, listener)
    }

    override val essentialBaseDir: Path
        get() = Essential.getInstance().baseDir.toPath()

    override val mcBaseDir: Path
        get() = UMinecraft.getMinecraft().mcDataDir.toPath()

    override val config: GuiEssentialPlatform.Config
        get() = EssentialConfig

    override val mcProtocolVersion: Int
        get() = MinecraftUtils.currentProtocolVersion

    override val mcGameVersion: String
        //#if MC>=11600
        //$$ get() = net.minecraft.util.SharedConstants.getVersion().name
        //#elseif MC>=11200
        get() = "1.12.2"
        //#else
        //$$ get() = "1.8.9"
        //#endif

    override val localModList: Map<String, String>
        get() = ModLoaderUtil.getMods().associate { it.name to it.version }

    override fun currentServerType(): ServerType? {
        val minecraft = Minecraft.getMinecraft()

        val spsManager = Essential.getInstance().connectionManager.spsManager
        val localSpsSession = spsManager.localSession
        if (localSpsSession != null) {
            return ServerType.SPS.Host(localSpsSession.hostUUID)
        }

        if (minecraft.isSingleplayer) {
            return ServerType.Singleplayer
        }

        //#if MC<12002
        if (minecraft.isConnectedToRealms) {
            return ServerType.Realms
        }
        //#endif

        val serverData = minecraft.currentServerData ?: return null

        //#if MC>=12002
        //$$ if (serverData.isRealm) {
        //$$     return ServerType.Realms
        //$$ }
        //#endif

        val remoteSpsHost = SpsAddress.parse(serverData.serverIP)?.host
        if (remoteSpsHost != null) {
            return ServerType.SPS.Guest(remoteSpsHost)
        }

        return ServerType.Multiplayer(serverData.serverName, serverData.serverIP)
    }

    override fun registerActiveSessionState(state: MutableState<USession>) {
        state.set(Minecraft.getMinecraft().session.toUSession())
        Essential.EVENT_BUS.register(object {
            @Subscribe(priority = 1000)
            private fun onReAuth(event: ReAuthEvent) {
                state.set(event.session)
            }
        })
    }

    override fun createSocialStates(): SocialStates {
        val cm = Essential.getInstance().connectionManager
        return object : SocialStates {
            override val relationships: IRelationshipStates by lazy { RelationshipStateManagerImpl(cm.relationshipManager) }
            override val messages: IMessengerStates by lazy { MessengerStateManagerImpl(cm.chatManager) }
            override val activity: IStatusStates by lazy { StatusStateManagerImpl(cm.profileManager, cm.spsManager) }
            override val suspensions: ListState<ProfileSuspension>
                get() = cm.profileManager.suspensions
        }
    }

    override fun resolveMessageRef(messageRef: MessageRef) {
        Essential.getInstance().connectionManager.chatManager.retrieveChannelHistoryUntil(messageRef)
    }

    override fun haveActiveRemoteSpsSession(host: UUID): Boolean {
        return Essential.getInstance().connectionManager.spsManager.getRemoteSession(host) != null
    }

    override val essentialUriListener: EssentialMarkdown.(EssentialMarkdown.LinkClickEvent) -> Unit
        get() = gg.essential.util.essentialUriListener

    override val noticesManager: INoticesManager
        get() = Essential.getInstance().connectionManager.noticesManager

    override val autoUpdateManager: AutoUpdateManager
        get() = AutoUpdate

    override val skinsManager: SkinsManager
        get() = Essential.getInstance().connectionManager.skinsManager

    override val cosmeticsManager: ICosmeticsManager
        get() = Essential.getInstance().connectionManager.cosmeticsManager

    override val wardrobeSettings: WardrobeSettings
        get() = Essential.getInstance().connectionManager.cosmeticsManager.wardrobeSettings

    override val mojangSkinManager: MojangSkinManager
        get() = Essential.getInstance().skinManager

    override val screenshotManager: IScreenshotManager
        get() = Essential.getInstance().connectionManager.screenshotManager

    override val disabledFeaturesManager: DisabledFeaturesManager
        get() = Essential.getInstance().connectionManager.disabledFeaturesManager

    override val screenshotFolder: Path
        get() = gg.essential.util.screenshotFolder.toPath()

    override val isOptiFineInstalled: Boolean
        get() = OptiFineUtil.isLoaded()

    override val trustedHosts: Set<String>
        get() = TrustedHostsUtil.getTrustedHosts().flatMapTo(mutableSetOf()) { it.domains }

    override val reportReasons: Map<String, String>
        get() = Essential.getInstance().connectionManager.chatManager.getReportReasons(UMinecraft.getSettings().language)

    override fun fileReport(modalManager: ModalManager, channelId: Long, messageId: Long, sender: UUID, reason: String) {
        Essential.getInstance().connectionManager.chatManager.fileReport(modalManager, channelId, messageId, sender, reason)
    }

    override fun enqueueTelemetry(packet: ClientTelemetryPacket) {
        Essential.getInstance().connectionManager.telemetryManager.enqueue(packet)
    }

    override fun findCodeSource(javaClass: Class<*>): CodeSource? =
        gg.essential.util.findCodeSource(javaClass)

    override fun trackByteBuf(alloc: LimitedAllocator, buf: ByteBuf): ByteBuf =
        gg.essential.gui.screenshot.bytebuf.trackByteBuf(alloc, buf)

    override fun newGlFrameBuffer(width: Int, height: Int, colorFormat: GpuTexture.Format, depthFormat: GpuTexture.Format): GlFrameBuffer =
        GlFrameBufferImpl(width, height, colorFormat, depthFormat)

    override fun newGpuTexture(width: Int, height: Int, format: GpuTexture.Format): GpuTexture =
        OwnedGpuTextureImpl(width, height, format)

    override val mcFrameBufferColorTexture: GpuTexture
        get() {
            //#if MC >= 26.2
            //$$ val fb = Minecraft.getInstance().gameRenderer.mainRenderTarget()
            //#else
            val fb = Minecraft.getMinecraft().framebuffer
            //#endif
            //#if MC >= 1.21.6
            //$$ val textureView = UGraphics.getPlatformAdapter().textureView(fb.colorAttachmentView!!)
            //#else
            val texture = UGraphics.getPlatformAdapter().texture(
                //#if MC >= 1.21.5
                //$$ fb.colorAttachment!!,
                //#else
                //#if MC>=11600
                //$$ fb.func_242996_f(),
                //#else
                fb.framebufferTexture,
                //#endif
                UGpuFormat.DEFAULT_RGBA,
                fb.framebufferTextureWidth,
                fb.framebufferTextureHeight,
                1,
                //#endif
            )
            val textureView = UGraphics.getDevice().createTextureView(texture)
            //#endif
            return UnownedGpuTextureImpl(GpuTexture.Format.RGBA8, textureView)
        }

    override val mcFrameBufferDepthTexture: GpuTexture?
        //#if MC>=12105
        //$$ get() {
            //#if MC >= 26.2
            //$$ val fb = Minecraft.getInstance().gameRenderer.mainRenderTarget()
            //#else
            //$$ val fb = MinecraftClient.getInstance().framebuffer
            //#endif
            //#if MC >= 1.21.6
            //$$ val textureView = UGraphics.getPlatformAdapter().textureView(fb.depthAttachmentView!!)
            //#else
            //$$ val texture = UGraphics.getPlatformAdapter().texture(fb.depthAttachment!!)
            //$$ val textureView = UGraphics.getDevice().createTextureView(texture)
            //#endif
        //$$     return UnownedGpuTextureImpl(GpuTexture.Format.DEPTH32, textureView)
        //$$ }
        //#else
        get() = null
        //#endif

    //#if MC>=12106
    //$$ override val outputColorTextureOverride: GpuTexture?
    //$$     get() = RenderSystem.outputColorTextureOverride?.let { tex ->
    //$$         UnownedGpuTextureImpl(GpuTexture.Format.RGBA8, UGraphics.getPlatformAdapter().textureView(tex))
    //$$     }
    //$$
    //$$ override val outputDepthTextureOverride: GpuTexture?
    //$$     get() = RenderSystem.outputDepthTextureOverride?.let { tex ->
    //$$         UnownedGpuTextureImpl(GpuTexture.Format.DEPTH32, UGraphics.getPlatformAdapter().textureView(tex))
    //$$     }
    //#else
    override val outputColorTextureOverride: GpuTexture? get() = null
    override val outputDepthTextureOverride: GpuTexture? get() = null
    //#endif

    override val irisReversesZ: Boolean
        //#if MC >= 26.2
        //$$ get() {
        //$$     if (ModLoaderUtil.isModLoaded("iris")) {
        //$$         // Separate method for class loading reasons
        //$$         fun value() = net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse
        //$$         return value()
        //$$     } else {
        //$$         return false
        //$$     }
        //$$ }
        //#else
        get() = false
        //#endif

    override val isZZeroToOne: Boolean
        //#if MC >= 26.2
        //$$ get() = RenderSystem.getDevice().deviceInfo.isZZeroToOne
        //#else
        get() = false
        //#endif

    override fun newWindowedTextureProvider(inner: WindowedImageProvider): WindowedTextureProvider =
        MinecraftWindowedTextureProvider(inner)

    override fun newUIPlayer(
        camera: State<PerspectiveCamera?>,
        profile: State<Pair<Skin, /*cape*/ String?>?>,
        cosmetics: State<Map<CosmeticSlot, EquippedCosmetic>>,
        sounds: State<Float>?,
        skinTextureOverride: UIdentifier?,
    ): UIPlayer {
        val baseGameProfile = GameProfile(UUID.randomUUID(), "EssentialBot")
        val gameProfile = memo {
            val (skin, cape) = profile() ?: return@memo null
            GameProfileManager.Overwrites(skin.hash, skin.model.type, cape).apply(baseGameProfile).wrapped()
        }
        val refHolder = ReferenceHolderImpl()
        val ui = EmulatedUI3DPlayer(
            draggable = camera.map { it == null }.toV1(refHolder),
            profile = gameProfile.toV1(refHolder),
            sounds = stateOf(sounds != null),
            soundsVolume = sounds ?: stateOf(0f),
        )
        ui.cosmeticsSource = cosmetics
        if (skinTextureOverride != null) {
            ui.skinTextureOverride = skinTextureOverride.toMC()
        }
        effect(refHolder) {
            ui.perspectiveCamera = camera()
            if (ui.perspectiveCamera != null) {
                ui.setRotations(0f, 0f)
            }
        }
        ui.holdOnto(refHolder)
        return ui
    }

    override fun shouldHideNotificationForHost(uuid: UUID): Boolean = DiscordIntegration.partyManager.shouldHideNotificationForHost(uuid)

    override fun openWardrobe(highlight: ItemId?) {
        val openedScreen = GuiUtil.openedScreen()
        if (openedScreen is Wardrobe) {
            if (highlight != null) {
                openedScreen.state.highlightItem.set(highlight)
            }
        } else {
            GuiUtil.openScreen {
                // Change initial category to stop the highlighted item highlighting on the featured page
                val initialCategory = if (highlight != null) WardrobeCategory.Cosmetics else null
                Wardrobe(initialCategory).apply { if (highlight != null) state.highlightItem.set(highlight) }
            }
        }
    }

    override fun openSocialMenu(channelId: Long?, user: UUID?) {
        fun configureScreen(screen: SocialMenu) {
            if (channelId != null) {
                screen.openMessageScreen(channelId)
            }
            if (user != null) {
                screen.openMessageScreen(user)
            }
        }
        val openedScreen = GuiUtil.openedScreen()
        if (openedScreen is SocialMenu) {
            configureScreen(openedScreen)
        } else {
            GuiUtil.openScreen { SocialMenu().also(::configureScreen) }
        }
    }

    override fun openScreenshotBrowser() {
        GuiUtil.openScreen { ScreenshotBrowser() }
    }

    override fun openWebAccountManager() {
        WebAccountManager.openInBrowser()
    }

    override fun connectToServer(name: String, address: String) {
        MinecraftUtils.connectToServer(name, address)
    }

    override fun shutdown() {
        MinecraftUtils.shutdown()
    }

    override val openEmoteWheelKeybind: GuiEssentialPlatform.Keybind
        get() = EssentialKeybindingRegistry.getInstance().openEmoteWheel

    override fun restoreMcStateAfterNanoVGDrawCall() {
        // NanoVG will have modified the GL state directly, so MC's state tracker are out of date and will potentially
        // skip calls because they incorrectly see them to be redundant. To fix that, we explicitly tell MC what
        // values may be set by NanoVG (and even if it did not set them, MC's state tracker will be back in sync).
        //#if MC>=12105
        //$$ GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        //#if MC >= 26.2
        //$$ GlStateManager._enableBlend(0)
        //#else
        //$$ GlStateManager._enableBlend()
        //#endif
        //$$ GlStateManager._disableDepthTest()
        //#else
        @Suppress("DEPRECATION")
        UGraphics.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        @Suppress("DEPRECATION")
        UGraphics.enableBlend()
        @Suppress("DEPRECATION")
        UGraphics.disableDepth()
        //#endif
        UGraphics.setActiveTexture(GL_TEXTURE0)
        //#if MC>=11700 && MC<12105
        //$$ net.minecraft.client.render.BufferRenderer.unbindAll()
        //#endif
    }

    override fun splitHostAndPort(address: String, defaultPort: Int): Pair<String, Int> {
        val hostAndPort = HostAndPort.fromString(address)
        val host = hostAndPort.host
        val port = hostAndPort.getPortOrDefault(25565)
        return Pair(host, port)
    }

    override fun parseIpAddress(address: String): InetAddress? {
        return try {
            InetAddresses.forString(address)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun loadIntegratedServerIcon(): BufferedImage? {
        val integratedServer = UMinecraft.getMinecraft().integratedServer
        val icon = integratedServer?.worldDirectory?.resolve("icon.png")
        return if (icon != null && icon.isRegularFile()) {
            ImageIO.read(icon.toFile())
        } else {
            null
        }
    }

    override fun isInMainMenu() = UScreen.currentScreen.isMainMenu

    // TODO: Eventually move to :gui:essential project
    override val modalPrerequisites
        get() = McModalPrerequisites

    override val suspensionManager: SuspensionManager
        get() = Essential.getInstance().connectionManager.suspensionManager

    override val rulesManager: RulesManager
        get() = Essential.getInstance().connectionManager.rulesManager

    //#if MC >= 26.2
    //$$ private val penToolVertexFormat: VertexFormat = VertexFormat.builder(0)
    //$$     .addAttribute("Position", GpuFormat.RGB32_FLOAT)
    //$$     .addAttribute("Color", GpuFormat.RGBA8_UNORM)
    //$$     .addAttribute("UV0", GpuFormat.RG32_FLOAT)
    //$$     .addAttribute("UV1", GpuFormat.RG16_SINT)
    //$$     .addAttribute("UV2", GpuFormat.RG16_SINT)
    //$$     .build()
    //#else
    private val penToolVertexFormat: VertexFormat = ImmutableMap.builder<String, VertexFormatElement>()
        //#if MC >= 1.21.1
        //$$ .put("Position", VertexFormatElement.POSITION)
        //$$ .put("Color", VertexFormatElement.COLOR)
        //$$ .put("UV0", VertexFormatElement.UV_0)
        //$$ .put("UV1", VertexFormatElement.UV_1)
        //$$ .put("UV2", VertexFormatElement.UV_2)
        //#else
        .put("Position", DefaultVertexFormats.POSITION_3F)
        .put("Color", DefaultVertexFormats.COLOR_4UB)
        .put("UV0", VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.UV, 2))
        .put("UV1", VertexFormatElement(1, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2))
        .put("UV2", VertexFormatElement(2, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.UV, 2))
        //#endif
        .build()
        .let { elements ->
            //#if MC >= 1.21.1
            //$$ VertexFormat.builder().also { builder -> elements.forEach { builder.add(it.key, it.value) }}.build()
            //#elseif MC >= 1.17
            //$$ VertexFormat(elements)
            //#elseif MC >= 1.16
            //$$ VertexFormat(com.google.common.collect.ImmutableList.copyOf(elements.values))
            //#else
            VertexFormat().also { format -> elements.forEach { format.addElement(it.value) } }
            //#endif
        }
    //#endif
    override fun newPenToolBufferBuilder(drawMode: UGraphics.DrawMode): UBufferBuilder =
        //#if MC >= 1.17
        //$$ UBufferBuilder.create(drawMode, penToolVertexFormat)
        //#else
        UBufferBuilder.create(drawMode, penToolVertexFormat).let { inner ->
            object : UBufferBuilder by inner {
                //#if MC < 1.16
                // MC has u/v flipped for SHORT types
                override fun overlay(u: Int, v: Int): UVertexConsumer = inner.overlay(v, u)
                override fun light(u: Int, v: Int): UVertexConsumer = inner.light(v, u)
                //#endif
            }
        }
        //#endif

    override fun newPenToolRenderPipelineBuilder(id: String, drawMode: UGraphics.DrawMode, vertSource: String, fragSource: String): URenderPipeline.Builder =
        URenderPipeline.builderWithLegacyShader(id, drawMode, penToolVertexFormat, vertSource, fragSource)

    override fun renderToTexture(width: Int, height: Int, block: (UMatrixStack) -> Unit): GpuTexture {
        //#if MC >= 1.21.6
        //$$ val texture = TemporaryTextureAllocator.TextureAllocation(width, height)
        //$$ val device = RenderSystem.getDevice()
        //#if MC >= 26.2
        //$$ val clearColor = org.joml.Vector4f(0f)
        //#else
        //$$ val clearColor = 0
        //#endif
        //$$ device.createCommandEncoder().clearColorAndDepthTextures(texture.texture, clearColor, texture.depthTexture, 1.0)
        //$$ AdvancedDrawContext.drawToTexture(texture, block)
        //$$ val uGpuTextureView = UGraphics.getPlatformAdapter().textureView(texture.textureView)
        //$$ return object : GpuTexture by UnownedGpuTextureImpl(GpuTexture.Format.RGBA8, uGpuTextureView) {
        //$$     override fun close() {
        //$$         texture.close()
        //$$     }
        //$$ }
        //#else
        val framebuffer = GlFrameBuffer(width, height)
        framebuffer.clear()
        framebuffer.useAsRenderTarget { _, _, _ ->
            //#if MC == 1.21.5
            //$$ val mc = MinecraftClient.getInstance() as MinecraftExt
            //$$ mc.`essential$setFramebufferOverride`(object : Framebuffer(null, true) {
            //$$     init {
            //$$         colorAttachment = UGraphics.getPlatformAdapter().texture(framebuffer.texture.uc)
            //$$         depthAttachment = UGraphics.getPlatformAdapter().texture(framebuffer.depthStencil.uc)
            //$$     }
            //$$ })
            //#endif
            //#if MC >= 1.17
            //$$ val guiScale = UResolution.scaleFactor.toFloat()
            //#if MC >= 1.19.3
            //$$ val projMatrix = Matrix4f().ortho(0f, width / guiScale, height / guiScale, 0f, 1000f, 3000f)
            //#else
            //$$ val projMatrix = Matrix4f.projectionMatrix(0f, width / guiScale, 0f, height / guiScale, 1000f, 3000f)
            //#endif
            //#if MC >= 1.21.2
            //$$ RenderSystem.setProjectionMatrix(projMatrix, ProjectionType.ORTHOGRAPHIC)
            //#elseif MC >= 1.20
            //$$ RenderSystem.setProjectionMatrix(projMatrix, VertexSorter.BY_Z)
            //#else
            //$$ RenderSystem.setProjectionMatrix(projMatrix)
            //#endif
            //$$ val modelViewStack = RenderSystem.getModelViewStack()
            //#if MC >= 1.20.5
            //$$ modelViewStack.pushMatrix()
            //$$ modelViewStack.identity()
            //$$ modelViewStack.translate(0f, 0f, -2000f)
            //#else
            //$$ modelViewStack.push()
            //$$ modelViewStack.loadIdentity()
            //$$ modelViewStack.translate(0.0, 0.0, -2000.0)
            //#endif
            //#if MC < 1.21.2
            //$$ RenderSystem.applyModelViewMatrix()
            //#endif
            //#else
            val guiScale = UResolution.scaleFactor
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.loadIdentity()
            GlStateManager.ortho(0.0, width / guiScale, height / guiScale, 0.0, 1000.0, 3000.0)
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.loadIdentity()
            GlStateManager.translate(0f, 0f, -2000f)
            //#endif
            block(UMatrixStack())
            //#if MC == 1.21.5
            //$$ mc.`essential$setFramebufferOverride`(null)
            //#endif

            //#if MC >= 1.17
            //#if MC >= 1.20.5
            //$$ modelViewStack.popMatrix()
            //#else
            //$$ modelViewStack.pop()
            //#endif
            //#if MC < 1.21.2
            //$$ RenderSystem.applyModelViewMatrix()
            //#endif
            //#endif
        }
        return object : GpuTexture by framebuffer.texture {
            override fun close() {
                framebuffer.close()
            }
        }
        //#endif
    }

    override val isEssentialContainerPresent: Boolean
        get() = EssentialContainerUtil.isContainerPresent()

    override val modsDependingOnEssential: List<ModInfo>
        get() = ModLoaderUtil.getModsDependingOnEssential()
}
