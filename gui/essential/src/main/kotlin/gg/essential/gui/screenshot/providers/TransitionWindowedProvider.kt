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

import gg.essential.gui.screenshot.ScreenshotId
import io.netty.util.ReferenceCountUtil.release

/**
 * This provider calls the  fallbackProvider once to receive all the items it already has loaded and caches those results.
 * This provider will call the primaryProvider every call and return its results with higher priority than the results from the fallbackProvider
 * Once the primaryProvider has all the items available, the fallbackProvider will be called with an empty list to clean up
 */
class TransitionWindowedProvider<T>(
    private val primaryProvider: WindowedProvider<T>,
    private var fallbackProvider: WindowedProvider<T>?,
) : WindowedProvider<T> {

    override var items: List<ScreenshotId>
        get() = primaryProvider.items
        set(value) {
            primaryProvider.items = value
            fallbackProvider?.items = value
        }

    private var knownFallbackItems: Set<ScreenshotId>? = null

    override fun provide(windows: List<WindowedProvider.Window>, optional: Set<ScreenshotId>): Map<ScreenshotId, T> {

        val fallbackProvider = fallbackProvider
        // Fallback provider has already been disposed of, just go straight to the primary one
            ?: return primaryProvider.provide(windows, optional)

        val primaryItems = primaryProvider.provide(windows, optional)

        //The ids we are expecting the primary provider to provide
        val expectedIds = windows.flatMap { items.slice(it.range) }


        //The primary provider has all the items available
        if (expectedIds.all { it in primaryItems || it in optional }) {

            //Have the fallback provider clean up all of its resources
            fallbackProvider.provide(emptyList(), emptySet())

            // Dispose of the provider provider (which will be freed by the above call)
            this.fallbackProvider = null
            this.knownFallbackItems = null

            return primaryItems
        } else {
            //Primary provider does not have everything we need so fallback on the old data with a lower priority than the current provider

            // But we don't want the fallback provider to start any new work, so we mark everything that isn't known to
            // already be available as optional (which will also allow the fallback provider to clean up anything we no
            // longer need).
            val ids = windows.flatMapTo(mutableSetOf()) { window ->
                window.range.asSequence().map { this.items[it] }
            }
            val fallbackOptional = knownFallbackItems?.let { ids - it } ?: optional

            val items = fallbackProvider.provide(windows, fallbackOptional).toMutableMap()

            // Store known-available items for the next call.
            // Optional items and items already available from the primary provider may be freed as well.
            knownFallbackItems = items.keys - optional - primaryItems.keys

            primaryItems.forEach { (key, value) -> release(items.put(key, value)) }

            return items
        }
    }

}