package com.roomzin.roomzinjava.types;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class NodeAddr {
    @JsonProperty("node_id")
    private final String nodeId;

    @JsonProperty("addr")
    private final String addr;

    @JsonProperty("tcp_port")
    private final int tcpPort;

    @JsonProperty("api_port")
    private final int apiPort;

    public NodeAddr(
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("addr") String addr,
            @JsonProperty("tcp_port") int tcpPort,
            @JsonProperty("api_port") int apiPort) {
        this.nodeId = nodeId;
        this.addr = addr;
        this.tcpPort = tcpPort;
        this.apiPort = apiPort;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getAddr() {
        return addr;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getApiPort() {
        return apiPort;
    }

    @Override
    public String toString() {
        return "NodeAddr{" +
                "nodeId='" + nodeId + '\'' +
                ", addr='" + addr + '\'' +
                ", tcpPort=" + tcpPort +
                ", apiPort=" + apiPort +
                '}';
    }
}