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
package gg.essential.network.connectionmanager.notices

import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.gui.elementa.state.v2.collections.effectOnChange
import gg.essential.gui.elementa.state.v2.filter
import gg.essential.gui.elementa.state.v2.mapEach
import gg.essential.gui.elementa.state.v2.mapList
import gg.essential.gui.elementa.state.v2.memo
import gg.essential.gui.wardrobe.components.showGiftReceivedToast
import gg.essential.network.connectionmanager.cosmetics.CosmeticsData
import gg.essential.network.cosmetics.Cosmetic
import gg.essential.notices.NoticeType
import gg.essential.util.UuidNameLookup
import java.util.UUID

class GiftedCosmeticNoticeListener(
    private val noticesManager: NoticesManager,
    private val cosmeticsData: CosmeticsData,
) {
    private val notices = noticesManager.activeNotices.filter { it.type == NoticeType.GIFTED_COSMETIC_TOAST }
        .mapEach { notice ->
            val byUuid = UUID.fromString(notice.metadata["gifted_by_id"] as String)
            val byName = UuidNameLookup.nameState(byUuid, null)
            val cosmeticId = notice.metadata["cosmetic_id"] as String
            val cosmetic = cosmeticsData.cosmetic(cosmeticId)
            memo {
                ResolvedNotice(
                    notice.id,
                    byUuid,
                    byName() ?: return@memo null,
                    cosmetic() ?: return@memo null,
                )
            }
        }
        .mapList { list -> list.mapNotNull { it() } }

    private class ResolvedNotice(
        val id: String,
        val byUuid: UUID,
        val byName: String,
        val cosmetic: Cosmetic,
    )

    fun hookUp(refHolder: ReferenceHolder) {
        notices.effectOnChange(refHolder, add = { (_, notice) ->
            showGiftReceivedToast(notice.cosmetic, notice.byUuid, notice.byName)
            noticesManager.dismissNotice(notice.id)
        }, remove = {})
    }
}