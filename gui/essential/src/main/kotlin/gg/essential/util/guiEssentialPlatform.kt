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

import gg.essential.connectionmanager.common.packet.telemetry.ClientTelemetryPacket
import gg.essential.cosmetics.EquippedCosmetic
import gg.essential.gui.common.UIPlayer
import gg.essential.gui.common.modal.Modal
import gg.essential.gui.elementa.essentialmarkdown.EssentialMarkdown
import gg.essential.gui.elementa.state.v2.MutableState
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.friends.message.v2.MessageRef
import gg.essential.gui.friends.state.SocialStates
import gg.essential.gui.modals.ModalPrerequisites
import gg.essential.gui.notification.NotificationsManager
import gg.essential.gui.overlay.ModalManager
import gg.essential.gui.overlay.OverlayManager
import gg.essential.gui.screenshot.bytebuf.LimitedAllocator
import gg.essential.gui.screenshot.providers.WindowedImageProvider
import gg.essential.gui.screenshot.providers.WindowedTextureProvider
import gg.essential.gui.wardrobe.ItemId
import gg.essential.handlers.MojangSkinManager
import gg.essential.mod.Skin
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.mod.cosmetics.preview.PerspectiveCamera
import gg.essential.model.backend.RenderBackend
import gg.essential.network.CMConnection
import gg.essential.network.connectionmanager.cosmetics.AssetLoader
import gg.essential.network.connectionmanager.cosmetics.ICosmeticsManager
import gg.essential.network.connectionmanager.cosmetics.ModelLoader
import gg.essential.network.connectionmanager.cosmetics.WardrobeSettings
import gg.essential.network.connectionmanager.features.DisabledFeaturesManager
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.network.connectionmanager.notices.INoticesManager
import gg.essential.network.connectionmanager.skins.SkinsManager
import gg.essential.network.connectionmanager.social.RulesManager
import gg.essential.network.connectionmanager.suspension.SuspensionManager
import gg.essential.universal.UGraphics
import gg.essential.universal.UImage
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.utils.ReleasedDynamicTexture
import gg.essential.universal.vertex.UBufferBuilder
import gg.essential.util.image.GpuTexture
import gg.essential.util.image.bitmap.MutableBitmap
import gg.essential.util.lwjgl3.Lwjgl3Loader
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.CoroutineDispatcher
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.nio.file.Path
import java.util.UUID
import kotlin.jvm.Throws

interface GuiEssentialPlatform {
    val mcVersion: Int

    val clientThreadDispatcher: CoroutineDispatcher

    val renderBackend: RenderBackend
    val overlayManager: OverlayManager
    val assetLoader: AssetLoader
    val modelLoader: ModelLoader
    val lwjgl3: Lwjgl3Loader

    val cmConnection: CMConnection

    val notifications: NotificationsManager

    fun createModalManager(): ModalManager
    fun pushModal(builder: (ModalManager) -> Modal): Modal {
        val manager = createModalManager()
        val modal = builder(manager)
        manager.queueModal(modal)
        return modal
    }

    fun onResourceManagerReload(runnable: Runnable)

    @Throws(IOException::class)
    fun bitmapFromMinecraftResource(identifier: UIdentifier): MutableBitmap?

    @Throws(IOException::class)
    fun bitmapFromInputStream(inputStream: InputStream): MutableBitmap

    fun uImageIntoReleasedDynamicTexture(uImage: UImage): ReleasedDynamicTexture

    fun identifierFromTexture(texture: RenderBackend.Texture): UIdentifier

    fun getGlId(identifier: UIdentifier): Int

    fun playSound(identifier: UIdentifier)
    fun playNoteHatSound(volume: Float, pitch: Float)

    fun registerCosmeticTexture(name: String, texture: ReleasedDynamicTexture): UIdentifier

    fun dismissModalOnScreenChange(modal: Modal, dismiss: () -> Unit)

    fun <T> registerEventBusListener(cls: Class<T>, listener: (T) -> Unit, priority: Int = 0)

    fun <T> unregisterEventBusListener(cls: Class<T>, listener: (T) -> Unit)

    val essentialBaseDir: Path
    val mcBaseDir: Path
    val config: Config

    val mcProtocolVersion: Int
    val mcGameVersion: String

    val localModList: Map<String /* Display name */, String /* Version */>

    fun currentServerType(): ServerType?

    fun registerActiveSessionState(state: MutableState<USession>)

    fun createSocialStates(): SocialStates

    fun resolveMessageRef(messageRef: MessageRef)

    // TODO inline once everything's accessible
    fun haveActiveRemoteSpsSession(host: UUID): Boolean

    val essentialUriListener: EssentialMarkdown.(EssentialMarkdown.LinkClickEvent) -> Unit

    val noticesManager: INoticesManager

    val autoUpdateManager: AutoUpdateManager

    val skinsManager: SkinsManager

    val cosmeticsManager: ICosmeticsManager

    val wardrobeSettings: WardrobeSettings

