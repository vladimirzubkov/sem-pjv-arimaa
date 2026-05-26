package cz.cvut.fel.pjv.arimaa.network;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP host/client session: NDJSON over UTF-8, host-authoritative {@link cz.cvut.fel.pjv.arimaa.persistence.GameSerializer}
 * snapshots, Silver-side {@link WireMessages.IntentMessage} forwarding.
 */
public final class ArimaaNetworkCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ArimaaNetworkCoordinator.class);

    private final NetworkGameBridge bridge;
    private final FxExecutor fx;
    private final ExecutorService ioPool =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "arimaa-network");
                        t.setDaemon(true);
                        return t;
                    });
    private final ScheduledExecutorService pingScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "arimaa-net-ping");
                        t.setDaemon(true);
                        return t;
                    });

    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private volatile Socket peerSocket;
    private volatile PrintWriter peerOut;
    private volatile NetworkRole role = NetworkRole.NONE;
    private volatile ServerSocket acceptingServerSocket;
    private volatile Future<?> sessionTask;
    private volatile ScheduledFuture<?> pingFuture;
    private final Object writeMonitor = new Object();
    /**
     * While {@code false}, the host must not push {@code state_snapshot} lines — otherwise FX-thread
     * {@code startNewGameAfterNetworkHostReady()} can emit a snapshot before the IO thread sends {@code welcome}, and the client
     * would read {@code state_snapshot} first ({@code expected welcome, got state_snapshot}).
     */
    private volatile boolean hostHandshakeComplete;

    /** Bridges UI/game mutations to the wire; all socket IO runs off the JavaFX thread via {@link FxExecutor}. */
    public ArimaaNetworkCoordinator(NetworkGameBridge bridge, FxExecutor fx) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    public NetworkRole getRole() {
        return role;
    }

    public boolean isActive() {
        return role != NetworkRole.NONE;
    }

    public boolean isHost() {
        return role == NetworkRole.HOST;
    }

    /**
     * {@code true} while the host session has not yet finished sending the first {@code welcome} (listening on
     * {@link ServerSocket#accept()}, reading {@code hello}, or running JavaFX setup). The user may cancel this phase
     * via {@link #stopSession()} to close the listener without an active game.
     */
    public boolean isHostBeforeWelcomeDone() {
        return role == NetworkRole.HOST && !hostHandshakeComplete;
    }

    /**
     * Binds TCP host: accept, read {@code hello}, prepare game on FX thread, send {@code welcome} + first snapshot, then
     * read Silver intents. Invoked from the host dialog.
     */
    public void startHost(int port) {
        stopSocketsAndTasks();
        hostHandshakeComplete = false;
        role = NetworkRole.HOST;
        stopped.set(false);
        sessionTask =
                ioPool.submit(
                        () -> {
                            ServerSocket listen = null;
                            try {
                                listen = new ServerSocket(port);
                                acceptingServerSocket = listen;
                                log.info("network host listening on TCP port {}", port);
                                fx.runOnUiThread(
                                        () -> bridge.setStatus("Síť — čekám na protihráče na portu %d…".formatted(port)));
                                Socket s = listen.accept();
                                configureSocket(s);
                                log.info("network host accepted peer {}", s.getRemoteSocketAddress());
                                peerSocket = s;
                                peerOut =
                                        new PrintWriter(
                                                new java.io.OutputStreamWriter(
                                                        s.getOutputStream(), StandardCharsets.UTF_8),
                                                true);
                                BufferedReader in =
                                        new BufferedReader(
                                                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                                String first = in.readLine();
                                if (first == null) {
                                    throw new IOException("Klient ukončil spojení před odesláním hello.");
                                }
                                log.info("network host recv (handshake): {}", truncateForLog(first, 400));
                                var parsed = NetworkJson.parseLine(first);
                                if (!"hello".equals(parsed.type())) {
                                    throw new IOException(
                                            "Očekáván typ „hello“, přišlo: „%s“. Úvod řádku: %s"
                                                    .formatted(parsed.type(), truncateForLog(first, 200)));
                                }
                                var hello = NetworkJson.readHello(parsed.node());
                                if (hello.protocolVersion() != NetworkJson.PROTOCOL_VERSION) {
                                    log.warn(
                                            "network host rejecting hello: protocolVersion {} (need {})",
                                            hello.protocolVersion(),
                                            NetworkJson.PROTOCOL_VERSION);
                                    synchronized (writeMonitor) {
                                        peerOut.println(
                                                NetworkJson.errorLine(
                                                        "Nepodporovaná verze protokolu: %d"
                                                                .formatted(hello.protocolVersion())));
                                    }
                                    return;
                                }
                                final PlayerControllerKind peerSilver;
                                try {
                                    peerSilver = NetworkAssignmentCodec.decode(hello.silverSeatControl());
                                } catch (IllegalArgumentException ex) {
                                    log.warn("network host rejecting hello: bad silverSeatControl", ex);
                                    synchronized (writeMonitor) {
                                        peerOut.println(
                                                NetworkJson.errorLine(
                                                        "Neplatné silverSeatControl: %s".formatted(ex.getMessage())));
                                    }
                                    return;
                                }
                                log.info(
                                        "network host preparing game on JavaFX thread (protocol {}, peer Silver: {})",
                                        NetworkJson.PROTOCOL_VERSION,
                                        peerSilver);
                                CountDownLatch latch = new CountDownLatch(1);
                                fx.runOnUiThread(
                                        () -> {
                                            try {
                                                bridge.prepareNetworkSessionAsHost(peerSilver);
                                                bridge.startNewGameAfterNetworkHostReady();
                                            } finally {
                                                latch.countDown();
                                            }
                                        });
                                latch.await();
                                String goldWire = NetworkAssignmentCodec.encode(bridge.getGoldPlayerKind());
                                String snap = bridge.buildNetworkSnapshotSaveText();
                                synchronized (writeMonitor) {
                                    String welcomeJson =
                                            NetworkJson.welcomeLine(
                                                    NetworkJson.PROTOCOL_VERSION, PlayerSide.SILVER, goldWire);
                                    String snapJson = NetworkJson.snapshotLine(snap);
                                    peerOut.println(welcomeJson);
                                    peerOut.println(snapJson);
                                    hostHandshakeComplete = true;
                                    log.info(
                                            "network host sent welcome (Gold seat {}) then state_snapshot ({} chars)",
                                            goldWire,
                                            snap.length());
                                    log.debug("network host welcome line: {}", truncateForLog(welcomeJson, 500));
                                }
                                startHostPingLoop();
                                readHostLoop(in);
                            } catch (Exception ex) {
                                log.warn("network host session failed", ex);
                                fx.runOnUiThread(
                                        () -> {
                                            bridge.setStatus("Síť — hostování selhalo: %s".formatted(ex.getMessage()));
                                            bridge.clearNetworkSessionAfterDisconnect();
                                        });
                            } finally {
                                if (listen != null) {
                                    try {
                                        listen.close();
                                    } catch (IOException ignored) {
                                    }
                                }
                                acceptingServerSocket = null;
                                stopSocketsAndTasks();
                                fx.runOnUiThread(bridge::clearNetworkSessionAfterDisconnect);
                            }
                        });
    }

    /**
     * Silver client: TCP connect, send {@code hello}, read {@code welcome} + snapshot on FX, then process host lines.
     */
    public void startClient(String host, int port) {
        stopSocketsAndTasks();
        role = NetworkRole.CLIENT;
        stopped.set(false);
        sessionTask =
                ioPool.submit(
                        () -> {
                            try {
                                log.info("network client connecting to {}:{} …", host, port);
                                Socket s = new Socket(host, port);
                                configureSocket(s);
                                log.info(
                                        "network client TCP connected (local {} → remote {})",
                                        s.getLocalSocketAddress(),
                                        s.getRemoteSocketAddress());
                                peerSocket = s;
                                peerOut =
                                        new PrintWriter(
                                                new java.io.OutputStreamWriter(
                                                        s.getOutputStream(), StandardCharsets.UTF_8),
                                                true);
                                String silverWire = NetworkAssignmentCodec.encode(bridge.getSilverPlayerKind());
                                String helloLine = NetworkJson.helloLine(NetworkJson.PROTOCOL_VERSION, silverWire);
                                synchronized (writeMonitor) {
                                    peerOut.println(helloLine);
                                }
                                log.info(
                                        "network client sent hello (protocol {}, Silver seat {})",
                                        NetworkJson.PROTOCOL_VERSION,
                                        silverWire);
                                log.debug("network client hello line: {}", truncateForLog(helloLine, 500));
                                BufferedReader in =
                                        new BufferedReader(
                                                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                                String welcomeLine = in.readLine();
                                if (welcomeLine == null) {
                                    throw new IOException("Server ukončil spojení před řádkem „welcome“.");
                                }
                                log.info("network client recv (handshake): {}", truncateForLog(welcomeLine, 400));
                                var w = NetworkJson.parseLine(welcomeLine);
                                if (!"welcome".equals(w.type())) {
                                    if ("error".equals(w.type())) {
                                        var err = NetworkJson.readError(w.node());
                                        log.warn("network client recv error from server: {}", err.message());
                                        throw new IOException("Server: %s".formatted(err.message()));
                                    }
                                    if ("state_snapshot".equals(w.type())) {
                                        log.error(
                                                "network client: server poslal nejdřív „state_snapshot“ místo „welcome“ — zkontrolujte verzi aplikace na hostiteli (handshake). Úryvek: {}",
                                                truncateForLog(welcomeLine, 300));
                                    }
                                    throw new IOException(
                                            "Očekáván řádek „welcome“, přišel typ „%s“. Úvod: %s"
                                                    .formatted(w.type(), truncateForLog(welcomeLine, 220)));
                                }
                                WireMessages.WelcomeMessage welcome = NetworkJson.readWelcome(w.node());
                                if (welcome.protocolVersion() != NetworkJson.PROTOCOL_VERSION) {
                                    throw new IOException(
                                            "Nepodporovaná verze protokolu na serveru: %d (klient %d)."
                                                    .formatted(welcome.protocolVersion(), NetworkJson.PROTOCOL_VERSION));
                                }
                                final PlayerControllerKind peerGold;
                                try {
                                    peerGold = NetworkAssignmentCodec.decode(welcome.goldSeatControl());
                                } catch (IllegalArgumentException ex) {
                                    throw new IOException("Neplatné goldSeatControl ze serveru.", ex);
                                }
                                log.info("network client applying welcome (peer Gold: {})", peerGold);
                                CountDownLatch latch = new CountDownLatch(1);
                                fx.runOnUiThread(
                                        () -> {
                                            try {
                                                bridge.prepareNetworkSessionAsClient(peerGold);
                                            } finally {
                                                latch.countDown();
                                            }
                                        });
                                latch.await();
                                readClientLoop(in);
                            } catch (Exception ex) {
                                log.warn("network client session failed", ex);
                                fx.runOnUiThread(
                                        () -> {
                                            String hint =
                                                    ex instanceof java.net.ConnectException
                                                            ? " Zkontrolujte IPv4 hostitele (řádek 192.168.x.x z dialogu Hostovat) a firewall."
                                                            : "";
                                            bridge.setStatus(
                                                    "Síť — připojení selhalo: %s.%s"
                                                            .formatted(ex.getMessage(), hint));
                                            bridge.clearNetworkSessionAfterDisconnect();
                                        });
                            } finally {
                                stopSocketsAndTasks();
                                fx.runOnUiThread(bridge::clearNetworkSessionAfterDisconnect);
                            }
                        });
    }

    /* Host IO thread: applies Silver intents and seat updates from the client line stream. */
    private void readHostLoop(BufferedReader in) throws IOException {
        while (!stopped.get()) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            NetworkJson.ParsedLine p;
            try {
                p = NetworkJson.parseLine(line);
            } catch (IOException ex) {
                log.debug("bad json line", ex);
                continue;
            }
            switch (p.type()) {
                case "intent" -> {
                    WireMessages.IntentMessage intent = NetworkJson.readIntent(p.node());
                    log.debug("network host recv intent: {}", intent.kind());
                    fx.runOnUiThread(
                            () -> {
                                if (stopped.get()) {
                                    return;
                                }
                                boolean ok = bridge.applyHostIntentFromNetwork(intent);
                                if (!ok) {
                                    log.info("network host rejected intent: {}", intent.kind());
                                    sendLine(NetworkJson.errorLine("Neplatný záměr nebo fáze hry."));
                                }
                            });
                }
                case "pong" -> { /* ignore */ }
                case "seat_control" -> {
                    WireMessages.SeatControlMessage sc = NetworkJson.readSeatControl(p.node());
                    fx.runOnUiThread(
                            () -> {
                                if (stopped.get()) {
                                    return;
                                }
                                bridge.applyNetworkPeerSilverSeatFromWire(sc.seatControl());
                            });
                }
                case "bye" -> stopped.set(true);
                default -> log.debug("host ignoring wire type {}", p.type());
            }
        }
    }

    /* Client IO thread: applies host snapshots, errors, seat updates; answers {@code ping} with {@code pong}. */
    private void readClientLoop(BufferedReader in) throws IOException {
        while (!stopped.get()) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            NetworkJson.ParsedLine p;
            try {
                p = NetworkJson.parseLine(line);
            } catch (IOException ex) {
                log.debug("bad json line", ex);
                continue;
            }
            switch (p.type()) {
                case "state_snapshot" -> {
                    WireMessages.StateSnapshotMessage snap = NetworkJson.readSnapshot(p.node());
                    log.debug("network client recv state_snapshot ({} chars)", snap.saveText().length());
                    fx.runOnUiThread(
                            () -> {
                                if (stopped.get()) {
                                    return;
                                }
                                bridge.applyNetworkSnapshotSaveText(snap.saveText());
                            });
                }
                case "error" -> {
                    WireMessages.ErrorMessage err = NetworkJson.readError(p.node());
                    fx.runOnUiThread(
                            () -> {
                                bridge.clearNetworkClientAwaitingHostSync();
                                bridge.setStatus("Síť — chyba: %s".formatted(err.message()));
                            });
                }
                case "ping" -> sendLine(NetworkJson.pongLine());
                case "seat_control" -> {
                    WireMessages.SeatControlMessage sc = NetworkJson.readSeatControl(p.node());
                    fx.runOnUiThread(
                            () -> {
                                if (stopped.get()) {
                                    return;
                                }
                                bridge.applyNetworkPeerGoldSeatFromWire(sc.seatControl());
                            });
                }
                case "bye" -> stopped.set(true);
                default -> log.debug("client ignoring wire type {}", p.type());
            }
        }
        if (!stopped.get()) {
            fx.runOnUiThread(
                    () -> {
                        bridge.setStatus("Síť — spojení ukončeno.");
                        bridge.clearNetworkSessionAfterDisconnect();
                    });
        }
    }

    /* Periodic {@code ping} so dead TCP sessions are detected while the match is open. */
    private void startHostPingLoop() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
        }
        pingFuture =
                pingScheduler.scheduleAtFixedRate(
                        () -> {
                            if (stopped.get() || peerOut == null) {
                                return;
                            }
                            sendLine(NetworkJson.pingLine());
                        },
                        25,
                        25,
                        TimeUnit.SECONDS);
    }

    /** Sends a Silver-side gameplay intent to the host (client role only). */
    public void sendIntent(WireMessages.IntentMessage intent) {
        if (peerOut == null || role != NetworkRole.CLIENT) {
            return;
        }
        synchronized (writeMonitor) {
            peerOut.println(NetworkJson.intentLine(intent));
        }
    }

    /** Host notifies client that Gold seat control changed (human vs CPU level). */
    public void sendSeatControlFromHost(String goldSeatWire) {
        if (peerOut == null || role != NetworkRole.HOST) {
            return;
        }
        sendLine(NetworkJson.seatControlLine(goldSeatWire));
    }

    /** Client notifies host that Silver seat control changed. */
    public void sendSeatControlFromClient(String silverSeatWire) {
        if (peerOut == null || role != NetworkRole.CLIENT) {
            return;
        }
        sendLine(NetworkJson.seatControlLine(silverSeatWire));
    }

    /** Thread-safe write of one NDJSON line to the peer (used for intents, snapshots, pings). */
    public void sendLine(String line) {
        PrintWriter w = peerOut;
        if (w == null) {
            return;
        }
        synchronized (writeMonitor) {
            w.println(line);
        }
    }

    /**
     * Pushes current save text to the client after model changes (host only; skipped until handshake + first snapshot).
     */
    public void broadcastSnapshotFromHostMainThread() {
        if (role != NetworkRole.HOST || peerOut == null) {
            return;
        }
        if (bridge.isApplyingNetworkSnapshot()) {
            return;
        }
        if (!hostHandshakeComplete) {
            log.debug("network host: držím state_snapshot — handshake ještě nedokončen (welcome + první snapshot)");
            return;
        }
        String text = bridge.buildNetworkSnapshotSaveText();
        synchronized (writeMonitor) {
            peerOut.println(NetworkJson.snapshotLine(text));
        }
        log.debug("network host sent state_snapshot ({} chars)", text.length());
    }

    /** Closes listener/socket, sends {@code bye}, and clears bridge state (user cancel or error). */
    public void stopSession() {
        stopped.set(true);
        ServerSocket acc = acceptingServerSocket;
        if (acc != null) {
            try {
                acc.close();
            } catch (IOException ignored) {
            }
        }
        sendLine(NetworkJson.byeLine());
        stopSocketsAndTasks();
        fx.runOnUiThread(bridge::clearNetworkSessionAfterDisconnect);
    }

    /* Low-latency mode for small JSON lines over localhost/LAN. */
    private void configureSocket(Socket s) throws IOException {
        s.setTcpNoDelay(true);
    }

    /* Shortens and sanitizes wire text for SLF4J (avoids huge snapshot lines in logs). */
    private static String truncateForLog(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ');
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, maxChars) + "…";
    }

    /** Closes sockets, cancels ping/session tasks, and resets role after a session ends. */
    private void stopSocketsAndTasks() {
        stopped.set(true);
        hostHandshakeComplete = false;
        role = NetworkRole.NONE;
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
        ServerSocket acc = acceptingServerSocket;
        if (acc != null) {
            try {
                acc.close();
            } catch (IOException ignored) {
            }
            acceptingServerSocket = null;
        }
        PrintWriter w = peerOut;
        peerOut = null;
        if (w != null) {
            w.close();
        }
        Socket s = peerSocket;
        peerSocket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        sessionTask = null;
    }
}
