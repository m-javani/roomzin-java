package com.roomzin.roomzinjava.internal;

import com.roomzin.roomzinjava.client.RoomzinConfig;
import com.roomzin.roomzinjava.internal.protocol.Frame;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified handler for Roomzin Java SDK.
 * Supports both standalone and router modes with a single connection.
 */
public class RoomzinHandler {
    private final RoomzinConfig config;
    private Socket socket;
    private BufferedInputStream input;
    private BufferedOutputStream output;
    private final ConcurrentHashMap<Integer, ArrayBlockingQueue<ProtocolTypes.RawResult>> demux = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable onReconnect;
    private volatile boolean keepaliveRunning = false;
    private Thread keepaliveThread;

    public void setOnReconnect(Runnable onReconnect) {
        this.onReconnect = onReconnect;
    }

    private void triggerReconnect() {
        if (onReconnect != null) {
            onReconnect.run();
        }
    }

    public RoomzinHandler(RoomzinConfig config) throws RoomzinException {
        this.config = config;
        reconnect();
        executor.submit(this::readLoop);

        // Start keepalive only in router mode
        if (config.getMode() == ProtocolTypes.Mode.ROUTER) {
            startKeepalive();
        }
    }

    private void reconnect() throws RoomzinException {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            socket = new Socket(config.getAddr(), config.getPort());
            socket.setKeepAlive(true);
            socket.setSoTimeout((int) config.getTimeout().toMillis());
            output = new BufferedOutputStream(socket.getOutputStream());
            input = new BufferedInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw RoomzinException.of("Connection error: " + e.getMessage());
        }
    }

    private void startKeepalive() {
        if (keepaliveRunning)
            return;
        keepaliveRunning = true;

        keepaliveThread = new Thread(() -> {
            while (!closed.get() && keepaliveRunning) {
                try {
                    Thread.sleep(config.getKeepAliveSec().toMillis()); // 10 seconds
                    if (!closed.get() && socket != null && !socket.isClosed()) {
                        byte[] frame = Frame.buildKeepaliveFrame(0);
                        output.write(frame);
                        output.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    // Connection issue - will be handled by reconnect
                    break;
                }
            }
        });
        keepaliveThread.setDaemon(true);
        keepaliveThread.start();
    }

    private void stopKeepalive() {
        keepaliveRunning = false;
        if (keepaliveThread != null) {
            keepaliveThread.interrupt();
            keepaliveThread = null;
        }
    }

    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            stopKeepalive();
            executor.shutdownNow();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public ProtocolTypes.RawResult execute(String segment, boolean isWrite, byte[] payload) throws RoomzinException {
        if (closed.get()) {
            throw RoomzinException.of("Handler closed");
        }

        // Self-heal
        if (socket == null || socket.isClosed()) {
            reconnect();
        }

        int clrId = nextId.getAndIncrement();
        ArrayBlockingQueue<ProtocolTypes.RawResult> queue = new ArrayBlockingQueue<>(1);
        demux.put(clrId, queue);

        try {
            byte[] frame;
            if (config.getMode() == ProtocolTypes.Mode.ROUTER) {
                frame = Frame.prependRouterHeader(segment, isWrite, clrId, payload);
            } else {
                frame = Frame.prependHeader(clrId, payload);
            }
            output.write(frame);
            output.flush();
        } catch (IOException e) {
            demux.remove(clrId);
            reconnect();
            throw RoomzinException.of("Write error: " + e.getMessage());
        }

        try {
            ProtocolTypes.RawResult res = queue.take();
            return res;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RoomzinException.of("Interrupted while waiting for response");
        } finally {
            demux.remove(clrId);
        }
    }

    private void readLoop() {
        while (!closed.get()) {
            try {
                Frame.FrameData frameData = Frame.drainFrame(input);
                ProtocolTypes.Header hdr = frameData.header;
                byte[] payload = frameData.payload;

                int statusLen = Byte.toUnsignedInt(payload[0]);
                if (payload.length < 1 + statusLen + 2) {
                    throw RoomzinException.of("Short frame: missing status or field count");
                }
                byte[] fieldsData = new byte[payload.length - (1 + statusLen + 2)];
                System.arraycopy(payload, 1 + statusLen + 2, fieldsData, 0, fieldsData.length);
                List<ProtocolTypes.Field> fields = Frame.parseFields(fieldsData, hdr.fieldCnt);

                ArrayBlockingQueue<ProtocolTypes.RawResult> queue = demux.get(hdr.clrId);
                if (queue != null) {
                    queue.put(new ProtocolTypes.RawResult(hdr.status, fields));
                }
            } catch (Exception e) {
                // Fail all pending requests
                demux.forEach((id, queue) -> {
                    try {
                        queue.put(new ProtocolTypes.RawResult("ERROR", List.of(
                                new ProtocolTypes.Field((short) 0x01, (byte) 0x01,
                                        e.getMessage().getBytes(StandardCharsets.UTF_8)))));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
                demux.clear();
                triggerReconnect();
                try {
                    reconnect();
                } catch (RoomzinException ignored) {
                    // Continue loop, will retry on next read
                }
            }
        }
    }
}