    val mojangSkinManager: MojangSkinManager

    val screenshotManager: IScreenshotManager

    val disabledFeaturesManager: DisabledFeaturesManager

    val screenshotFolder: Path

    val isOptiFineInstalled: Boolean

    val trustedHosts: Set<String>

    val reportReasons: Map<String, String>

    // TODO move to :gui:essential project
    fun fileReport(modalManager: ModalManager, channelId: Long, messageId: Long, sender: UUID, reason: String)

    fun enqueueTelemetry(packet: ClientTelemetryPacket)

    fun findCodeSource(javaClass: Class<*>): CodeSource?

    fun trackByteBuf(alloc: LimitedAllocator, buf: ByteBuf): ByteBuf

    fun newGlFrameBuffer(width: Int, height: Int, colorFormat: GpuTexture.Format, depthFormat: GpuTexture.Format): GlFrameBuffer

    fun newGpuTexture(width: Int, height: Int, format: GpuTexture.Format): GpuTexture

    val mcFrameBufferColorTexture: GpuTexture
    val mcFrameBufferDepthTexture: GpuTexture?

    val outputColorTextureOverride: GpuTexture?
    val outputDepthTextureOverride: GpuTexture?

    /**
     * Whether MC uses "reversed Z" when storing depth.
     * Meaning the depth value of the far plane is 0 instead of 1.
     * See https://developer.nvidia.com/blog/visualizing-depth-precision/.
     *
     * Note that Elementa uses regular Z on all versions.
     *
     * Note that Iris will revert to regular Z when a shader pack is active. That however will not be reflected by
     * this property, because it will also automatically flip all your depth values when interacting via B3D.
     * Our GpuTexture will also automatically flip depth values accordingly.
     * This change is however observable when reading from a depth texture in a shader, so one needs to account for it
     * there. See [irisReversesZ].
     */
    val usesReversedZ: Boolean get() = mcVersion >= 26_02_00

    /**
     * Whether Iris is presently reversing Z.
     * Happens on 26.2+ when a shader pack is active. See the respective paragraph in [usesReversedZ].
     */
    val irisReversesZ: Boolean

    /**
     * Whether clip space is [-1, 1] (like conventional OpenGL) or [0, 1] (like Vulkan).
     *
     * Note however that this is not just a `isVulkan` check!
     * As of Minecraft 26.2, OpenGL will be configured to use [0, 1] as well, when GL_ARB_clip_space is supported.
     */
    val isZZeroToOne: Boolean

    fun newWindowedTextureProvider(inner: WindowedImageProvider): WindowedTextureProvider

    fun newUIPlayer(
        camera: State<PerspectiveCamera?>,
        profile: State<Pair<Skin, /*cape*/ String?>?>,
        cosmetics: State<Map<CosmeticSlot, EquippedCosmetic>>,
        sounds: State<Float>? = null,
        skinTextureOverride: UIdentifier? = null,
    ): UIPlayer

    // TODO move DiscordIntegration to :gui:essential project
    fun shouldHideNotificationForHost(uuid: UUID): Boolean

    fun openWardrobe(highlight: ItemId? = null)

    fun openSocialMenu(channelId: Long? = null, user: UUID? = null)

    fun openScreenshotBrowser()

    fun openWebAccountManager()

    fun connectToServer(name: String, address: String)

    fun shutdown()

    val openEmoteWheelKeybind: Keybind

    fun restoreMcStateAfterNanoVGDrawCall()

    fun splitHostAndPort(address: String, defaultPort: Int = 25565): Pair<String, Int>

    /** Parses the given IP address. Returns `null` if the address is invalid. Never does any DNS lookups. */
    fun parseIpAddress(address: String): InetAddress?

    fun loadIntegratedServerIcon(): BufferedImage?

    fun isInMainMenu(): Boolean

    // TODO: Eventually move override to :gui:essential project
    val modalPrerequisites: ModalPrerequisites

    val suspensionManager: SuspensionManager
    val rulesManager: RulesManager

    fun newPenToolBufferBuilder(drawMode: UGraphics.DrawMode): UBufferBuilder
    fun newPenToolRenderPipelineBuilder(
        id: String,
        drawMode: UGraphics.DrawMode,
        vertSource: String,
        fragSource: String,
    ): URenderPipeline.Builder

    fun renderToTexture(width: Int, height: Int, block: (UMatrixStack) -> Unit): GpuTexture

    val isEssentialContainerPresent: Boolean
    val modsDependingOnEssential: List<ModInfo>

    interface Keybind {
        val isBound: Boolean
        val boundKeyName: String?
        val isConflicting: Boolean
    }

    interface Config {
        val shouldDarkenRetexturedButtons: Boolean
        val useVanillaButtonForRetexturing: State<Boolean>
    }

    companion object {
        val platform: GuiEssentialPlatform =
            Class.forName(GuiEssentialPlatform::class.java.name + "Impl").newInstance() as GuiEssentialPlatform
    }
}
