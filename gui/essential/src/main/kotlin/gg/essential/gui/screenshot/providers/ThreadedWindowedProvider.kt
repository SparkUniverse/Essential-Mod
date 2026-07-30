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
import gg.essential.gui.screenshot.concurrent.PrioritizedCallable.Companion.withPriority
import io.netty.util.ReferenceCountUtil.release
import io.netty.util.ReferenceCountUtil.retain
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class ThreadedWindowedProvider<T : Any>(
    private val innerProvider: WindowedProvider<T>,
    private val threadPool: ExecutorService,
    private val providerPriority: Int,
) : WindowedProvider<T> {

    override var items: List<ScreenshotId>
        get() {
            return newItemsRequested ?: innerProvider.items
        }
        set(value) {
            // If we have no tasks running, we can set the inner items right away
            if (activeTasks.isEmpty() && canceledTasks.isEmpty()) {
                innerProvider.items = value
                newItemsRequested = null
            } else {
                newItemsRequested = value
            }
        }

    //The new ids the inner provider should be set to once all submitted tasks are completed
    private var newItemsRequested: List<ScreenshotId>? = null

    //Map to keep track of the tasks that are currently being worked on by the innerProvider
    private val activeTasks = mutableMapOf<ScreenshotId, Pair<CompletableFuture<Map<ScreenshotId, T>>, AtomicBoolean>>()

    // List containing canceled tasks which may still be worked on by the innerProvider, even if we no longer care
    // about their result.
    private val canceledTasks = mutableListOf<CompletableFuture<Void>>()

    //Cache of the items currently in scope
    private val cache = mutableMapOf<ScreenshotId, T>()

    override fun provide(windows: List<WindowedProvider.Window>, optional: Set<ScreenshotId>): Map<ScreenshotId, T> {
        //Synchronize calls on this method to ensure atomicity
        synchronized(this) {

            val newItemsRequested = this.newItemsRequested
            val items = newItemsRequested ?: items
            val requestedIds = windows.flatMapTo(mutableSetOf()) { window ->
                window.range.asSequence().map { items[it] }.filterNot { it in optional }
            }

            // Cancel tasks which we are no longer interested in (or all of them if `items` needs to change)
            activeTasks.entries.removeIf { (id, entry) ->
                if (id !in requestedIds || newItemsRequested != null) {
                    val (future, canceled) = entry
                    // We no longer care about this task, cancel it
                    canceled.set(true)
                    // and add it to the list of canceled tasks (we still need to wait for it to finish before we can
                    // update `items`)
                    canceledTasks.add(future.thenAccept { result ->
                        // and clean up its results, if it produced any
                        result.values.forEach(::release)
                    })
                    // and remove it from the active tasks
                    true
                } else {
                    false
                }
            }

            // Clean up canceled tasks which are no longer running
            canceledTasks.removeIf { it.isDone }

            // Process task results
            activeTasks.values.removeIf { (future, _) ->
                val result = future.getNow(null) ?: return@removeIf false
                result.forEach { (key, value) ->
                    release(cache.put(key, value))
                }
                return@removeIf true
            }

            // Schedule new tasks (but only if we don't need to update `items`)
            if (newItemsRequested == null) {

                for ((windowIndex, window) in windows.withIndex()) {
                    for (index in window.range.reversed(window.backwards)) {

                        val id = items[index]

                        if (id in cache || id in activeTasks || id in optional) {
                            continue
                        }

                        val isCanceled = AtomicBoolean(false)
                        val future = CompletableFuture.supplyAsync({
                            if (!isCanceled.get()) {
                                innerProvider.provide(index.toSingleWindowRequest(), emptySet())
                            } else {
                                emptyMap()
                            }
                        }, threadPool.withPriority(windowIndex, providerPriority, index))

                        activeTasks[id] = Pair(future, isCanceled)
                    }
                }

            } else {

                // If we need to update our items list, we need to wait for all tasks to exit
                if (activeTasks.isEmpty() && canceledTasks.isEmpty()) {
                    this.items = newItemsRequested
                }
            }

            //Delete unused entries
            cache.entries.removeIf {
                if (it.key !in requestedIds) {
                    release(it.value)
                    true
                } else {
                    false
                }
            }

            return cache.mapValues { retain(it.value) }
        }
    }
}