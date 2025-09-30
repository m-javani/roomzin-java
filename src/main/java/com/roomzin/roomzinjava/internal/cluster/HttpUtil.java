package com.roomzin.roomzinjava.internal.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomzin.roomzinjava.client.cluster.ClusterConfig;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.NodeAddr;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Utility class for HTTP-based cluster discovery operations, including health
 * checks and node information retrieval.
 */
public class HttpUtil {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Represents node information returned from the /node-info endpoint.
     */
    public static class NodeInfo {
        public String nodeId;
        public String zoneId;
        public String shardId;
        public String leaderId;
    }

    /**
     * Represents the result of cluster discovery, containing the leader and list of
     * followers.
     */
    public static class ClusterInfo {
        public final NodeAddr leader;
        public final List<NodeAddr> followers;

        ClusterInfo(NodeAddr leader, List<NodeAddr> followers) {
            this.leader = leader;
            this.followers = followers;
        }
    }

    /**
     * Internal class to hold combined node data
     */
    private static class NodeData {
        final String nodeId;
        final String host;
        final int tcpPort;
        final int apiPort;
        final String health;
        final String leaderId;

        NodeData(String nodeId, String host, int tcpPort, int apiPort, String health, String leaderId) {
            this.nodeId = nodeId;
            this.host = host;
            this.tcpPort = tcpPort;
            this.apiPort = apiPort;
            this.health = health;
            this.leaderId = leaderId;
        }
    }

    /**
     * Parses a comma-separated string of node IDs into a list.
     */
    public static List<String> parseNodeIds(String s) throws RoomzinException {
        if (s == null || s.trim().isEmpty()) {
            throw RoomzinException.of("Seed node IDs cannot be empty");
        }
        String[] parts = s.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Performs an HTTP GET request and parses the response.
     */
    private static <T> T httpGet(String host, int port, String path, String authToken, Duration timeout, Class<T> dst)
            throws RoomzinException {
        if (host == null || host.trim().isEmpty()) {
            throw RoomzinException.of("Host cannot be empty");
        }
        if (port <= 0) {
            throw RoomzinException.of("Port must be positive");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw RoomzinException.of("Timeout must be positive");
        }

        String url = "http://" + host + ":" + port + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(timeout)
                .GET();
        if (authToken != null && !authToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + authToken);
        }
        HttpRequest req = builder.build();

        try {
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw RoomzinException.of("HTTP " + resp.statusCode());
            }

            if (dst == String.class) {
                return dst.cast(resp.body().trim());
            } else if (dst == NodeInfo.class) {
                JsonNode json = OBJECT_MAPPER.readTree(resp.body());
                NodeInfo info = new NodeInfo();
                info.nodeId = json.has("node_id") ? json.get("node_id").asText() : "";
                info.zoneId = json.has("zone_id") ? json.get("zone_id").asText() : "";
                info.shardId = json.has("shard_id") ? json.get("shard_id").asText() : "";
                info.leaderId = json.has("leader_id") ? json.get("leader_id").asText() : "";
                return dst.cast(info);
            } else if (dst == List.class) {
                JsonNode json = OBJECT_MAPPER.readTree(resp.body());
                List<String> peers = new ArrayList<>();
                if (json.isArray()) {
                    for (JsonNode node : json) {
                        peers.add(node.asText());
                    }
                } else if (json.has("peers") && json.get("peers").isArray()) {
                    for (JsonNode node : json.get("peers")) {
                        peers.add(node.asText());
                    }
                }
                return dst.cast(peers);
            } else {
                throw RoomzinException.of("Unsupported response type: " + dst.getName());
            }
        } catch (java.io.IOException e) {
            throw RoomzinException.of("HTTP request error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RoomzinException.of("HTTP request interrupted");
        }
    }

    /**
     * Performs a health check on a node.
     */
    public static String healthCheck(String host, int port, String authToken, Duration timeout)
            throws RoomzinException {
        return httpGet(host, port, "/healthz", authToken, timeout, String.class);
    }

    /**
     * Retrieves node information from a node.
     */
    public static NodeInfo getNodeInfo(String host, int port, String authToken, Duration timeout)
            throws RoomzinException {
        return httpGet(host, port, "/node-info", authToken, timeout, NodeInfo.class);
    }

