package com.roomzin.roomzinjava.client;

import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;

import java.time.Duration;
import java.util.Objects;

/**
 * Unified configuration for the Roomzin Java SDK client.
 * Supports both standalone and router (cluster) modes.
 */
public class RoomzinConfig {
    private final String addr;
    private final int port;
    private final ProtocolTypes.Mode mode;
    private final Duration timeout;
    private final Duration keepAliveSec;

    private RoomzinConfig(Builder builder) {
        this.addr = builder.addr;
        this.port = builder.port;
        this.mode = builder.mode;
        this.timeout = builder.timeout;
        this.keepAliveSec = builder.keepAliveSec;
    }

    public String getAddr() {
        return addr;
    }

    public int getPort() {
        return port;
    }

    public ProtocolTypes.Mode getMode() {
        return mode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getKeepAliveSec() {
        return keepAliveSec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RoomzinConfig that = (RoomzinConfig) o;
        return port == that.port &&
                Objects.equals(addr, that.addr) &&
                mode == that.mode &&
                Objects.equals(timeout, that.timeout) &&
                Objects.equals(keepAliveSec, that.keepAliveSec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addr, port, mode, timeout, keepAliveSec);
    }

    /**
     * Builder for constructing a RoomzinConfig instance with validation.
     */
    public static class Builder {
        private String addr = "127.0.0.1";
        private int port = 7777;
        private ProtocolTypes.Mode mode = ProtocolTypes.Mode.STANDALONE;
        private Duration timeout = Duration.ofSeconds(2);
        private Duration keepAliveSec = Duration.ofSeconds(5);

        public Builder withAddr(String addr) {
            this.addr = addr != null ? addr.trim() : "";
            return this;
        }

        public Builder withPort(int port) {
            this.port = port;
            return this;
        }

        public Builder withMode(ProtocolTypes.Mode mode) {
            this.mode = mode;
            return this;
        }

        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout != null ? timeout : Duration.ofSeconds(2);
            return this;
        }

        public Builder withKeepAlive(Duration keepAlive) {
            this.keepAliveSec = keepAlive != null ? keepAlive : Duration.ofSeconds(30);
            return this;
        }

        public RoomzinConfig build() {
            if (addr.isEmpty()) {
                throw new IllegalArgumentException("Address cannot be empty");
            }
            if (port <= 0) {
                throw new IllegalArgumentException("Port must be positive");
            }
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            if (keepAliveSec.isNegative() || keepAliveSec.isZero()) {
                throw new IllegalArgumentException("Keep-alive duration must be positive");
            }
            return new RoomzinConfig(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}