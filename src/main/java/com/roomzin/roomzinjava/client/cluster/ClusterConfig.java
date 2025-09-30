package com.roomzin.roomzinjava.client.cluster;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import com.roomzin.roomzinjava.types.NodeAddr;

/**
 * Configuration for the Roomzin Java SDK cluster client.
 * Defines settings for connecting to a cluster, including seed node IDs, ports,
 * authentication, and timeouts.
 */
public class ClusterConfig {
    private final String seedNodeIds; // Comma-separated node ID list
    private final int apiPort; // HTTP port for discovery
    private final int tcpPort; // TCP port for commands
    private final String authToken;
    private final Duration timeout;
    private final Duration httpTimeout;
    private final Duration keepAlive;
    private final int maxActiveConns;
    private final Duration nodeProbeInterval;
    private final String discoveryAddr;
    private final List<NodeAddr> staticDiscovery;

    private ClusterConfig(Builder builder) {
        this.seedNodeIds = builder.seedNodeIds;
        this.apiPort = builder.apiPort;
        this.tcpPort = builder.tcpPort;
        this.authToken = builder.authToken;
        this.timeout = builder.timeout;
        this.httpTimeout = builder.httpTimeout;
        this.keepAlive = builder.keepAlive;
        this.maxActiveConns = builder.maxActiveConns;
        this.nodeProbeInterval = builder.nodeProbeInterval;
        this.discoveryAddr = builder.discoveryAddr;
        this.staticDiscovery = builder.staticDiscovery != null
                ? List.copyOf(builder.staticDiscovery)
                : List.of();
    }

    /**
     * Returns the comma-separated list of seed node IDs.
     * 
     * @return Seed node IDs string
     */
    public String getSeedNodeIds() {
        return seedNodeIds;
    }

    /**
     * Returns the HTTP API port for cluster discovery.
     * 
     * @return API port number
     */
    public int getApiPort() {
        return apiPort;
    }

    /**
     * Returns the TCP port for command communication.
     * 
     * @return TCP port number
     */
    public int getTcpPort() {
        return tcpPort;
    }

    /**
     * Returns the authentication token for cluster access.
     * 
     * @return Authentication token
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * Returns the timeout duration for general operations.
     * 
     * @return Timeout duration
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Returns the timeout duration for HTTP requests.
     * 
     * @return HTTP timeout duration
     */
    public Duration getHttpTimeout() {
        return httpTimeout;
    }

    /**
     * Returns the keep-alive duration for connections.
     * 
     * @return Keep-alive duration
     */
    public Duration getKeepAlive() {
        return keepAlive;
    }

    /**
     * Returns the maximum number of active connections.
     * 
     * @return Maximum active connections
     */
    public int getMaxActiveConns() {
        return maxActiveConns;
    }

    public Duration getNodeProbeInterval() {
        return nodeProbeInterval;
    }

    public String getDiscoveryAddr() {
        return discoveryAddr;
    }

    public List<NodeAddr> getStaticDiscovery() {
        return staticDiscovery;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClusterConfig that = (ClusterConfig) o;
        return apiPort == that.apiPort &&
                tcpPort == that.tcpPort &&
                maxActiveConns == that.maxActiveConns &&
                Objects.equals(seedNodeIds, that.seedNodeIds) &&
                Objects.equals(authToken, that.authToken) &&
                Objects.equals(timeout, that.timeout) &&
                Objects.equals(httpTimeout, that.httpTimeout) &&
                Objects.equals(keepAlive, that.keepAlive) &&
                Objects.equals(discoveryAddr, that.discoveryAddr) &&
                Objects.equals(staticDiscovery, that.staticDiscovery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seedNodeIds, apiPort, tcpPort, authToken, timeout,
                httpTimeout, keepAlive, maxActiveConns, discoveryAddr, staticDiscovery);
    }

    /**
     * Builder for constructing a {@link ClusterConfig} instance with validation.
     */
    public static class Builder {
        private String seedNodeIds = "";
        private int apiPort = 0;
        private int tcpPort = 0;
        private String authToken = "";
        private Duration timeout = Duration.ofSeconds(2);
        private Duration httpTimeout = Duration.ofSeconds(2);
        private Duration keepAlive = Duration.ofSeconds(30);
        private int maxActiveConns = 10;
        private Duration nodeProbeInterval = Duration.ofSeconds(2);
        private String discoveryAddr = "";
        private List<NodeAddr> staticDiscovery = List.of();

