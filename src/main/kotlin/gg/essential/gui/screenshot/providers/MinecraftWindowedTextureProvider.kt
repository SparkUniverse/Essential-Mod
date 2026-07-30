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
package gg.essential.gui.screenshot.providers

import gg.essential.Essential
import gg.essential.gui.screenshot.ScreenshotId
import gg.essential.gui.screenshot.downsampling.PixelBuffer
import gg.essential.gui.screenshot.image.PixelBufferTexture
import gg.essential.universal.UMinecraft
import gg.essential.util.OperatingSystem
import gg.essential.util.RefCounted
import gg.essential.util.executor
import gg.essential.util.os
import org.lwjgl.opengl.GL11

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

//#if MC<11600
import org.lwjgl.opengl.Display
import org.lwjgl.opengl.SharedDrawable

//#else
//$$ import org.lwjgl.glfw.GLFW
//$$ import org.lwjgl.opengl.GL
//$$ import org.lwjgl.system.MemoryStack
//$$ import org.lwjgl.system.MemoryUtil
//#endif
/**
 * Provides a unique Minecraft ResourceLocation for each screenshot depending on name and resolution
 * Caches results while in use
 *
 * This class is not thread safe and therefore must only ever be used by a single thread.
 */
class MinecraftWindowedTextureProvider(
    private val sourceProvider: WindowedImageProvider
) : WindowedTextureProvider {

    //No auto expire rule here, we will be manually maintaining the contents of the cache
    private val loaded = mutableMapOf<ScreenshotId, RegisteredTexture>()

    private val loading = mutableMapOf<ScreenshotId, RegisteredTexture>()

    override var items: List<ScreenshotId> by sourceProvider::items

    private var textureManager: AsyncTextureManager? = null

    override fun provide(windows: List<WindowedProvider.Window>, optional: Set<ScreenshotId>): Map<ScreenshotId, RegisteredTexture> {
        // Avoid creating a new texture manager only to delete it later
        if (windows.isEmpty() && textureManager == null) {
            return emptyMap()
        }
        val textureManager = textureManager ?: AsyncTextureManager().also { textureManager = it }

        val processed = mutableMapOf<ScreenshotId, RegisteredTexture>()

        val requestedIds = windows.flatMapTo(mutableSetOf()) { window ->
            window.range.asSequence().map { items[it] }.filterNot { it in optional }
        }

        for (id in requestedIds) {
            processed[id] = loaded[id] ?: continue
        }

        for (entry in sourceProvider.provide(windows, optional + loaded.keys + loading.keys)) {
            val id = entry.key

            if (id !in loaded && id !in loading && id !in optional) {
                textureManager.createResource(id, entry.value)
            }

            entry.value.release()
        }

        for (id in textureManager.getFinished()) {
            val resourceLocation = loading.remove(id)!!
            loaded[id] = resourceLocation
            if (id in requestedIds) {
                processed[id] = resourceLocation
            }
        }

        loaded.entries.removeIf {
            if (it.key !in processed) {
                onRemoval(it.value)
                return@removeIf true
            }
            false
        }

        if (windows.isEmpty()) {
            textureManager.cleanup()
            this.textureManager = null
        }

        return processed
    }

    protected fun finalize() {
        textureManager?.cleanup()
        textureManager = null
        if (loaded.isNotEmpty()) {
            Essential.logger.warn("Entries in provider cleaned up during finalize instead of prior. Did you forget to call `provide(emptyList())`?")
            invalidateAll()
        }
    }

    fun invalidateAll() {
        loaded.entries.removeIf {
            onRemoval(it.value)
            true
        }
    }

    private fun AsyncTextureManager.createResource(id: ScreenshotId, image: PixelBuffer) {
        val texture = PixelBufferTexture(id.toString(), image)
        loading[id] = RegisteredTexture(texture.uGpuTextureView)
        image.retain()
        upload(id) {
            texture.upload(image)
            image.release()
            texture
        }
    }


    private fun onRemoval(texture: RegisteredTexture) {
        UMinecraft.getMinecraft().executor.execute {
            texture.gpuTextureView?.texture?.close()
            texture.gpuTextureView?.close()
        }
    }
}

var asyncErrored = false

