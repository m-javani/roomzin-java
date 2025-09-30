package com.roomzin.roomzinjava.internal.cluster;

import com.roomzin.roomzinjava.client.cluster.ClusterConfig;
import com.roomzin.roomzinjava.internal.protocol.Frame;
import com.roomzin.roomzinjava.internal.protocol.Login;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.NodeAddr;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ClusterHandler manages connections to a Roomzin cluster, routing write
 * requests to the leader and read requests to followers using round-robin.
 */
public class ClusterHandler {
    private final ClusterConfig config;
    private final DiscoveryMap discoveryMap;
    private final LeaderHandler leaderHandler;
    private final FollowersHandler followersHandler;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ConcurrentLinkedQueue<Request> requestPool = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BlockingQueue<ProtocolTypes.RawResult>> responseQueuePool = new ConcurrentLinkedQueue<>();

    public ClusterHandler(ClusterConfig config) {
        this.config = config;
        this.discoveryMap = buildDiscoveryMap(config);
        this.leaderHandler = new LeaderHandler(config, discoveryMap);
        this.followersHandler = new FollowersHandler(config, discoveryMap);
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.executor = Executors.newCachedThreadPool();

        // Pre-populate pools
        for (int i = 0; i < 50; i++) {
            requestPool.offer(new Request());
            responseQueuePool.offer(new ArrayBlockingQueue<>(1));
        }

        // Start discovery task if in HTTP mode
        if (config.getDiscoveryAddr() != null && !config.getDiscoveryAddr().isEmpty()) {
            startDiscoveryTask();
        }
    }

    /**
     * Builds the discovery map based on config (static or empty for HTTP mode)
     */
    private DiscoveryMap buildDiscoveryMap(ClusterConfig config) {
        DiscoveryMap dm = new DiscoveryMap();

        if (config.getDiscoveryAddr() != null && !config.getDiscoveryAddr().isEmpty()) {
            // HTTP mode: start empty, populated later by background task
            return dm;
        }

        // Static mode
        List<NodeAddr> staticDiscovery = config.getStaticDiscovery();
        if (staticDiscovery == null || staticDiscovery.isEmpty()) {
            throw new IllegalArgumentException("static discovery enabled but StaticDiscovery is empty");
        }

        dm.setStatic(staticDiscovery, config.getTcpPort(), config.getApiPort());
        return dm;
    }

    /**
     * Updates the discovery map with new nodes (HTTP mode)
     */
    private void updateDiscoveryMap(List<NodeAddr> nodes) {
        discoveryMap.update(nodes, config.getTcpPort(), config.getApiPort());
    }

    /**
     * Fetches external discovery service using Jackson
     */
    private List<NodeAddr> fetchExternalDiscovery() throws RoomzinException {
        String discoveryAddr = config.getDiscoveryAddr();
        if (discoveryAddr == null || discoveryAddr.isEmpty()) {
            throw RoomzinException.of("discovery address not configured");
        }

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(discoveryAddr))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw RoomzinException.of("discovery service returned status: " + response.statusCode());
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(response.body());
            com.fasterxml.jackson.databind.JsonNode nodesArray = json.get("nodes");

            if (nodesArray == null || !nodesArray.isArray() || nodesArray.size() == 0) {
                throw RoomzinException.of("discovery service returned empty node list");
            }

