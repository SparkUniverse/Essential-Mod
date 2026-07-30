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
package gg.essential.gui.modals

import gg.essential.Essential
import gg.essential.gui.overlay.ModalFlow
import gg.essential.gui.common.modal.ConnectingModal
import gg.essential.gui.modals.McModalPrerequisites.connectionStatus
import gg.essential.network.connectionmanager.ConnectionManagerStatus
import gg.essential.network.connectionmanager.cosmetics.CosmeticsManager
import gg.essential.network.connectionmanager.features.Feature
import gg.essential.network.connectionmanager.suspension.suspensionModal
import gg.essential.universal.UMinecraft
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import kotlin.time.Duration.Companion.seconds

object McModalPrerequisites : ModalPrerequisites() {

    private val connectionStatus = Essential.getInstance().connectionManager.connectionStatus

    override suspend fun ModalFlow.doTermsOfServiceModal(): PrerequisiteResult {
        return if (connectionStatus.getUntracked() is ConnectionManagerStatus.TOSNotAccepted) {
            tosModal(connectionStatus).toResult()
        } else {
            PrerequisiteResult.PASS
        }
    }

    override suspend fun ModalFlow.doRequiredUpdateModal(): PrerequisiteResult {
        return if (connectionStatus.getUntracked() is ConnectionManagerStatus.Outdated) {
            updateRequiredModal()
            PrerequisiteResult.FAILURE
        } else {
            PrerequisiteResult.PASS
        }
    }

    override suspend fun ModalFlow.doSuspensionAndRuleManagerLoadedCheck(): PrerequisiteResult {
        val connectionManager = Essential.getInstance().connectionManager
        return if (!connectionManager.suspensionManager.isLoaded.getUntracked() || !connectionManager.rulesManager.isLoaded.getUntracked()) {
            awaitModal { continuation ->
                ConnectingModal(
                    modalManager,
                    title = "Connecting to Essential...",
                    isConnecting = { (connectionStatus() == ConnectionManagerStatus.Success && (!connectionManager.suspensionManager.isLoaded() || !connectionManager.rulesManager.isLoaded())) || connectionStatus() == null },
                    continuation = continuation
                )
            }.toResult()
        } else {
            PrerequisiteResult.PASS
        }
    }

    override suspend fun ModalFlow.doCosmeticsModal(): PrerequisiteResult {
        val cosmeticsLoaded = Essential.getInstance().connectionManager.cosmeticsManager.cosmeticsLoaded
        if (!cosmeticsLoaded.getUntracked()) {
            return cosmeticsLoadingModal(cosmeticsLoaded, CosmeticsManager.LOAD_TIMEOUT_SECONDS.seconds).toResult()
        }
        return PrerequisiteResult.PASS
    }

    override suspend fun ModalFlow.doConnectionStatusErrorModal(): PrerequisiteResult {
        // Checks all connection errors including authentication errors
        if (connectionStatus.getUntracked() is ConnectionManagerStatus.Error) {
            connectionManagerErrorModal(connectionStatus)
            return PrerequisiteResult.SUCCESS
        }
        return PrerequisiteResult.PASS
    }

    override suspend fun ModalFlow.doConnectingModal(): PrerequisiteResult {
        if (connectionStatus.getUntracked() == null) {
            val connectionManager = Essential.getInstance().connectionManager
            return awaitModal { continuation ->
                ConnectingModal(
                    modalManager,
                    title = "Connecting to Essential...",
                    isConnecting = { (connectionStatus() == ConnectionManagerStatus.Success && (!connectionManager.suspensionManager.isLoaded() || !connectionManager.rulesManager.isLoaded())) || connectionStatus() == null },
                    continuation = continuation
                )
            }.toResult()
        }
        return PrerequisiteResult.PASS
    }

    override suspend fun ModalFlow.doCommunityRulesModal(): PrerequisiteResult {
        val rulesManager = Essential.getInstance().connectionManager.rulesManager
        return if (rulesManager.hasRules.getUntracked() && !rulesManager.acceptedRules) {
            communityRulesModal(rulesManager, UMinecraft.getSettings().language).toResult()
        } else {
            PrerequisiteResult.PASS
        }
    }

    override suspend fun ModalFlow.doSocialSuspensionModal(): PrerequisiteResult {
        Essential.getInstance().connectionManager.suspensionManager.activeSuspension.getUntracked()?.let { suspension ->
            suspensionModal(suspension)
            return PrerequisiteResult.FAILURE
        }
        return PrerequisiteResult.PASS
    }

    override suspend fun ModalFlow.doPermanentSuspensionModal(): PrerequisiteResult {
        Essential.getInstance().connectionManager.suspensionManager.activeSuspension.getUntracked()?.let { suspension ->
            if (suspension.isPermanent) {
                suspensionModal(suspension)
                return PrerequisiteResult.FAILURE
            }
        }
        return PrerequisiteResult.PASS
    }

    override suspend fun ModalFlow.doFeatureDisabledModal(features: List<Feature>): PrerequisiteResult {
        for (feature in features) {
            if (platform.disabledFeaturesManager.isFeatureDisabled(feature)) {
                awaitModal<Nothing> {
                    when (feature) {
                        Feature.WARDROBE -> FeatureDisabledModal.wardrobe(modalManager)
                        Feature.SOCIAL -> FeatureDisabledModal.social(modalManager)
                        Feature.MEDIA -> FeatureDisabledModal.media(modalManager)
                        Feature.COSMETIC_PURCHASE,
                        Feature.COIN_BUNDLE_PURCHASE -> FeatureDisabledModal.store(modalManager)
                        Feature.WORLD_HOSTING -> FeatureDisabledModal.worldHosting(modalManager)
                    }
                }
            }
        }
        return PrerequisiteResult.PASS
    }
}