//#if MC>=11600
//$$ var currentHintsFailed = false
//$$ var workingGlVersion: Pair<Int, Int>? = null
//$$
//$$ // All possible GL versions
//$$ var glVersions = listOf(
//$$     4 to 6, 4 to 5, 4 to 4, 4 to 3, 4 to 2, 4 to 1, 4 to 0, 3 to 3, 3 to 2, 3 to 1, 3 to 0, 2 to 1, 2 to 0
//$$ )
//#endif

private fun makeUploadBackend(): UploadBackend {
    //#if MC >= 26.2
    //$$ // B3D doesn't provide any way to do async texture uploads, so for now we'll have to do uploads
    //$$ // from the main thread when using Vulkan.
    //$$ if (com.mojang.blaze3d.systems.RenderSystem.getDevice().deviceInfo.backendName == "Vulkan") {
    //$$     return NotAsyncUploadBackend()
    //$$ }
    //#endif

    val supported =
        //#if MC <= 1.12.2
        // Disable this flag due to a LWJGL2 (<= 1.12.2) + MacOS threading bug
        if (os == OperatingSystem.MACOS) false else
        //#endif
            System.getProperty("essential.async_texture_loading")?.toBoolean() ?: true


    if (!supported || asyncErrored) {
        return NotAsyncUploadBackend()
    }

    //#if MC>=11600
    //$$ val mcWindow = GLFW.glfwGetCurrentContext()
    //$$
    //$$ fun createWindow(version: Pair<Int, Int>?): Long {
    //$$     if (version != null) {
    //$$         val (major, minor) = version
    //$$         GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major)
    //$$         GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor)
    //$$         // It's only valid to set a profile on GL 3.2+, but querying the attribute will return one on any version
    //$$         if (major > 3 && minor > 2) {
    //$$             GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.glfwGetWindowAttrib(mcWindow, GLFW.GLFW_OPENGL_PROFILE))
    //$$         } else {
    //$$             GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_ANY_PROFILE)
    //$$         }
    //$$     }
    //$$
    //$$     GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
    //$$     val window = GLFW.glfwCreateWindow(1, 1, "Essential screenshot uploader", 0, mcWindow)
    //$$     GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE)
    //$$     return window
    //$$ }
    //$$
    //$$ // We don't want Minecraft to log GLFW errors as we try each version, so we disable the error callback
    //$$ val previousErrorCallback = GLFW.glfwSetErrorCallback(null)
    //$$ try {
    //$$     // First, try the current window hints
    //$$     if (!currentHintsFailed) {
    //$$         Essential.logger.debug("Trying current window hints")
    //$$
    //$$         val window = createWindow(null)
    //$$         if (window != 0L) {
    //$$             return AsyncUploadBackendImpl(window)
    //$$         } else {
    //$$             currentHintsFailed = true;
    //$$             Essential.logger.debug("Current window hints failed, trying GL versions")
    //$$         }
    //$$     }
    //$$
    //$$     // Try multiple opengl versions to find a working one, since GLFW doesn't tell us the real version
    //$$     GLFW.glfwDefaultWindowHints()
    //$$     GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API)
    //$$     GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API)
    //$$     // This attribute can be safely determined from the existing window
    //$$     GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.glfwGetWindowAttrib(mcWindow, GLFW.GLFW_OPENGL_FORWARD_COMPAT))
    //$$
    //$$     if (workingGlVersion == null) {
    //$$         for ((major, minor) in glVersions) {
    //$$             Essential.logger.debug("Trying GL version $major.$minor")
    //$$             val window = createWindow(major to minor)
    //$$             if (window != 0L) {
    //$$                 Essential.logger.debug("Found working GL version $major.$minor")
    //$$                 workingGlVersion = major to minor
    //$$                 // Clear the GLFW error flag
    //$$                 GLFW.glfwGetError(null)
    //$$                 return AsyncUploadBackendImpl(window)
    //$$             }
    //$$         }
    //$$     }
    //$$ } finally {
    //$$     GLFW.glfwSetErrorCallback(previousErrorCallback)
    //$$ }
    //$$
    //$$ val version = workingGlVersion
    //$$ if (version != null) {
    //$$     val window = createWindow(version)
    //$$     if (window != 0L) {
    //$$         return AsyncUploadBackendImpl(window)
    //$$     }
    //$$ }
    //$$
    //$$ Essential.logger.warn("Failed to create GLFW window! Falling back to non-async uploads.")
    //$$ // Print the GLFW error (if any) and clear the error flag
    //$$ MemoryStack.stackPush().use { stack ->
    //$$     val p = stack.mallocPointer(1)
    //$$     val error = GLFW.glfwGetError(p)
    //$$     if (error != GLFW.GLFW_NO_ERROR) {
    //$$         Essential.logger.warn("GLFW Error {}: {}", error, MemoryUtil.memUTF8Safe(p.get(0)))
    //$$     }
    //$$ }
    //$$ asyncErrored = true
    //$$ return NotAsyncUploadBackend()
    //#else
    return AsyncUploadBackendImpl()
    //#endif
}

