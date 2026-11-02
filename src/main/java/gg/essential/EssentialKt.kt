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
package gg.essential

import gg.essential.config.EssentialConfig
import gg.essential.util.takeOwnershipOfHarFileLoggerJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

abstract class EssentialKt {

    private val harFileLoggerJob = takeOwnershipOfHarFileLoggerJob()

    private var shutdownLateCalled = false
    init {
        // Our mixin will only be called on orderly shutdown; for crashes we still need a shutdown hook.
        Runtime.getRuntime().addShutdownHook(Thread {
            if (!shutdownLateCalled) {
                shutdownLate()
            }
        })
    }

    fun shutdownLate() {
        shutdownLateCalled = true
        try {
            doShutdownLate()
        } catch (t: Throwable) {
            LOGGER.error("Exception during late shutdown: ", t)
        }

    }

    /**
     * Called very late during shutdown.
     * Be careful what you access from here! A lot is likely already shut down.
     * Even the main game thread may no longer be running at this point.
     */
    private fun doShutdownLate() {
        runBlocking {
            harFileLoggerJob.cancelAndJoin()
        }

        EssentialConfig.save()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(EssentialKt::class.java)
    }
}
