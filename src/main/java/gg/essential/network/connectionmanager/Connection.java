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
package gg.essential.network.connectionmanager;

import com.google.common.primitives.Bytes;
import gg.essential.Essential;
import gg.essential.connectionmanager.common.packet.Packet;
import gg.essential.connectionmanager.common.packet.connection.ConnectionKeepAlivePacket;
import gg.essential.data.VersionData;
import gg.essential.handlers.CertChain;
import gg.essential.network.connectionmanager.ConnectionManagerKt.CloseInfo;
import gg.essential.network.connectionmanager.legacyjre.LegacyJre;
import gg.essential.network.connectionmanager.legacyjre.LegacyJreDnsResolver;
import gg.essential.network.connectionmanager.legacyjre.LegacyJreSocketFactory;
import gg.essential.util.LimitedExecutor;
import gg.essential.util.MagicPathsKt;
import gg.essential.util.Multithreading;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Pair;
import org.java_websocket.client.DnsResolver;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class Connection extends WebSocketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connection.class);

    private static final Lazy<Function<String, SSLSocketFactory>> SSL_SOCKET_FACTORY_FACTORY = LazyKt.lazy(() -> {
        try {
            SSLSocketFactory factory = new CertChain()
                .loadEmbedded()
                .done()
                .getFirst()
                .getSocketFactory();

            if (LegacyJre.IS_LEGACY_JRE_51 || LegacyJre.IS_LEGACY_JRE_74) {
                Essential.logger.info("Using LegacyJreSocketFactory");
                return host -> new LegacyJreSocketFactory(factory, host);
            } else {
                Essential.logger.info("Using Default JreSocketFactory");
                return host -> factory;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private static final Lazy<DnsResolver> DNS_RESOLVER = LazyKt.lazy(() -> {
        if (LegacyJre.IS_LEGACY_JRE_51) {
            Essential.logger.info("Using LegacyJreDnsResolver");
            return new LegacyJreDnsResolver();
        } else {
            Essential.logger.info("Using Default JreDnsResolver");
            return uri -> InetAddress.getByName(uri.getHost());
        }
    });

    private static URI CM_HOST_URI;
    static {
        String defaultUri = "wss://connect.essential.gg/v1";
        URI uri = URI.create(
            System.getProperty(
                "essential.cm.host",
                System.getenv().getOrDefault("ESSENTIAL_CM_HOST", defaultUri)
            )
        );
        String[] knownHosts = {".essential.gg",".modcore.dev"};
        boolean schemeGood = uri.getScheme().equals("wss");
        boolean hostGood = Arrays.stream(knownHosts).anyMatch(uri.getHost()::endsWith);
        if (!schemeGood || !hostGood) {
            Essential.logger.error("Potentially insecure ESSENTIAL_CM_HOST found, using default instead");
            uri = URI.create(defaultUri);
        }
        CM_HOST_URI = uri;
    }

    private static Pair<UUID, String> latestReconnectToken;

    @NotNull
    private final Executor sendExecutor = new LimitedExecutor(Multithreading.getPool(), 1, new ConcurrentLinkedQueue<>());

    //
    @NotNull
    private final Callbacks callbacks;
    private final ConnectionCodec codec = new ConnectionCodec(this::log);
    private UUID user;

    private int usingProtocol = 1;
    private ScheduledFuture<?> timeoutTask;
    private Exception lastWebsocketException = null;

    private static final int MAX_PROTOCOL = (12);

    public Connection(@NotNull Callbacks callbacks) {
        super(CM_HOST_URI);

        this.callbacks = callbacks;

        this.setTcpNoDelay(true);
        this.setReuseAddr(true);
        this.setConnectionLostTimeout(0); // We have our own keep alive.
    }

    public int getUsingProtocol() {
        return usingProtocol;
    }

    public void close(@NotNull final CloseReason closeReason) {
        this.close(closeReason.getCode(), closeReason.name());
    }

    @Override
    public void onOpen(@NotNull final ServerHandshake serverHandshake) {
        this.usingProtocol = Integer.parseInt(serverHandshake.getFieldValue("Essential-Protocol-Version"));

        String reconnectToken = serverHandshake.getFieldValue("Essential-Authentication-Token");
        latestReconnectToken = reconnectToken != null ? new Pair<>(user, reconnectToken) : null;

        scheduleTimeout();

        log(out -> out.printf("{\"type\": \"OPEN\", \"version\": %d}\n", usingProtocol));

        this.callbacks.onOpen();
    }

    @Override
    public void onClosing(int code, @NotNull String reason, boolean remote) {
        onClosingOrClosed(code, reason, remote);
    }

    @Override
    public void onClose(final int code, @NotNull final String reason, final boolean remote) {
        onClosingOrClosed(code, reason, remote);
    }

    private void onClosingOrClosed(int code, @NotNull String reason, boolean remote) {
        ScheduledFuture<?> timeoutTask = this.timeoutTask;
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            this.timeoutTask = null;
        }

        KnownCloseReason knownReason;
        if (reason.contains("Invalid status code received: 410") || reason.contains("Invalid status code received: 404")) {
            knownReason = KnownCloseReason.OUTDATED;
        } else if (code == 4008) {
            knownReason = KnownCloseReason.SUSPENDED;
        } else if (this.lastWebsocketException instanceof UnknownHostException) {
            knownReason = KnownCloseReason.DNS_FAILED;
        } else {
            knownReason = null;
        }

        this.callbacks.onClose(new CloseInfo(code, reason, remote, knownReason));
        this.lastWebsocketException = null;
    }

    @Override
    public void close() {
        log(out -> {
            out.print("{\"type\": \"CLOSE\"}\n");
            out.close();
            logClosed = true;
        });

        super.close();
    }

    @Override
    public void onMessage(@NotNull final String message) {
        // Method is not support, I dislike the fact that this is a required method to have though.
    }

    // Debug is kept for the time being.
    @Override
    public void onMessage(@NotNull final ByteBuffer byteBuffer) {
        final Packet packet = codec.decode(byteBuffer.array());
        if (packet == null) {
            return;
        }
        this.onMessage(packet);
    }

    private void onMessage(final Packet packet) {
        if (packet instanceof ConnectionKeepAlivePacket) {
            scheduleTimeout();
            Packet response = new ConnectionKeepAlivePacket();
            response.setUniqueId(packet.getPacketUniqueId());
            this.send(response);
            return;
        }
        this.callbacks.onPacketAsync(packet);
    }

    @Override
    public void onError(@NotNull final Exception e) {
        Essential.logger.error("Critical error occurred on connection management. ", e);
        this.lastWebsocketException = e;
    }

    /**
     * Send a packet to the Connection Manager.
     *
     * @param packet           to send to the connection manager
     */
    public void send(@NotNull final Packet packet) {
        final Packet fakeReplyPacket = packet.getFakeReplyPacket();
        if (fakeReplyPacket != null) {
            fakeReplyPacket.setUniqueId(packet.getPacketUniqueId());
            Multithreading.scheduleOnBackgroundThread(() -> onMessage(fakeReplyPacket), packet.getFakeReplyDelayMs(), TimeUnit.MILLISECONDS);
            return;
        }

        sendExecutor.execute(() -> doSend(packet));
    }

    private void doSend(Packet packet) {
        codec.encode(packet, this::send);
    }

    public void setupAndConnect(UUID uuid, String userName, byte[] secret) {
        this.user = uuid;

        byte[] colon = ":".getBytes(StandardCharsets.UTF_8);
        byte[] name = userName.getBytes(StandardCharsets.UTF_8);
        byte[] nameSecret = Bytes.concat(name, colon, secret);
        String encoded = Base64.getEncoder().encodeToString(nameSecret);
        this.addHeader("Authorization", "Basic " + encoded);

        Pair<UUID, String> reconnectToken = latestReconnectToken;
        if (reconnectToken != null && reconnectToken.getFirst().equals(uuid)) {
            this.addHeader("Essential-Authentication-Token", reconnectToken.getSecond());
        }

        String protocolProperty = System.getProperty("essential.cm.protocolVersion");
        if (protocolProperty == null) {
            this.addHeader("Essential-Max-Protocol-Version", String.valueOf(MAX_PROTOCOL));
        } else {
            this.addHeader("Essential-Protocol-Version", protocolProperty);
        }

        this.addHeader("Essential-User-UUID", uuid.toString());
        this.addHeader("Essential-User-Name", userName);

        this.addHeader("Essential-Mod-Version", VersionData.INSTANCE.getEssentialVersion());
        this.addHeader("Essential-Mod-Commit", VersionData.INSTANCE.getEssentialCommit());
        this.addHeader("Essential-Mod-Branch", VersionData.INSTANCE.getEssentialBranch());
        this.addHeader("Essential-Minecraft-Version", VersionData.INSTANCE.getMinecraftVersion());
        this.addHeader("Essential-Minecraft-Modloader", VersionData.INSTANCE.getMinecraftPlatform().name());

        // Attempt to connect.
        try {
            this.setDnsResolver(DNS_RESOLVER.getValue());
            if ("wss".equals(this.uri.getScheme())) {
                this.setSocketFactory(SSL_SOCKET_FACTORY_FACTORY.getValue().apply(this.uri.getHost()));
            }

            this.connect();
        } catch (final Exception e) {
            Essential.logger.error("Error when connecting to Essential ConnectionManager.", e);

            e.printStackTrace();
        }
    }

    private void scheduleTimeout() {
        ScheduledFuture<?> timeoutTask = this.timeoutTask;
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }

        this.timeoutTask = Multithreading.getScheduledPool().schedule(
            () -> this.close(CloseReason.SERVER_KEEP_ALIVE_TIMEOUT),
            60L, TimeUnit.SECONDS);
    }

    private static final int MAX_LOGS = 10;
    private final Executor loggingExecutor = new LimitedExecutor(Multithreading.getPool(), 1, new ConcurrentLinkedQueue<>());
    private PrintStream logOut;
    private boolean logClosed;

    private void log(IOConsumer<PrintStream> consumer) {
        loggingExecutor.execute(() -> logSync(consumer));
    }

    private void logSync(IOConsumer<PrintStream> consumer) {
        if (logClosed) return;

        if (logOut == null) {
            Path folder = MagicPathsKt.getGlobalEssentialDirectory().resolve("infra-logs");

            // Cleanup/compress existing files
            cleanupLogs(folder);

            // Create new file
            Path file = folder.resolve(
                    Instant.now().toString().replace(':', '_') // windows doesn't like colons in filenames
                            + ".log");
            try {
                Files.createDirectories(folder);
                logOut = new PrintStream(Files.newOutputStream(file));
            } catch (IOException e) {
                LOGGER.error("Failed to create connection log file {}", file, e);
                return;
            }
        }

        try {
            consumer.accept(logOut);
        } catch (IOException e) {
            LOGGER.error("Failed to write to connection log file", e);
        }
    }

    private static void cleanupLogs(Path folder) {
        List<Path> files;
        try (Stream<Path> stream = Files.exists(folder) ? Files.list(folder) : Stream.empty()) {
            files = stream.sorted(Comparator.<Path, FileTime>comparing(file -> {
                try {
                    return Files.getLastModifiedTime(file);
                } catch (IOException e) {
                    LOGGER.warn("Failed to get last modified time of {}", file, e);
                    return FileTime.from(Instant.EPOCH);
                }
            }).reversed()).collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("Failed to list files in {}", folder, e);
            return;
        }
        int count = 0;
        for (Path file : files) {
            if (count >= MAX_LOGS) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete {}", file, e);
                    continue;
                }
            } else if (file.getFileName().toString().endsWith(".log")) {
                Path gzFile = file.resolveSibling(file.getFileName() + ".gz");
                try (OutputStream out = Files.newOutputStream(gzFile);
                     GZIPOutputStream gzOut = new GZIPOutputStream(out)) {
                    Files.copy(file, gzOut);
                } catch (IOException e) {
                    LOGGER.warn("Failed to write compressed file {}", gzFile, e);
                    throw new RuntimeException(e);
                }
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete {}", file, e);
                    continue;
                }
            }
            count++;
        }
    }

    interface IOConsumer<T> {
        void accept(T value) throws IOException;
    }

    interface Callbacks {
        void onOpen();
        void onPacketAsync(Packet packet);
        void onClose(CloseInfo info);
    }

    public enum KnownCloseReason {

        OUTDATED,
        SUSPENDED,
        DNS_FAILED,
    }
}
