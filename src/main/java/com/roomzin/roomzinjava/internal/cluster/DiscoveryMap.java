package com.roomzin.roomzinjava.internal.cluster;

import com.roomzin.roomzinjava.types.NodeAddr;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe discovery map for resolving node IDs to addresses.
 * Used by the cluster handler to resolve node IDs to host/port combinations.
 */
public class DiscoveryMap {
    private final Map<String, ResolvedAddr> data = new ConcurrentHashMap<>();

    /**
     * Resolved address information for a node.
     */
    public static class ResolvedAddr {
        private final String host;
        private final int tcpPort;
        private final int apiPort;

        public ResolvedAddr(String host, int tcpPort, int apiPort) {
            this.host = host;
            this.tcpPort = tcpPort;
            this.apiPort = apiPort;
        }

        public String getHost() {
            return host;
        }

        public int getTcpPort() {
            return tcpPort;
        }

        public int getApiPort() {
            return apiPort;
        }
    }

    /**
     * Resolves a node ID to its address information.
     * 
     * @param nodeId The node ID to resolve
     * @return ResolvedAddr if found, null otherwise
     */
    public ResolvedAddr resolve(String nodeId) {
        return data.get(nodeId);
    }

    /**
     * Updates the discovery map with new nodes (used by HTTP discovery mode).
     * 
     * @param nodes          List of NodeAddr objects
     * @param defaultTcpPort Default TCP port if not specified
     * @param defaultApiPort Default API port if not specified
     */
    public void update(List<NodeAddr> nodes, int defaultTcpPort, int defaultApiPort) {
        Map<String, ResolvedAddr> newData = new ConcurrentHashMap<>();
        for (NodeAddr node : nodes) {
            int tcpPort = node.getTcpPort() > 0 ? node.getTcpPort() : defaultTcpPort;
            int apiPort = node.getApiPort() > 0 ? node.getApiPort() : defaultApiPort;
            newData.put(node.getNodeId(), new ResolvedAddr(node.getAddr(), tcpPort, apiPort));
        }
        data.clear();
        data.putAll(newData);
    }

    /**
     * Sets static discovery data (used by static discovery mode).
     * 
     * @param nodes          List of NodeAddr objects
     * @param defaultTcpPort Default TCP port if not specified
     * @param defaultApiPort Default API port if not specified
     */
    public void setStatic(List<NodeAddr> nodes, int defaultTcpPort, int defaultApiPort) {
        data.clear();
        for (NodeAddr node : nodes) {
            int tcpPort = node.getTcpPort() > 0 ? node.getTcpPort() : defaultTcpPort;
            int apiPort = node.getApiPort() > 0 ? node.getApiPort() : defaultApiPort;
            data.put(node.getNodeId(), new ResolvedAddr(node.getAddr(), tcpPort, apiPort));
        }
    }

    /**
     * Returns the size of the discovery map.
     */
    public int size() {
        return data.size();
    }

    /**
     * Checks if the discovery map is empty.
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }
}