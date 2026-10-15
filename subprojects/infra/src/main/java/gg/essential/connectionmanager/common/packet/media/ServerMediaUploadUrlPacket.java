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
package gg.essential.connectionmanager.common.packet.media;

import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.lib.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ServerMediaUploadUrlPacket extends Packet {

    @SerializedName("media_id")
    private final @NotNull String mediaId;

    @SerializedName("upload_url")
    private final @NotNull String uploadUrl;

    @SerializedName("upload_headers")
    private final @Nullable Map<String, String> uploadHeaders;

    public ServerMediaUploadUrlPacket(
            final @NotNull String mediaId,
            final @NotNull String uploadUrl,
            final @Nullable Map<String, String> uploadHeaders
    ) {
        this.mediaId = mediaId;
        this.uploadUrl = uploadUrl;
        this.uploadHeaders = uploadHeaders;
    }

    public @NotNull String getMediaId() {
        return this.mediaId;
    }

    public @NotNull String getUploadUrl() {
        return this.uploadUrl;
    }

    public @Nullable Map<String, String> getUploadHeaders() {
        return this.uploadHeaders;
    }

}
