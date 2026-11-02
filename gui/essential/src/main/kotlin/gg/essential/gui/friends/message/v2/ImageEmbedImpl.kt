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
package gg.essential.gui.friends.message.v2

import com.sparkuniverse.toolbox.chat.enums.ChannelType
import gg.essential.elementa.components.UIImage
import gg.essential.gui.EssentialPalette
import gg.essential.gui.common.IconButton
import gg.essential.gui.common.LoadingIcon
import gg.essential.gui.elementa.state.v2.mutableStateOf
import gg.essential.gui.friends.message.MessageUtils
import gg.essential.gui.layoutdsl.Alignment
import gg.essential.gui.layoutdsl.Arrangement
import gg.essential.gui.layoutdsl.BasicWidthModifier
import gg.essential.gui.layoutdsl.LayoutScope
import gg.essential.gui.layoutdsl.Modifier
import gg.essential.gui.layoutdsl.alignHorizontal
import gg.essential.gui.layoutdsl.box
import gg.essential.gui.layoutdsl.childBasedHeight
import gg.essential.gui.layoutdsl.color
import gg.essential.gui.layoutdsl.fillWidth
import gg.essential.gui.layoutdsl.heightAspect
import gg.essential.gui.layoutdsl.hoverColor
import gg.essential.gui.layoutdsl.hoverScope
import gg.essential.gui.layoutdsl.layoutAsBox
import gg.essential.gui.layoutdsl.onLeftClick
import gg.essential.gui.layoutdsl.outline
import gg.essential.gui.layoutdsl.row
import gg.essential.gui.layoutdsl.shadow
import gg.essential.gui.layoutdsl.text
import gg.essential.gui.layoutdsl.whenTrue
import gg.essential.gui.layoutdsl.width
import gg.essential.gui.layoutdsl.withHoverState
import gg.essential.gui.notification.Notifications
import gg.essential.gui.notification.error
import gg.essential.gui.screenshot.copyScreenshotToClipboard
import gg.essential.gui.screenshot.saveImageAsScreenshot
import gg.essential.util.Game
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import gg.essential.util.httpGetToInputStream
import gg.essential.util.loadUIImage
import gg.essential.util.onRightClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.math.min

