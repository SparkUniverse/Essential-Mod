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
package gg.essential.gui.image

import gg.essential.config.LoadsResources
import gg.essential.elementa.components.UIImage
import gg.essential.elementa.components.inspector.Inspector
import gg.essential.elementa.utils.ResourceCache
import java.util.*

/**
 * An ImageFactory that returns images resulting from the specified [resource].
 * Utilizes a [ResourceCache] to decrease loading time
 */
class ResourceImageFactory @LoadsResources("%resource%") constructor(
    private val resource: String,
    preload: Boolean = true
) : ImageFactory() {

    init {
        if (preload) {
            synchronized(preloadQueue) {
                if (!preloadEnabled) {
                    preloadQueue.add(this)
                } else {
                    generate()
                }
            }
        }
    }

    override val name: String
        get() = resource

    override fun generate(): UIImage {
        return UIImage.ofResourceCached(resource, cache)
    }

    companion object {
        private var preloadEnabled = false
        private val preloadQueue = Collections.newSetFromMap<ResourceImageFactory>(WeakHashMap())

        fun preload() {
            synchronized(preloadQueue) {
                preloadEnabled = true
                for (factory in preloadQueue) {
                    factory.generate()
                }
                preloadQueue.clear()
            }
        }

        private val cache: ResourceCache = ResourceCache(Int.MAX_VALUE, javaClass)

        init {
            Inspector.registerComponentFactory(ResourceImageFactory::class.java)
        }
    }
}
