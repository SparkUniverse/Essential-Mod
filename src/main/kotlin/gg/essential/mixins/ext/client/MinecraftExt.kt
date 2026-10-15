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
package gg.essential.mixins.ext.client

import kotlinx.coroutines.MainCoroutineDispatcher
import net.minecraft.client.Minecraft
import java.util.concurrent.Executor

interface MinecraftExt {
    /**
     * Returns an executor for queuing tasks to run on the main thread.
     * This executor has a few special properties which the default MC method for scheduling work does not fulfill:
     * - Persistent: In 1.14+ the MC executor queue is cleared when you disconnect from the server. This executor does
     *               not just drop any of its work.
     * - "Lock-free": (not the standard meaning)
     *                Prior to 1.14 the MC executor would synchronize on the entire queue while draining/executing it,
     *                but it also synchronizes on the queue to add new items. This gives potential for deadlocks if any
     *                task queued needs to wait for an async task to finish but the async task is itself trying to add
     *                new items to the queue which, since we're currently executing a task, is locked.
     * - Direct: If a task is submitted on the main thread, it is ran immediately, not added to the end of the queue.
     *           This behavior is identical to that of MC but explicitly mentioned as it is not generally common for
     *           queue-style executors. In particular, this means that task ordering is only guaranteed to be consistent
     *           on the same thread, not between different threads, even when synchronized externally (though this
     *           extreme case only applies if one of the thread is the main thread).
     * The tasks submitted to this executor are ran immediately before MC drains its task queue.
     */
    val `essential$executor`: Executor

    /**
     * Standard [MainCoroutineDispatcher] for the main game thread.
     * Available as [Dispatchers.Game].
     *
     * Called in exactly the same places as the above executor (in fact, the above executor is a wrapper around the
     * [MainCoroutineDispatcher.immediate] of this dispatcher).
     */
    val `essential$dispatcher`: MainCoroutineDispatcher
}

val MinecraftExt.executor get() = `essential$executor`
val Minecraft.ext get() = this as MinecraftExt
