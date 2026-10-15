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
package gg.essential.network.cosmetics.cape

import gg.essential.Essential
import gg.essential.connectionmanager.common.packet.cosmetic.ClientCosmeticCheckoutPacket
import gg.essential.connectionmanager.common.packet.cosmetic.ServerCosmeticsUserUnlockedPacket
import gg.essential.connectionmanager.common.packet.cosmetic.capes.ClientCosmeticCapesUnlockedPacket
import gg.essential.connectionmanager.common.packet.response.ResponseActionPacket
import gg.essential.handlers.MinecraftGameProfileTexturesRefresher
import gg.essential.mod.cosmetics.CAPE_DISABLED_COSMETIC_ID
import gg.essential.mod.cosmetics.CosmeticSlot
import gg.essential.network.connectionmanager.ConnectionManager
import gg.essential.network.connectionmanager.cosmetics.CosmeticsManager
import gg.essential.network.mojang.ManagedMojangProfileApi
import gg.essential.network.mojang.MojangProfileApi
import gg.essential.util.Client
import gg.essential.util.USession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EnumPlayerModelParts


class CapeCosmeticsManager(
    private val connectionManager: ConnectionManager,
    private val cosmeticsManager: CosmeticsManager,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Client)

    private fun mojangProfileApi(): ManagedMojangProfileApi =
        ManagedMojangProfileApi.forUser(USession.activeNow().uuid)

    private var queued: Upload? = null

    private data class Upload(val api: ManagedMojangProfileApi, val hash: String?)

    fun queueCape(hash: String?) {
        queued = Upload(mojangProfileApi(), hash)
    }

    fun flushCapeUpdatesAsync() = coroutineScope.launch {
        flushCapeUpdates()
    }
    suspend fun flushCapeUpdates() = withContext(NonCancellable) { doFlushCapeUpdates() }
    private suspend fun doFlushCapeUpdates() {
        var cape: MojangProfileApi.Cape? = null

        try {
            val upload = queued.also { queued = null } ?: return

            val api = upload.api
            val cached = api.fetch()
            cape = cached.capes.find { it.hash == upload.hash }

            api.putCape(cape?.id) ?: return

            Essential.logger.info("Updated Mojang cape to \"${cape?.name ?: "<none>"}\"")
            MinecraftGameProfileTexturesRefresher.updateTextures()
        } catch (e: Throwable) {
            Essential.logger.error("Error enabling cape $cape at Mojang:", e)
        }
    }

    fun unlockMissingCapesAsync() {
        connectionManager.connectionScope.launch {
            try {
                unlockMissingCapes()
            } catch (e: Throwable) {
                Essential.logger.error("Error loading capes from Mojang:", e)
            }
        }
        if (CAPE_DISABLED_COSMETIC_ID !in cosmeticsManager.unlockedCosmetics.get()) {
            val capesVisible = Minecraft.getMinecraft().gameSettings.isPlayerModelPartEnabled(EnumPlayerModelParts.CAPE)
            connectionManager.send(ClientCosmeticCheckoutPacket(setOf(CAPE_DISABLED_COSMETIC_ID))) { maybeReply ->
                val reply = maybeReply.orElse(null)
                if (reply !is ServerCosmeticsUserUnlockedPacket && !(reply is ResponseActionPacket && reply.isSuccessful)) {
                    Essential.logger.error("Failed to unlock $CAPE_DISABLED_COSMETIC_ID ($maybeReply).")
                    return@send
                }

                val outfitManager = connectionManager.outfitManager
                // If they are new and currently have capes disabled, then we'll equip the Cape Disabled
                // cosmetic in their active outfit to keep it that way.
                if (!capesVisible) {
                    val selectedOutfit = outfitManager.getSelectedOutfit()
                    if (selectedOutfit != null) {
                        outfitManager.updateEquippedCosmetic(selectedOutfit.id, CosmeticSlot.CAPE, CAPE_DISABLED_COSMETIC_ID)
                    }
                }
            }
        }
    }

    private suspend fun unlockMissingCapes() {
        val api = mojangProfileApi()
        val profile = api.fetch()
        val capes = profile.capes
        val missing = capes.filter { it.hash !in cosmeticsManager.unlockedCosmetics.get() }
        if (missing.isEmpty()) {
            return // no capes yet to unlock, nothing to do
        }

        val signatures = api.obtainSignaturesForCapes(missing)

        val payload = signatures.associate { it.second.value to it.second.signature!! }
        connectionManager.send(ClientCosmeticCapesUnlockedPacket(payload)) { reply ->
            if ((reply.orElse(null) as ResponseActionPacket?)?.isSuccessful != true) {
                Essential.logger.warn("Backend failed to unlock capes ($reply):")
                for ((cape, proof) in signatures) {
                    Essential.logger.warn("  - ${cape.name}:")
                    Essential.logger.warn("      Id: ${cape.id}")
                    Essential.logger.warn("      Url: ${cape.url}")
                    Essential.logger.warn("      Proof of ownership: ${proof.value}")
                    Essential.logger.warn("      Signature: ${proof.signature}")
                }
            }
        }
    }

    //#if MC<11700
    private fun net.minecraft.client.settings.GameSettings.isPlayerModelPartEnabled(part: EnumPlayerModelParts): Boolean =
        part in modelParts
    //#endif
}