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
package gg.essential.gui.screenshot.components

import gg.essential.gui.elementa.state.v2.ListState
import gg.essential.gui.elementa.state.v2.ReferenceHolderImpl
import gg.essential.gui.elementa.state.v2.State
import gg.essential.gui.elementa.state.v2.combinators.map
import gg.essential.gui.elementa.state.v2.effect
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.stateOf
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.bytebuf.LimitedAllocator
import gg.essential.gui.screenshot.bytebuf.WorkStealingAllocator
import gg.essential.gui.screenshot.concurrent.PrioritizedCallable
import gg.essential.gui.screenshot.concurrent.PriorityThreadPoolExecutor
import gg.essential.gui.screenshot.providers.*
import gg.essential.util.lwjgl3.api.NativeImageReader
import gg.essential.network.connectionmanager.media.IScreenshotManager
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.PooledByteBufAllocator
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Manages the staged image loading providers for the Screenshot Manager
 */
class ScreenshotProviderManager(
    screenshotManager: IScreenshotManager,
    private val isScreenOpen: State<Boolean>,
    previewItems: ListState<ScreenshotId>,
    previewImageSize: State<Pair<Int, Int>>,
    focusImageSize: State<Pair<Int, Int>> = stateOf(Pair(0, 0)),
) {

    private val refHolder = ReferenceHolderImpl()
    private val nativeImageReader = platform.lwjgl3.get<NativeImageReader>()
    private val nonBlockingAllocator = LimitedAllocator(PooledByteBufAllocator.DEFAULT, MAX_MEMORY)
    private val allocator = WorkStealingAllocator(nonBlockingAllocator) {
        val task = pool.stealBackgroundTask()
        if (task != null) {
            task.run()
        } else {
            Thread.sleep(1) // wait for other threads to make progress
        }
    }

    private val targetPreviewImageSize = previewImageSize.map { (realWidth, realHeight) ->
        roundResolutionToCommonValues(Pair(realWidth, realHeight))
    }

    private val targetFocusImageSize = focusImageSize

    private val minResolutionBicubicProvider = createFileCachedBicubicProvider(minResolutionTargetResolution)
    private var focusImageResolution = createFocusImageProvider(minResolutionTargetResolution)
    private val minResolutionMinecraftWindowedTextureProvider = platform.newWindowedTextureProvider(
        ThreadedWindowedProvider(
            minResolutionBicubicProvider,
            pool,
            PrioritizedCallable.MIN_RES,
        )
    )

    private val scopePreservedMinResolutionProvider =  ScopePreservingWindowedProvider(
        MaxScopeExpansionWindowProvider(
            minResolutionMinecraftWindowedTextureProvider
        )
    )

    private val providerArray: Array<WindowedProvider<RegisteredTexture>> = arrayOf(

        // First item is the primary and provider
        // This is updated to be the target resolution when the target resolution changes
        createWindowedTextureProvider(Pair(200, 200)),

        // Fallback to the lowest resolution if the target resolution is not available
        // Expand the scope of the max to keep everything in the scope
        scopePreservedMinResolutionProvider
    )

    // The actual provider list view items are queried from
    private val provider = PriorityDelegatedWindowProvider(providerArray)

    // The screenshots in the current view [gg.essential.gui.screenshot.components.Tab]
    // Setup in reloadItems()
    val currentIdsState: ListState<ScreenshotId> = previewItems
    val currentIds: List<ScreenshotId>
        get() = currentIdsState.getUntracked()

    private val indexByIdState = State { currentIdsState().withIndex().associate { it.value to it.index } }
    val indexById: Map<ScreenshotId, Int>
        get() = indexByIdState.getUntracked()

    /**
     * Finds the nearest resolution with no less than 30% difference from our value
     * Candidate resolutions are found by starting at 200 and growing by 30%
     */
    private fun roundResolutionToCommonValues(targetResolution: Pair<Int, Int>): Pair<Int, Int> {
        val (width, height) = targetResolution
        return Pair(roundResolutionToCommonValues(width), roundResolutionToCommonValues(height))
    }

    private fun roundResolutionToCommonValues(targetResolution: Int): Int {
        val minResolution = 200f //Roughly native resolution for images being rendered on a 1080p monitor with 7 per row
        val acceptableScaling = .3f //Percentage the output resolution can be at most wrong by

        var outputLTInput = minResolution //output resolution one increment smaller than the targetResolution

        //output resolution one increment greater than the targetResolution
        var outputGTInput = minResolution * (1f + acceptableScaling)

        while (outputGTInput < targetResolution) {
            outputLTInput = outputGTInput
            outputGTInput *= (1 + acceptableScaling)
        }


        //Choose the resolution that we are closer to
        val deltaLt = targetResolution - outputLTInput
        val deltaGt = outputGTInput - targetResolution

        return if (deltaLt < deltaGt) outputLTInput.toInt() else outputGTInput.toInt()
    }

    private fun createFileCachedBicubicProvider(targetResolution: Pair<Int, Int>): FileCachedWindowedImageProvider =
        createFileCachedBicubicProvider(targetResolution, pool, allocator, platform.essentialBaseDir, nativeImageReader)

    private fun createFocusImageProvider(targetResolution: Pair<Int, Int>): WindowedTextureProvider {
        return platform.newWindowedTextureProvider(
            ThreadedWindowedProvider(
                createFileCachedBicubicProvider(targetResolution),
                pool,
                PrioritizedCallable.FOCUS,
            )
        )
    }

    private fun createWindowedTextureProvider(resolution: Pair<Int, Int>): WindowedTextureProvider {
        return ScopeExpansionWindowProvider(
            platform.newWindowedTextureProvider(
                ThreadedWindowedProvider(
                    createFileCachedBicubicProvider(roundResolutionToCommonValues(resolution)), pool, PrioritizedCallable.REGULAR
                ),
            ),
            1f,
        )
    }

    init {
        val allIds = screenshotManager.screenshots.mapEach { it.id }
        effect(refHolder) {
            scopePreservedMinResolutionProvider.itemsToBePreserved = allIds()
        }

        effect(refHolder) {
            val size = targetFocusImageSize()
            focusImageResolution = TransitionWindowedProvider(createFocusImageProvider(size), focusImageResolution)
            focusImageResolution.items = currentIds
        }

        effect(refHolder) {
            val size = targetPreviewImageSize()
            val newTargetProvider = createWindowedTextureProvider(size)
            val currentTargetProvider = providerArray[0]

            //Debug log to help track down any user experienced performance issues
            LOGGER.debug("Updating provider to target resolution $size")


            val transitionWindowedProvider = TransitionWindowedProvider(newTargetProvider, currentTargetProvider)
            transitionWindowedProvider.items = currentIds
            providerArray[0] = transitionWindowedProvider
        }

        effect(refHolder) {
            val items = currentIdsState()
            provider.items = items
            focusImageResolution.items = items
            flushCache()
        }
    }

    private var lastWindowProvided: WindowedProvider.Window? = null
    private fun flushCache() {
        if (!isScreenOpen.getUntracked()) {
            return // nothing should be cached
        }
        // provide() is only called, and therefore provider is only updated, while the list
        // view is active because that's the only thing which needs it. This in turn means
        // that if we delete an edited image, and then re-edit the original to re-create a
        // new edited image at the exact same location, all without leaving focus view, then
        // provider will never get a chance to flush the old image from its cache. It doesn't
        // know that the underlying file content has changed, and by the time its provide method
        // is called, its items is effectively unchanged.
        // We can mitigate this by forcing a call to provide every time the items are changed,
        // and therefore also right after an item is deleted, regardless of which view is currently active.
        provide(lastWindowProvided ?: WindowedProvider.Window(IntRange.EMPTY, false))
    }

    init { effect(refHolder) { if (!isScreenOpen()) cleanup() } }
    private fun cleanup() {
        // Call with empty windows to clean up any allocated textures
        provider.provide(emptyList(), emptySet())
        focusImageResolution.provide(emptyList(), emptySet())
    }

    /**
     * Queries the [provider] with the specificed window and returns the result
     */
    fun provide(window: WindowedProvider.Window): Map<ScreenshotId, RegisteredTexture> {
        val windows = listOfNotNull(window.inRange(provider.items.indices))
        if (windows.isEmpty()) {
            // If we call the provider with an empty list, it will unnecessarily clean up all resources.
            // To prevent this, we skip the call in that case and just return an empty map.
            return emptyMap()
        }
        lastWindowProvided = windows.first()
        return provider.provide(windows, emptySet())
    }

    /**
     * Queries the [focusImageResolution] with the specified window and returns the result
     */
    fun provideFocus(window: WindowedProvider.Window): Map<ScreenshotId, RegisteredTexture> {
        return provideFocus(listOf(window))
    }

    /**
     * Queries the [focusImageResolution] with the specified windows and returns the result
     */
    fun provideFocus(windows: List<WindowedProvider.Window>): Map<ScreenshotId, RegisteredTexture> {
        return focusImageResolution.provide(windows, emptySet())
    }

    val allocatedBytes: Long
        get() = nonBlockingAllocator.getAllocatedBytes()

    companion object {

        private val LOGGER = LoggerFactory.getLogger(ScreenshotProviderManager::class.java)

        private val nThread = Runtime.getRuntime().availableProcessors() * 4
        private val pool = PriorityThreadPoolExecutor(nThread).apply {
            setKeepAliveTime(30, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }

        /**
         * The resolution of the smallest down sampled size that screenshots are available at
         */
        @JvmField
        val minResolutionTargetResolution = Pair(40, 40)

        val MAX_MEMORY = (System.getProperty("essential.screenshots.max_mem_mb")?.toLong() ?: 100) * 1_000_000

        @JvmOverloads
        fun createFileCachedBicubicProvider(
            targetResolution: Pair<Int, Int>,
            pool: PriorityThreadPoolExecutor,
            alloc: ByteBufAllocator,
            essentialDir: Path,
            nativeImageReader: NativeImageReader,
            precomputeOnly: Boolean = false,
        ): FileCachedWindowedImageProvider {
            val (targetWidth, targetHeight) = targetResolution
            return FileCachedWindowedImageProvider(
                PostProcessWindowedImageProvider(
                    CloudflareImageProvider(
                        DiskWindowedImageProvider(nativeImageReader, alloc),
                        nativeImageReader,
                        alloc,
                        targetResolution,
                    ),
                    PostProcessWindowedImageProvider.bicubicFilter(targetWidth, targetHeight)
                ),
                FileCachedWindowedImageProvider.inDirectory(
                    essentialDir.resolve("screenshot-cache")
                        .resolve("bicubic_${targetWidth}x$targetHeight")
                        .also(Files::createDirectories)
                ),
                pool,
                nativeImageReader,
                alloc,
                precomputeOnly
            )
        }
    }
}
