package com.roomzin.roomzinjava.client.single;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the Roomzin Java SDK single-node client.
 */
public class SingleConfig {
    private final String host;
    private final int tcpPort;
    private final String authToken;
    private final Duration timeout;

    private SingleConfig(Builder builder) {
        this.host = builder.host;
        this.tcpPort = builder.tcpPort;
        this.authToken = builder.authToken;
        this.timeout = builder.timeout;
    }

    public String getHost() {
        return host;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public String getAuthToken() {
        return authToken;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SingleConfig that = (SingleConfig) o;
        return tcpPort == that.tcpPort &&
                Objects.equals(host, that.host) &&
                Objects.equals(authToken, that.authToken) &&
                Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, tcpPort, authToken, timeout);
    }

    /**
     * Builder for constructing a SingleConfig instance with validation.
     */
    public static class Builder {
        private String host = "localhost";
        private int tcpPort = 9999;
        private String authToken = "";
        private Duration timeout = Duration.ofSeconds(2);

        public Builder withHost(String host) {
            this.host = host != null ? host.trim() : "";
            return this;
        }

        public Builder withTcpPort(int tcpPort) {
            this.tcpPort = tcpPort;
            return this;
        }

        public Builder withAuthToken(String authToken) {
            this.authToken = authToken != null ? authToken.trim() : "";
            return this;
        }

        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout != null ? timeout : Duration.ofSeconds(2);
            return this;
        }

        public SingleConfig build() {
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Host cannot be empty");
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
            return new SingleConfig(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}