    /**
     * Retrieves peers from a node.
     */
    @SuppressWarnings("unchecked")
    private static List<String> getPeers(String host, int port, String authToken, Duration timeout) {
        try {
            return httpGet(host, port, "/peers", authToken, timeout, List.class);
        } catch (RoomzinException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Discovers the cluster leader and followers using the provided configuration
     * and discovery map.
     */
    public static ClusterInfo getClusterInfo(ClusterConfig cfg, DiscoveryMap dmap) throws RoomzinException {
        List<String> nodeIds = parseNodeIds(cfg.getSeedNodeIds());
        if (nodeIds.isEmpty()) {
            throw RoomzinException.of("no seed node IDs provided");
        }

        ExecutorService es = Executors.newFixedThreadPool(nodeIds.size());

        Set<String> existing = new HashSet<>(nodeIds);
        Set<String> discovered = ConcurrentHashMap.newKeySet();
        Map<String, NodeData> nodes = new ConcurrentHashMap<>();

        // First phase: seed nodes
        List<Future<Void>> futures = new ArrayList<>();
        for (String nodeId : nodeIds) {
            futures.add(es.submit(() -> {
                DiscoveryMap.ResolvedAddr resolved = dmap.resolve(nodeId);
                if (resolved == null)
                    return null;

                String host = resolved.getHost();
                int apiPort = resolved.getApiPort();
                int tcpPort = resolved.getTcpPort();

                try {
                    String health = healthCheck(host, apiPort, cfg.getAuthToken(), cfg.getHttpTimeout());
                    if ("unavailable".equals(health))
                        return null;

                    NodeInfo info = getNodeInfo(host, apiPort, cfg.getAuthToken(), cfg.getHttpTimeout());

                    nodes.put(host, new NodeData(
                            nodeId, host, tcpPort, apiPort, health, info.leaderId));

                    // Discover peers
                    List<String> peers = getPeers(host, apiPort, cfg.getAuthToken(), cfg.getHttpTimeout());
                    for (String peerId : peers) {
                        if (!existing.contains(peerId)) {
                            discovered.add(peerId);
                        }
                    }
                } catch (RoomzinException e) {
                    // Ignore failed nodes
                }
                return null;
            }));
        }

        // Wait for first phase
        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                // Ignore
            }
        }

        // Second phase: discovered nodes
        if (!discovered.isEmpty()) {
            List<Future<Void>> newFutures = new ArrayList<>();
            for (String nodeId : discovered) {
                newFutures.add(es.submit(() -> {
                    DiscoveryMap.ResolvedAddr resolved = dmap.resolve(nodeId);
                    if (resolved == null)
                        return null;

                    String host = resolved.getHost();
                    int apiPort = resolved.getApiPort();
                    int tcpPort = resolved.getTcpPort();

                    try {
                        String health = healthCheck(host, apiPort, cfg.getAuthToken(), cfg.getHttpTimeout());
                        if ("unavailable".equals(health))
                            return null;

                        NodeInfo info = getNodeInfo(host, apiPort, cfg.getAuthToken(), cfg.getHttpTimeout());

                        nodes.put(host, new NodeData(
                                nodeId, host, tcpPort, apiPort, health, info.leaderId));
                    } catch (RoomzinException e) {
                        // Ignore failed nodes
                    }
                    return null;
                }));
            }

            for (Future<Void> f : newFutures) {
                try {
                    f.get();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        es.shutdown();

        // Leader election: count votes
        Map<String, Integer> votes = new HashMap<>();
        for (NodeData node : nodes.values()) {
            if (node.leaderId != null && !node.leaderId.isEmpty()) {
                votes.put(node.leaderId, votes.getOrDefault(node.leaderId, 0) + 1);
            }
        }

        if (votes.isEmpty()) {
            throw RoomzinException.of("no leader available");
        }

        String leaderId = null;
        int maxVotes = 0;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                leaderId = entry.getKey();
            }
        }

        if (leaderId == null) {
            throw RoomzinException.of("no leader available");
        }

        NodeAddr leader = null;
        List<NodeAddr> followers = new ArrayList<>();

        for (NodeData node : nodes.values()) {
            if (leaderId.equals(node.leaderId)) {
                NodeAddr addr = new NodeAddr(
                        node.nodeId,
                        node.host,
                        node.tcpPort,
                        node.apiPort);

                if ("active_leader".equals(node.health)) {
                    leader = addr;
                } else if ("active_follower".equals(node.health)) {
                    followers.add(addr);
                }
            }
        }

        if (leader == null) {
            throw RoomzinException.of("no leader available");
        }

        return new ClusterInfo(leader, followers);
    }
}