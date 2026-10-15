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
package gg.essential.network.connectionmanager.cosmetics;

import com.google.common.collect.Maps;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.network.connectionmanager.AsyncResponseHandler;
import gg.essential.network.connectionmanager.ConnectionManager;
import gg.essential.network.connectionmanager.EarlyResponseHandler;
import gg.essential.network.connectionmanager.handler.PacketHandler;
import gg.essential.universal.UMinecraft;
import gg.essential.util.ExtensionsKt;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class PacketHandlers extends PacketHandlersKt {
    @NotNull
    private final Executor mainThreadExecutor = ExtensionsKt.getExecutor(UMinecraft.getMinecraft());

    @NotNull
    private final Map<Class<? extends Packet>, PacketHandler<?>> packetHandlers = Maps.newHashMap();

    public PacketHandlers(@NotNull CoroutineScope coroutineScope) {
        super(coroutineScope);
    }

    public <T extends Packet> void register(Class<T> cls, PacketHandler<T> handler) {
        this.packetHandlers.put(cls, handler);
    }

    public void handle(ConnectionManager connectionManager, Packet packet) {
        final Consumer<Optional<Packet>> fResponseHandler;
        final Consumer<Optional<Packet>> asyncResponseHandler;
        final PacketHandler packetHandler = this.packetHandlers.get(packet.getClass());

        // Retrieve the packetId and responseHandler for the incoming packet if need be.
        UUID packetId = packet.getPacketUniqueId();
        Consumer<Optional<Packet>> responseHandler = null;
        if (packetId != null) {
            responseHandler = takePacketResponseHandler(packetId);
        }
        if (responseHandler instanceof AsyncResponseHandler) {
            fResponseHandler = null;
            asyncResponseHandler = responseHandler;
        } else {
            fResponseHandler = responseHandler;
            asyncResponseHandler = null;
        }

        if (packetHandler == null && responseHandler == null) {
            return;
        }

        if (asyncResponseHandler != null) {
            try {
                asyncResponseHandler.accept(Optional.of(packet));
            } catch (final Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        this.mainThreadExecutor.execute(() -> {
            // If the connection closed since we went from read thread -> main thread then throw out the packet.
            if (!connectionManager.isOpen()) {
                return;
            }

            Consumer<Optional<Packet>> responseHandlerSync = fResponseHandler;

            if (responseHandlerSync instanceof EarlyResponseHandler) {
                try {
                    responseHandlerSync.accept(Optional.of(packet));
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                }
                responseHandlerSync = null;
            }

            if (packetHandler != null) {
                try {
                    packetHandler.handle(packet);
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                }
            }

            if (responseHandlerSync != null) {
                try {
                    responseHandlerSync.accept(Optional.of(packet));
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        });
    }
}