class ImageEmbedImpl(
    val mediaId: String,
    wrapper: MessageWrapper,
) : ImageEmbed(wrapper) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Game)
    private var loadJob: Job? = null

    override var url: URL? = null

    private val loadState = mutableStateOf<LoadState>(LoadState.Loading)
    private val highlighted = mutableStateOf(false)

    private val loadedImage: BufferedImage?
        get() = when (val state = loadState.getUntracked()) {
            is LoadState.Loaded -> state.image
            else -> null
        }

    init {
        val isAnnouncement = wrapper.channelType == ChannelType.ANNOUNCEMENT

        layoutAsBox(BasicWidthModifier { MessageUtils.getMessageWidth(isAnnouncement) }.childBasedHeight()) {
            bind(loadState) { state ->
                when (state) {
                    LoadState.Loading -> loadingPlaceholder()
                    LoadState.Failed -> failedPlaceholder()
                    is LoadState.Loaded -> loadedImage(state)
                }
            }
        }

        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadState.set(LoadState.Loading)

        val localPaths = platform.screenshotManager.getUploadedLocalPathsCache(mediaId)

        loadJob = coroutineScope.launch {
            val newState = try {
                val baseUrl = platform.cmConnection.getGatewayService("media").baseUrl()
                url = baseUrl.resolve(mediaId)?.toUrl()

                val image = withContext(Dispatchers.IO) {
                    localPaths.firstNotNullOfOrNull { readLocalImage(it) } ?: readRemoteImage(baseUrl)
                }

                val uiImage = loadUIImage(image).apply {
                    textureMagFilter = UIImage.TextureScalingMode.LINEAR
                    textureMinFilter = UIImage.TextureScalingMode.LINEAR
                }

                LoadState.Loaded(image, uiImage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOGGER.warn("Failed to load image $mediaId", e)
                LoadState.Failed
            }

            loadState.set(newState)
        }
    }

    private fun readLocalImage(path: Path): BufferedImage? {
        if (!path.isRegularFile()) {
            LOGGER.debug("Local image path does not point to a file: {}", path)
            return null
        }

        return try {
            path.inputStream().use { ImageIO.read(it) }
        } catch (e: Exception) {
            LOGGER.debug("Failed to read local image $path", e)
            null
        }
    }

    private suspend fun readRemoteImage(baseUrl: HttpUrl): BufferedImage {
        val imageUrl = baseUrl.resolve("$mediaId/redirect")
            ?: throw IOException("Failed to resolve image url for $mediaId")

        val image = httpGetToInputStream(imageUrl.toString()).use { ImageIO.read(it) }
        return image ?: throw IOException("Unsupported image format for $mediaId")
    }

    private fun LayoutScope.placeholder(modifier: Modifier, block: LayoutScope.() -> Unit) {
        box(Modifier.fillWidth().heightAspect(1 / DEFAULT_ASPECT_RATIO).then(modifier), block)
    }

    private fun LayoutScope.loadingPlaceholder() {
        placeholder(Modifier.color(EssentialPalette.LIGHT_DIVIDER)) {
            LoadingIcon(2.0)()
        }
    }

    private fun LayoutScope.failedPlaceholder() {
        val colorModifier = Modifier.withHoverState { hovered ->
            Modifier.color {
                if (hovered() || messageWrapper.appearHovered()) {
                    EssentialPalette.BUTTON_HIGHLIGHT
                } else {
                    EssentialPalette.COMPONENT_BACKGROUND_HIGHLIGHT
                }
            }
        }

        placeholder(colorModifier.hoverScope()) {
            row(Arrangement.spacedBy(10f)) {
                text(
                    "Image failed to load",
                    Modifier.shadow(EssentialPalette.TEXT_SHADOW),
                    centeringContainsShadow = false,
                )

                IconButton(EssentialPalette.RETRY_7X)(
                    Modifier
                        .width(17f)
                        .heightAspect(1f)
                        .color(EssentialPalette.COMPONENT_BACKGROUND)
                        .hoverColor(EssentialPalette.BUTTON)
                        .hoverScope()
                        .shadow(Color.BLACK)
                        .onLeftClick { load() }
                )
            }
        }
    }

    private fun LayoutScope.loadedImage(state: LoadState.Loaded) {
        // Images taller than 16:9 are narrowed so that they are never taller than a 16:9 image would be
        val widthFraction = min(1f, state.aspectRatio / DEFAULT_ASPECT_RATIO)
        val alignment = if (messageWrapper.sentByClient) Alignment.End else Alignment.Start

        val modifier = Modifier
            .fillWidth(fraction = widthFraction)
            .heightAspect(1 / state.aspectRatio)
            .alignHorizontal(alignment)
            .whenTrue(highlighted, Modifier.outline(EssentialPalette.MESSAGE_HIGHLIGHT, 3f, drawInsideChildren = true))
            .onLeftClick {
                platform.pushModal(doFadeInTransition = false) { manager ->
                    ImagePreviewModal(manager, state.uiImage, state.aspectRatio, url)
                }
            }

        val imageComponent = state.uiImage(modifier)

        // TODO: Add `Modifier.onRightClick`.
        imageComponent.onRightClick {
            messageWrapper.openOptionMenu(it, this@ImageEmbedImpl)
        }
    }

    override fun copyImageToClipboard() {
        val image = loadedImage ?: return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val tempFile = Files.createTempFile("essential-screenshot", ".png")

                try {
                    ImageIO.write(image, "png", tempFile.toFile())

                    withContext(Dispatchers.Game) {
                        copyScreenshotToClipboard(tempFile)
                    }
                } finally {
                    tempFile.deleteIfExists()
                }
            } catch (e: IOException) {
                LOGGER.error("Failed to copy image $mediaId to clipboard", e)
                Notifications.error("Failed to copy picture", "")
            }
        }
    }

    override fun saveImageToScreenshotBrowser() {
        val image = loadedImage ?: return

        coroutineScope.launch {
            saveImageAsScreenshot(image)
        }
    }

    override fun beginHighlight() {
        highlighted.set(true)
    }

    override fun releaseHighlight() {
        highlighted.set(false)
    }

    private sealed interface LoadState {
        object Loading : LoadState

        class Loaded(val image: BufferedImage, val uiImage: UIImage) : LoadState {
            val aspectRatio = image.width / image.height.toFloat()
        }

        object Failed : LoadState
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ImageEmbedImpl::class.java)

        private const val DEFAULT_ASPECT_RATIO = 16 / 9f
    }
}