        /**
         * Sets the comma-separated list of seed node IDs.
         * 
         * @param seedNodeIds Comma-separated node IDs
         * @return This builder
         */
        public Builder withSeedNodeIds(String seedNodeIds) {
            this.seedNodeIds = seedNodeIds != null ? seedNodeIds.trim() : "";
            return this;
        }

        /**
         * Sets the HTTP API port for cluster discovery.
         * 
         * @param apiPort HTTP port number
         * @return This builder
         */
        public Builder withApiPort(int apiPort) {
            this.apiPort = apiPort;
            return this;
        }

        /**
         * Sets the TCP port for command communication.
         * 
         * @param tcpPort TCP port number
         * @return This builder
         */
        public Builder withTcpPort(int tcpPort) {
            this.tcpPort = tcpPort;
            return this;
        }

        /**
         * Sets the authentication token for cluster access.
         * 
         * @param authToken Authentication token
         * @return This builder
         */
        public Builder withAuthToken(String authToken) {
            this.authToken = authToken != null ? authToken.trim() : "";
            return this;
        }

        /**
         * Sets the timeout duration for general operations.
         * 
         * @param timeout Timeout duration
         * @return This builder
         */
        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout != null ? timeout : Duration.ofSeconds(2);
            return this;
        }

        /**
         * Sets the timeout duration for HTTP requests.
         * 
         * @param httpTimeout HTTP timeout duration
         * @return This builder
         */
        public Builder withHttpTimeout(Duration httpTimeout) {
            this.httpTimeout = httpTimeout != null ? httpTimeout : Duration.ofSeconds(2);
            return this;
        }

        /**
         * Sets the keep-alive duration for connections.
         * 
         * @param keepAlive Keep-alive duration
         * @return This builder
         */
        public Builder withKeepAlive(Duration keepAlive) {
            this.keepAlive = keepAlive != null ? keepAlive : Duration.ofSeconds(30);
            return this;
        }

        /**
         * Sets the maximum number of active connections.
         * 
         * @param maxActiveConns Maximum active connections
         * @return This builder
         */
        public Builder withMaxActiveConns(int maxActiveConns) {
            this.maxActiveConns = maxActiveConns;
            return this;
        }

        /**
         * Sets the node probe interval for health checks.
         * 
         * @param nodeProbeInterval Probe interval duration
         * @return This builder
         */
        public Builder withNodeProbeInterval(Duration nodeProbeInterval) {
            this.nodeProbeInterval = nodeProbeInterval != null ? nodeProbeInterval : Duration.ofSeconds(2);
            return this;
        }

        /**
         * Sets the external discovery service address (HTTP mode).
         * 
         * @param discoveryAddr Discovery service URL
         * @return This builder
         */
        public Builder withDiscoveryAddr(String discoveryAddr) {
            this.discoveryAddr = discoveryAddr != null ? discoveryAddr.trim() : "";
            return this;
        }

        /**
         * Sets the static discovery list (static mode).
         * 
         * @param staticDiscovery List of NodeAddr
         * @return This builder
         */
        public Builder withStaticDiscovery(List<NodeAddr> staticDiscovery) {
            this.staticDiscovery = staticDiscovery != null
                    ? List.copyOf(staticDiscovery)
                    : List.of();
            return this;
        }

        /**
         * Builds a {@link ClusterConfig} instance after validating all required fields.
         * 
         * @return A new {@link ClusterConfig} instance
         * @throws IllegalArgumentException If validation fails
         */
        public ClusterConfig build() {
            if (seedNodeIds.isEmpty()) {
                throw new IllegalArgumentException("Seed node IDs cannot be empty");
            }
            if (apiPort <= 0) {
                throw new IllegalArgumentException("API port must be positive");
            }
            if (tcpPort <= 0) {
                throw new IllegalArgumentException("TCP port must be positive");
            }
            if (authToken.isEmpty()) {
                throw new IllegalArgumentException("Authentication token cannot be empty");
            }
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            if (httpTimeout.isNegative() || httpTimeout.isZero()) {
                throw new IllegalArgumentException("HTTP timeout must be positive");
            }
            if (keepAlive.isNegative() || keepAlive.isZero()) {
                throw new IllegalArgumentException("Keep-alive duration must be positive");
            }
            if (maxActiveConns < 0) {
                throw new IllegalArgumentException("Max active connections cannot be negative");
            }
            if (nodeProbeInterval.isNegative() || nodeProbeInterval.isZero()) {
                throw new IllegalArgumentException("Node probe interval must be positive");
            }
            return new ClusterConfig(this);
        }
    }

    /**
     * Creates a new {@link Builder} instance for constructing a
     * {@link ClusterConfig}.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}