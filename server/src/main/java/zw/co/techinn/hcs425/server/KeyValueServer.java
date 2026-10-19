package zw.co.techinn.hcs425.server;

import zw.co.techinn.hcs425.shared.Operation;
import zw.co.techinn.hcs425.shared.Protocol;
import zw.co.techinn.hcs425.shared.ProtocolException;
import zw.co.techinn.hcs425.shared.Request;
import zw.co.techinn.hcs425.shared.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Multi-client TCP server. One connection is assigned to one bounded-pool worker. */
public final class KeyValueServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(KeyValueServer.class.getName());
    private final String host;
    private final int requestedPort;
    private final int workerCount;
    private final String nodeName;
    private final KeyValueStore store;
    private final ServerMetrics metrics = new ServerMetrics();
    private final ReplicationSink replicationSink;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong clientSequence = new AtomicLong();
    private final AtomicLong lastReplicatedSequence = new AtomicLong();
    private final ConcurrentMap<String, ClientConnectionInfo> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Socket> sockets = new ConcurrentHashMap<>();
    private final List<ServerLogEntry> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();
    private Thread acceptThread;
    private ThreadPoolExecutor workers;

    public KeyValueServer(String host, int port, int workerCount, String nodeName) {
        this(host, port, workerCount, nodeName, null, new KeyValueStore());
    }

    public KeyValueServer(String host, int port, int workerCount, String nodeName, ReplicationSink replicationSink) {
        this(host, port, workerCount, nodeName, replicationSink, new KeyValueStore());
    }

    public KeyValueServer(String host, int port, int workerCount, String nodeName,
                          ReplicationSink replicationSink, KeyValueStore store) {
        this.host = host;
        this.requestedPort = port;
        this.workerCount = Math.max(2, workerCount);
        this.nodeName = nodeName == null ? "node" : nodeName;
        this.replicationSink = replicationSink;
        this.store = store;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        ServerSocket socket = new ServerSocket(requestedPort, 128, java.net.InetAddress.getByName(host));
        socket.setReuseAddress(true);
        serverSocket.set(socket);
        workers = new ThreadPoolExecutor(workerCount, workerCount, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(workerCount * 4), namedFactory("hcs425-worker-"),
                new ThreadPoolExecutor.AbortPolicy());
        running.set(true);
        log("INFO", nodeName + " listening on " + host + ":" + port());
        acceptThread = new Thread(this::acceptLoop, "hcs425-acceptor-" + nodeName);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.get().accept();
                client.setSoTimeout(30_000);
                String clientId = "C-" + String.format("%04d", clientSequence.incrementAndGet());
                ClientConnectionInfo info = new ClientConnectionInfo(clientId, client.getRemoteSocketAddress().toString());
                clients.put(clientId, info);
                sockets.put(clientId, client);
                metrics.connectionOpened();
                log("INFO", clientId + " connected from " + info.remoteAddress());
                try { workers.execute(() -> handleClient(clientId, client, info)); }
                catch (RejectedExecutionException ex) {
                    log("WARN", clientId + " rejected: worker pool saturated");
                    safeClose(client);
                    clients.remove(clientId);
                    sockets.remove(clientId);
                    metrics.connectionClosed();
                }
            } catch (SocketException ex) {
                if (running.get()) log("ERROR", "accept failed: " + ex.getMessage());
            } catch (IOException ex) {
                if (running.get()) log("ERROR", "accept failed: " + ex.getMessage());
            }
        }
    }

    private void handleClient(String clientId, Socket socket, ClientConnectionInfo info) {
        metrics.workerStarted();
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                info.requestReceived();
                long started = System.nanoTime();
                Response response;
                try {
                    Request request = Protocol.parseRequest(line);
                    response = execute(request);
                    metrics.requestFinished(response.success(), System.nanoTime() - started);
                    log("INFO", clientId + " " + request.operation() + (request.key() == null ? "" : " " + request.key()));
                } catch (ProtocolException ex) {
                    response = Response.error(ex.getMessage());
                    metrics.requestFinished(false, System.nanoTime() - started);
                    log("WARN", clientId + " malformed request: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    response = Response.error("INTERNAL_ERROR");
                    metrics.requestFinished(false, System.nanoTime() - started);
                    LOGGER.log(Level.WARNING, "Request failed", ex);
                    log("ERROR", clientId + " processing error: " + ex.getMessage());
                }
                Protocol.write(writer, response);
            }
        } catch (SocketTimeoutException ex) {
            log("WARN", clientId + " timeout; connection closed");
        } catch (IOException ex) {
            log("WARN", clientId + " disconnected: " + ex.getMessage());
        } finally {
            sockets.remove(clientId);
            clients.remove(clientId);
            metrics.connectionClosed();
            metrics.workerFinished();
            log("INFO", clientId + " disconnected");
        }
    }

    private Response execute(Request request) {
        return switch (request.operation()) {
            case PUT -> {
                store.put(request.key(), request.value());
                if (replicationSink != null) replicationSink.onLocalWrite(request.key(), request.value());
                yield Response.ok("STORED");
            }
            case GET -> {
                String value = store.get(request.key());
                yield value == null ? Response.ok("NOT_FOUND") : Response.value(value);
            }
            case DELETE -> {
                String previous = store.delete(request.key());
                if (previous != null && replicationSink != null) replicationSink.onLocalWrite(request.key(), null);
                yield previous == null ? Response.ok("NOT_FOUND") : Response.ok("DELETED");
            }
            case LIST -> Response.list(store.snapshot());
            case PING -> Response.ok("PONG");
            case STATS -> Response.ok(statsLine());
            case REPLICATE -> applyReplication(request);
        };
    }

    private Response applyReplication(Request request) {
        long previous = lastReplicatedSequence.get();
        if (request.sequence() <= previous) return Response.ok("DUPLICATE");
        if (!lastReplicatedSequence.compareAndSet(previous, request.sequence())) return applyReplication(request);
        if (request.value() == null) store.delete(request.key()); else store.put(request.key(), request.value());
        log("INFO", "replication applied seq=" + request.sequence() + " key=" + request.key());
        return Response.ok("REPLICATED");
    }

    private String statsLine() {
        ServerMetrics.Snapshot s = metrics.snapshot();
        return String.format("STATS node=%s active=%d workers=%d accepted=%d requests=%d success=%d failed=%d avgLatencyMs=%.3f throughputOpsSec=%.3f storeKeys=%d",
                nodeName, s.activeConnections(), s.activeWorkers(), s.acceptedConnections(), s.totalRequests(),
                s.successfulRequests(), s.failedRequests(), s.averageLatencyMs(), s.throughputOpsSec(), store.size());
    }

    private void log(String level, String message) { logs.add(new ServerLogEntry(Instant.now(), level, message)); }

    private static java.util.concurrent.ThreadFactory namedFactory(String prefix) {
        AtomicLong seq = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public int port() { ServerSocket socket = serverSocket.get(); return socket == null ? requestedPort : socket.getLocalPort(); }
    public boolean isRunning() { return running.get(); }
    public KeyValueStore store() { return store; }
    public ServerMetrics.Snapshot metrics() { return metrics.snapshot(); }
    public List<ClientConnectionInfo> clients() { return List.copyOf(new ArrayList<>(clients.values())); }
    public List<ServerLogEntry> logs() { return List.copyOf(logs); }
    public String nodeName() { return nodeName; }

    @Override public synchronized void close() {
        if (!running.getAndSet(false)) return;
        log("INFO", nodeName + " shutting down");
        ServerSocket socket = serverSocket.getAndSet(null);
        if (socket != null) safeClose(socket);
        sockets.values().forEach(KeyValueServer::safeClose);
        if (workers != null) {
            workers.shutdownNow();
            try { workers.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        if (acceptThread != null) {
            try { acceptThread.join(500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
        clients.clear();
        sockets.clear();
    }

    private static void safeClose(java.io.Closeable closeable) {
        try { closeable.close(); } catch (IOException ignored) { }
    }
}