/**
 * A wrapper around a thread with an active OpenGL context.
 */
interface UploadBackend {
    /**
     * Submits a block to run on the context thread
     */
    fun submit(block: () -> Unit)

    /**
     * Cleans up the backend's resources, destroying the underlying context
     */
    fun cleanup()
}

abstract class AsyncUploadBackend : UploadBackend {
    private val singletonExecutor = Executors.newSingleThreadExecutor {
        Thread(it, "Screenshot uploader thread ${nextThreadNumber.getAndIncrement()}")
    }

    private var initialized = false

    abstract fun prepareContext()

    abstract fun destroyContext()

    private fun checkInitialized() {
        if (!initialized) {
            initialized = true
            singletonExecutor.execute { prepareContext() }
        }
    }

    override fun submit(block: () -> Unit) {
        checkInitialized()
        singletonExecutor.execute { block() }
    }

    override fun cleanup() {
        singletonExecutor.execute { destroyContext() }
        singletonExecutor.shutdown()
    }

    companion object {
        private val nextThreadNumber = AtomicInteger(1)
    }
}

//#if MC<11600
class AsyncUploadBackendImpl : AsyncUploadBackend() {

    private val drawable = SharedDrawable(Display.getDrawable())

    override fun prepareContext() {
        drawable.makeCurrent()
    }

    override fun destroyContext() {
        drawable.destroy()
    }

}
//#else
//$$ class AsyncUploadBackendImpl(private val window: Long) : AsyncUploadBackend() {
//$$
//$$     override fun prepareContext() {
//$$         GLFW.glfwMakeContextCurrent(window)
//$$         GL.createCapabilities()
//$$     }
//$$
//$$     override fun destroyContext() {
//$$        GLFW.glfwMakeContextCurrent(0L)
//$$        UMinecraft.getMinecraft().executor.execute {
//$$            GLFW.glfwDestroyWindow(window)
//$$        }
//$$    }
//$$
//$$ }
//#endif

class NotAsyncUploadBackend : UploadBackend {
    override fun submit(block: () -> Unit) {
        block()
    }

    override fun cleanup() {

    }
}

/**
 * Utility class for allowing a worker thread to upload textures off the main thread
 */
class AsyncTextureManager {
    private val uploadBackend = uploadBackendRefCounted.obtain { makeUploadBackend() }

    /**
     * Set of screenshot ids that have been uploaded since
     * the last call to [getFinished]
     */
    private val complete = mutableMapOf<ScreenshotId, PixelBufferTexture>()

    /**
     * Schedules the [texture] function to be called on a worker thread.
     */
    fun upload(id: ScreenshotId, texture: () -> PixelBufferTexture) {
        uploadBackend.submit {
            val pixelBufferTexture = texture()

            if (uploadBackend is AsyncUploadBackend) {
                GL11.glFlush()
            }

            UMinecraft.getMinecraft().executor.execute {
                synchronized(complete) {
                    complete[id] = pixelBufferTexture
                }
            }
        }
    }

    /**
     * Returns the list of ids that had their textures uploaded since the last call to getFinished()
     */
    fun getFinished(): Set<ScreenshotId> {
        //Clone the entries that are loaded
        return synchronized(complete) {
            complete.keys.toSet().also { complete.clear() }
        }
    }

    /**
     * Called to clean the context free the underlying resources
     */
    fun cleanup() {
        uploadBackend.submit {
            // In-progress uploads switch from the executor to the MC thread, so we need to follow them if we want
            // to make sure they're all done running.
            UMinecraft.getMinecraft().executor.execute {
                // and then we can clean up any unclaimed results
                synchronized(complete) {
                    complete.forEach { (_, texture) ->
                        texture.close()
                    }
                    complete.clear()
                }
            }
        }
        uploadBackendRefCounted.release { it.cleanup() }
    }

    companion object {
        private val uploadBackendRefCounted = RefCounted<UploadBackend>()
    }
}