            List<NodeAddr> nodes = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode nodeJson : nodesArray) {
                String nodeId = nodeJson.has("node_id") ? nodeJson.get("node_id").asText() : "";
                String addr = nodeJson.has("addr") ? nodeJson.get("addr").asText() : "";
                int tcpPort = nodeJson.has("tcp_port") ? nodeJson.get("tcp_port").asInt() : 0;
                int apiPort = nodeJson.has("api_port") ? nodeJson.get("api_port").asInt() : 0;
                nodes.add(new NodeAddr(nodeId, addr, tcpPort, apiPort));
            }

            return nodes;

        } catch (java.io.IOException | InterruptedException e) {
            throw RoomzinException.of("Failed to fetch discovery: " + e.getMessage());
        }
    }

    /**
     * Starts background discovery task for HTTP mode
     */
    private void startDiscoveryTask() {
        long interval = config.getNodeProbeInterval().toMillis();

        // Initial fetch on startup
        try {
            List<NodeAddr> nodes = fetchExternalDiscovery();
            if (!nodes.isEmpty()) {
                updateDiscoveryMap(nodes);
            }
        } catch (RoomzinException e) {
            // silent fail - keep using empty map
        }

        // Periodic fetch
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                List<NodeAddr> nodes = fetchExternalDiscovery();
                if (!nodes.isEmpty()) {
                    updateDiscoveryMap(nodes);
                }
            } catch (RoomzinException e) {
                // silent fail - keep using existing map
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void setOnReconnectCallback(Runnable callback) {
        this.leaderHandler.setOnReconnect(callback);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            // Start leader sync worker with proper interval
            scheduler.scheduleWithFixedDelay(leaderHandler::leaderSyncWorker, 0,
                    config.getNodeProbeInterval().toMillis(), TimeUnit.MILLISECONDS);

            // Start follower sync worker with proper interval
            scheduler.scheduleWithFixedDelay(followersHandler::followerSyncWorker, 0,
                    config.getNodeProbeInterval().toMillis(), TimeUnit.MILLISECONDS);

            // Start cleanup workers
            scheduler.scheduleWithFixedDelay(leaderHandler::cleanupDemux, 0,
                    config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            scheduler.scheduleWithFixedDelay(followersHandler::cleanupDemux, 0,
                    config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

            scheduler.scheduleWithFixedDelay(() -> {
                if (followersHandler.getActiveConnectionCount() == 0) {
                    followersHandler.followerSyncWorker();
                }
            }, 0, 100, TimeUnit.MILLISECONDS); // 100ms fast check

            // Start send workers
            executor.submit(leaderHandler::leaderSendWorker);
            executor.submit(followersHandler::followerSendWorker);
        }
    }

    public ProtocolTypes.RawResult execute(boolean isWrite, byte[] payload) throws RoomzinException {
        if (closed.get()) {
            throw RoomzinException.of("Cluster handler closed");
        }
        if (payload == null || payload.length == 0) {
            throw RoomzinException.of("Payload should not be empty");
        }
        if (isWrite && leaderHandler.conn == null) {
            throw RoomzinException.of("cluster has no leader");
        }

        // Get objects from pools
        Request req = borrowRequest();
        BlockingQueue<ProtocolTypes.RawResult> respQueue = borrowResponseQueue();

        try {
            req.payload = payload;
            req.respQueue = respQueue;
            req.clrId = 0; // Will be set by send worker

            // Choose handler based on write/read
            BlockingQueue<Request> handlerQueue = isWrite ? leaderHandler.reqQueue : followersHandler.reqQueue;

            // Retry policy
            int maxRetries = 5;
            int attempts = 0;

            // Send first attempt
            if (!handlerQueue.offer(req)) {
                throw RoomzinException.of("Request queue full");
            }

            while (attempts <= maxRetries) {
                try {
                    ProtocolTypes.RawResult result = respQueue.poll(config.getTimeout().toMillis(),
                            TimeUnit.MILLISECONDS);
                    if (result == null) {
                        throw RoomzinException.of("Request timeout");
                    }

                    if ("SUCCESS".equals(result.status)) {
                        return result;
                    }

                    // Handle errors with retry logic
                    String errMsg = result.fields.isEmpty() ? result.status
                            : new String(result.fields.get(0).data, StandardCharsets.UTF_8);

                    boolean backoff = false;
                    switch (errMsg) {
                        case "405": // follower node is promoted to leader and rejects reads
                        case "308": // leader changed
                            // Immediate retry for these cases
                            break;
                        case "503": // unavailable
                        case "429": // busy
                            backoff = true;
                            break;
                        default:
                            if (attempts >= maxRetries) {
                                return result; // Return final error after max retries
                            }
                            attempts++;
                            continue; // Retry for other errors
                    }

                    if (attempts >= maxRetries) {
                        throw RoomzinException.of("Max retries reached after " + errMsg);
                    }
                    attempts++;

                    // Backoff for busy/unavailable errors
                    if (backoff) {
                        TimeUnit.MILLISECONDS.sleep(attempts * 100L);
                    }

                    // Retry the request
                    req.clrId = 0; // Reset for retry
                    if (!handlerQueue.offer(req)) {
                        throw RoomzinException.of("Retry failed - queue full after " + errMsg);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw RoomzinException.of("Execution interrupted");
                }
            }

            throw RoomzinException.of("Max retries exceeded");

        } catch (Exception e) {
            // Return objects to pool on failure
            returnRequest(req);
            returnResponseQueue(respQueue);
            throw RoomzinException.of(e);
        }
    }

    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            scheduler.shutdownNow();
            executor.shutdownNow();
            leaderHandler.close();
            followersHandler.close();
        }
    }

    // Pool management methods
    private Request borrowRequest() {
        Request req = requestPool.poll();
        return req != null ? req : new Request();
    }

    private void returnRequest(Request req) {
        req.payload = null;
        req.respQueue = null;
        req.clrId = 0;
        requestPool.offer(req);
    }

    private BlockingQueue<ProtocolTypes.RawResult> borrowResponseQueue() {
        BlockingQueue<ProtocolTypes.RawResult> queue = responseQueuePool.poll();
        return queue != null ? queue : new ArrayBlockingQueue<>(1);
    }

    private void returnResponseQueue(BlockingQueue<ProtocolTypes.RawResult> queue) {
        queue.clear();
        responseQueuePool.offer(queue);
    }

    // Inner classes
    private static class Request {
        byte[] payload;
        BlockingQueue<ProtocolTypes.RawResult> respQueue;
        @SuppressWarnings("unused")
        int clrId;
    }

    private static class DemuxMap {
        private final ConcurrentHashMap<Integer, DemuxEntry> entries = new ConcurrentHashMap<>();

        void store(int clrId, BlockingQueue<ProtocolTypes.RawResult> ch, Instant sendTime) {
            entries.put(clrId, new DemuxEntry(ch, sendTime));
        }

        DemuxEntry loadRemove(int clrId) {
            return entries.remove(clrId);
        }

        void cleanup(Duration maxAge) {
            Instant threshold = Instant.now().minus(maxAge);
            final ProtocolTypes.RawResult timeoutResult = new ProtocolTypes.RawResult("ERROR",
                    List.of(new ProtocolTypes.Field((short) 0, (byte) 0,
                            "Timeout".getBytes(StandardCharsets.UTF_8))));

            entries.entrySet().removeIf(e -> {
                if (e.getValue().sendTime.isBefore(threshold)) {
                    // Send timeout result - this will unblock the poll() in execute()
                    e.getValue().ch.offer(timeoutResult);
                    return true;
                }
                return false;
            });
        }
    }

    private static class DemuxEntry {
        final BlockingQueue<ProtocolTypes.RawResult> ch;
        final Instant sendTime;

        DemuxEntry(BlockingQueue<ProtocolTypes.RawResult> ch, Instant sendTime) {
            this.ch = ch;
            this.sendTime = sendTime;
        }
    }

    private static class Connection {
        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;
        private final DemuxMap demuxMap;
        private final BlockingQueue<byte[]> sendQueue = new ArrayBlockingQueue<>(8192);
        private final String addr;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
        private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();

        Connection(String addr, int tcpPort, String authToken, Duration timeout, DemuxMap demuxMap)
                throws RoomzinException {
            this.addr = addr;
            this.demuxMap = demuxMap;

            try {
                socket = new Socket(addr, tcpPort);
                socket.setKeepAlive(true);
                socket.setSoTimeout((int) timeout.toMillis());
                output = new BufferedOutputStream(socket.getOutputStream());
                input = new BufferedInputStream(socket.getInputStream());

                // Login sequence
                byte[] loginPayload = Login.buildLoginPayload(authToken);
                byte[] frame = Frame.prependHeader(0, loginPayload);
                output.write(frame);
                output.flush();

                // Read login response
                byte[] buf = new byte[8]; // "LOGIN OK" is 8 bytes
                int n = input.read(buf);
                if (n != 8) {
                    throw RoomzinException.of("Login failed: short response");
                }
                String reply = new String(buf, 0, n);
                if (!"LOGIN OK".equals(reply)) {
                    throw RoomzinException.of("Login failed: " + reply);
                }
            } catch (IOException e) {
                throw RoomzinException.of("Failed to connect to " + addr + ": " + e.getMessage());
            }
        }

        void activate() {
            writeExecutor.submit(this::writeLoop);
            readExecutor.submit(this::readLoop);
        }

        private void writeLoop() {
            while (!closed.get()) {
                try {
                    byte[] data = sendQueue.take();
                    output.write(data);
                    output.flush();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    break;
                }
            }
            close();
        }

        private void readLoop() {
            while (!closed.get()) {
                try {
                    Frame.FrameData frameData = Frame.drainFrame(input);
                    ProtocolTypes.Header hdr = frameData.header;

                    DemuxEntry entry = demuxMap.loadRemove(hdr.clrId);
                    if (entry == null) {
                        close();
                        return;
                    }

                    // Parse fields from payload
                    int statusLen = Byte.toUnsignedInt(frameData.payload[0]);
                    byte[] fieldsData = new byte[frameData.payload.length - (1 + statusLen + 2)];
                    System.arraycopy(frameData.payload, 1 + statusLen + 2, fieldsData, 0, fieldsData.length);
                    List<ProtocolTypes.Field> fields = Frame.parseFields(fieldsData, hdr.fieldCnt);

                    // Handle error hints
                    if ("ERROR".equals(hdr.status) && !fields.isEmpty()) {
                        String errCode = new String(fields.get(0).data, StandardCharsets.UTF_8);
                        switch (errCode) {
                            case "308": // leader changed
                            case "405": // method not allowed - leader rejects reads
                            case "503": // unavailable
                                close();
                                break;
                            case "429": // busy
                                break;
                        }
                    }

                    entry.ch.offer(new ProtocolTypes.RawResult(hdr.status, fields));
                } catch (Exception e) {
                    close();
                    break;
                }
            }
        }

        public boolean send(byte[] frame) {
            return !closed.get() && sendQueue.offer(frame);
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                writeExecutor.shutdownNow();
                readExecutor.shutdownNow();
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        public boolean isClosed() {
            return closed.get() || socket.isClosed();
        }

        public String getAddr() {
            return addr;
        }
    }

    private static class LeaderHandler {
        private final ClusterConfig cfg;
        private final DiscoveryMap discoveryMap;
        private final BlockingQueue<Request> reqQueue = new ArrayBlockingQueue<>(1024);
        private final AtomicInteger clrId = new AtomicInteger(0);
        private Connection conn;
        private final ReentrantReadWriteLock connLock = new ReentrantReadWriteLock();
        private final DemuxMap demuxMap = new DemuxMap();
        private Runnable onReconnect;

        LeaderHandler(ClusterConfig cfg, DiscoveryMap discoveryMap) {
            this.cfg = cfg;
            this.discoveryMap = discoveryMap;
        }

        public void setOnReconnect(Runnable onReconnect) {
            this.onReconnect = onReconnect;
        }

        private void triggerOnReconnectCallback() {
            if (onReconnect != null) {
                onReconnect.run();
            }
        }

        void leaderSyncWorker() {
            try {
                // Check if current connection is healthy
                connLock.readLock().lock();
                Connection currentConn = conn;
                connLock.readLock().unlock();

                if (currentConn != null && !currentConn.isClosed()) {
                    return; // Connection is healthy
                }

                // Invalidate client's codecs
                triggerOnReconnectCallback();

                // Reconnect to leader using discovery map
                HttpUtil.ClusterInfo clusterInfo = HttpUtil.getClusterInfo(cfg, discoveryMap);
                NodeAddr leader = clusterInfo.leader;

                Connection newConn = new Connection(leader.getAddr(), leader.getTcpPort(), cfg.getAuthToken(),
                        cfg.getTimeout(), demuxMap);
                newConn.activate();

                connLock.writeLock().lock();
                try {
                    if (conn != null) {
                        conn.close();
                    }
                    conn = newConn;
                } finally {
                    connLock.writeLock().unlock();
                }

                // Give tasks time to start
                TimeUnit.MILLISECONDS.sleep(10);

            } catch (Exception ignored) {
                // Retry on next iteration
            }
        }

        void leaderSendWorker() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Request req = reqQueue.take();

                    Connection currentConn;
                    connLock.readLock().lock();
                    try {
                        currentConn = conn;
                    } finally {
                        connLock.readLock().unlock();
                    }

                    // Wait for connection to be ready
                    while (currentConn == null || currentConn.isClosed()) {
                        TimeUnit.MILLISECONDS.sleep(100);
                        connLock.readLock().lock();
                        try {
                            currentConn = conn;
                        } finally {
                            connLock.readLock().unlock();
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }

                    int currentClrId = clrId.incrementAndGet();
                    req.clrId = currentClrId;
                    demuxMap.store(currentClrId, req.respQueue, Instant.now());

                    byte[] frame = Frame.prependHeader(currentClrId, req.payload);
                    if (!currentConn.send(frame)) {
                        req.respQueue.offer(new ProtocolTypes.RawResult("ERROR",
                                List.of(new ProtocolTypes.Field((short) 1, (byte) 0,
                                        "Send failed".getBytes(StandardCharsets.UTF_8)))));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        void cleanupDemux() {
            demuxMap.cleanup(cfg.getTimeout().multipliedBy(2));
        }

        void close() throws IOException {
            connLock.writeLock().lock();
            try {
                if (conn != null) {
                    conn.close();
                }
            } finally {
                connLock.writeLock().unlock();
            }
        }
    }

    private static class FollowersHandler {
        private final ClusterConfig cfg;
        private final DiscoveryMap discoveryMap;
        private final BlockingQueue<Request> reqQueue = new ArrayBlockingQueue<>(1024);
        private final AtomicInteger clrId = new AtomicInteger(0);
        private final List<Connection> connections = new ArrayList<>();
        private final AtomicInteger rrIndex = new AtomicInteger(0);
        private final ReentrantReadWriteLock connLock = new ReentrantReadWriteLock();

        FollowersHandler(ClusterConfig cfg, DiscoveryMap discoveryMap) {
            this.cfg = cfg;
            this.discoveryMap = discoveryMap;
        }

        public int getActiveConnectionCount() {
            connLock.readLock().lock();
            try {
                return (int) connections.stream()
                        .filter(c -> !c.isClosed())
                        .count();
            } finally {
                connLock.readLock().unlock();
            }
        }

        void followerSyncWorker() {
            try {
                HttpUtil.ClusterInfo clusterInfo = HttpUtil.getClusterInfo(cfg, discoveryMap);
                List<NodeAddr> followers = clusterInfo.followers;

                // Update connections list
                connLock.writeLock().lock();
                try {
                    // Remove connections that are no longer in follower list or are closed
                    connections.removeIf(conn -> {
                        boolean stillFollower = followers.stream()
                                .anyMatch(f -> f.getAddr().equals(conn.getAddr()));
                        return !stillFollower || conn.isClosed();
                    });

                    // Add new followers we don't have yet
                    for (NodeAddr follower : followers) {
                        boolean exists = connections.stream()
                                .anyMatch(c -> c.getAddr().equals(follower.getAddr()) && !c.isClosed());
                        if (!exists) {
                            try {
                                Connection newConn = new Connection(follower.getAddr(), follower.getTcpPort(),
                                        cfg.getAuthToken(), cfg.getTimeout(), new DemuxMap());
                                newConn.activate();
                                connections.add(newConn);
                            } catch (RoomzinException ignored) {
                                // Skip failed connection
                            }
                        }
                    }

                    // Reset round-robin index if needed
                    if (rrIndex.get() >= connections.size()) {
                        rrIndex.set(0);
                    }
                } finally {
                    connLock.writeLock().unlock();
                }

            } catch (Exception ignored) {
                // Retry on next iteration
            }
        }

        void followerSendWorker() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Request req = reqQueue.take();

                    Connection nextConn;
                    // Wait for a valid connection
                    while (true) {
                        nextConn = nextFollowerConnection();
                        if (nextConn != null && !nextConn.isClosed()) {
                            break;
                        }
                        TimeUnit.MILLISECONDS.sleep(100);
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }

                    int currentClrId = clrId.incrementAndGet();
                    req.clrId = currentClrId;
                    nextConn.demuxMap.store(currentClrId, req.respQueue, Instant.now());

                    byte[] frame = Frame.prependHeader(currentClrId, req.payload);
                    if (!nextConn.send(frame)) {
                        req.respQueue.offer(new ProtocolTypes.RawResult("ERROR",
                                List.of(new ProtocolTypes.Field((short) 1, (byte) 0,
                                        "Send failed".getBytes(StandardCharsets.UTF_8)))));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private Connection nextFollowerConnection() {
            connLock.readLock().lock();
            try {
                if (connections.isEmpty()) {
                    return null;
                }

                int startIdx = rrIndex.getAndUpdate(i -> (i + 1) % Math.max(connections.size(), 1));
                int attempts = 0;

                while (attempts < connections.size()) {
                    int idx = (startIdx + attempts) % connections.size();
                    Connection conn = connections.get(idx);

                    if (!conn.isClosed()) {
                        return conn;
                    }
                    attempts++;
                }

                return null;
            } finally {
                connLock.readLock().unlock();
            }
        }

        void cleanupDemux() {
            connLock.readLock().lock();
            try {
                for (Connection conn : connections) {
                    if (!conn.isClosed()) {
                        conn.demuxMap.cleanup(cfg.getTimeout().multipliedBy(2));
                    }
                }
            } finally {
                connLock.readLock().unlock();
            }
        }

        void close() throws IOException {
            connLock.writeLock().lock();
            try {
                for (Connection conn : connections) {
                    conn.close();
                }
                connections.clear();
                rrIndex.set(0);
            } finally {
                connLock.writeLock().unlock();
            }
        }
    }
}