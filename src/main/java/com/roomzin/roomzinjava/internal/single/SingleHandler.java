package com.roomzin.roomzinjava.internal.single;

import com.roomzin.roomzinjava.client.single.SingleConfig;
import com.roomzin.roomzinjava.internal.protocol.Frame;
import com.roomzin.roomzinjava.internal.protocol.Login;
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
 * SingleHandler manages a single TCP connection to a Roomzin server, handling
 * command execution
 * and response demultiplexing for the single-node client.
 */
public class SingleHandler {
    private final SingleConfig config;
    private Socket socket;
    private BufferedInputStream input;
    private BufferedOutputStream output;
    private final ConcurrentHashMap<Integer, ArrayBlockingQueue<ProtocolTypes.RawResult>> demux = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable onReconnect;

    public void setOnReconnect(Runnable onReconnect) {
        this.onReconnect = onReconnect;
    }

    // Call this when reconnect happens
    private void triggerReconnect() {
        if (onReconnect != null) {
            onReconnect.run();
        }
    }

    /**
     * Constructs a SingleHandler with the given configuration, establishing a
     * connection and starting the read loop.
     * 
     * @param config The configuration for the single-node client
     * @throws RoomzinException If connection or login fails
     */
    public SingleHandler(SingleConfig config) throws RoomzinException {
        this.config = config;
        reconnect();
        executor.submit(this::readLoop);
    }

    /**
     * Re-establishes the TCP connection and performs the login handshake.
     * 
     * @throws RoomzinException If connection or login fails
     */
    private void reconnect() throws RoomzinException {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            socket = new Socket(config.getHost(), config.getTcpPort());
            socket.setKeepAlive(true);
            socket.setSoTimeout((int) config.getTimeout().toMillis());
            output = new BufferedOutputStream(socket.getOutputStream());
            input = new BufferedInputStream(socket.getInputStream());

            // Handshake
            byte[] payload = Login.buildLoginPayload(config.getAuthToken());
            byte[] frame = Frame.prependHeader(0, payload);
            output.write(frame);
            output.flush();

            byte[] response = new byte[32];
            int n = input.read(response);
            String reply = new String(response, 0, n).trim();
            if (!"LOGIN OK".equals(reply)) {
                throw RoomzinException.of("Login failed: " + reply);
            }
        } catch (IOException e) {
            throw RoomzinException.of("Connection error: " + e.getMessage());
        }
    }

    /**
     * Closes the handler, shutting down the executor and socket.
     * 
     * @throws IOException If socket closure fails
     */
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Sends a command payload and waits for its response.
     * 
     * @param payload The command payload to send
     * @return The raw result containing status and fields
     * @throws RoomzinException If the handler is closed, write fails, or
     *                          interrupted
     */
    public ProtocolTypes.RawResult roundTrip(byte[] payload) throws RoomzinException {
        if (closed.get()) {
            throw RoomzinException.of("Handler closed");
        }

        // Self-heal if disconnected
        if (socket == null || socket.isClosed()) {
            reconnect();
        }

        int clrId = nextId.getAndIncrement();
        ArrayBlockingQueue<ProtocolTypes.RawResult> queue = new ArrayBlockingQueue<>(1);
        demux.put(clrId, queue);

        try {
            byte[] frame = Frame.prependHeader(clrId, payload);
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

    /**
     * Continuously reads frames from the input stream, parses them, and routes
     * responses to the appropriate queue.
     */
    private void readLoop() {
        while (!closed.get()) {
            try {
                Frame.FrameData frameData = Frame.drainFrame(input);
                ProtocolTypes.Header hdr = frameData.header;
                byte[] payload = frameData.payload;

                // Parse payload into fields
                int statusLen = Byte.toUnsignedInt(payload[0]);
                if (payload.length < 1 + statusLen + 2) {
                    throw RoomzinException.of("Short frame: missing status or field count");
                }
                byte[] fieldsData = new byte[payload.length - (1 + statusLen + 2)];
                System.arraycopy(payload, 1 + statusLen + 2, fieldsData, 0, fieldsData.length);
                List<ProtocolTypes.Field> fields = Frame.parseFields(fieldsData, hdr.fieldCnt);

                // Route response to the corresponding queue
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
                // Connection lost - invalidate codecs
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