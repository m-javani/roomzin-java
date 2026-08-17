package com.roomzin.roomzinjava.internal.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.roomzin.roomzinjava.internal.protocol.ProtocolTypes.*;

public class Frame {

    /**
     * Builds a shard frame for standalone mode:
     * | magic(1) | clrid(4) | totalLen(4) | payload |
     */
    public static byte[] prependHeader(int clrId, byte[] payload) {
        int totalLen = payload.length;
        ByteBuffer bb = ByteBuffer.allocate(9 + totalLen).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(SHARD_MAGIC);
        bb.putInt(clrId);
        bb.putInt(totalLen);
        bb.put(payload);
        return bb.array();
    }

    /**
     * Builds a router frame for cluster mode:
     * | routerMagic(1) | totalLen(4) | segmentLen(1) | segment(n) | isWrite(1) |
     * shardFrame |
     * where shardFrame is the output of prependHeader
     */
    public static byte[] prependRouterHeader(String segment, boolean isWrite, int clrId, byte[] payload) {
        // Shard frame: magic(1) + clrid(4) + totalLen(4) + payload
        int shardTotalLen = payload.length;
        int shardFrameLen = 9 + shardTotalLen;

        // Router header: segmentLen(1) + segment(n) + isWrite(1)
        byte[] segmentBytes = segment.getBytes(StandardCharsets.UTF_8);
        int segmentLen = segmentBytes.length;
        int routerHeaderLen = 1 + segmentLen + 1;

        // Total frame: routerMagic(1) + totalLen(4) + routerHeader + shardFrame
        int totalLen = 1 + 4 + routerHeaderLen + shardFrameLen;

        ByteBuffer bb = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);

        // Router magic
        bb.put(ROUTER_MAGIC);

        // Total length (everything after this field)
        bb.putInt(routerHeaderLen + shardFrameLen);

        // Segment length
        bb.put((byte) segmentLen);

        // Segment
        bb.put(segmentBytes);

        // IsWrite flag
        bb.put((byte) (isWrite ? 0x01 : 0x00));

        // Shard frame (magic, clrid, totalLen, payload)
        bb.put(SHARD_MAGIC);
        bb.putInt(clrId);
        bb.putInt(shardTotalLen);
        bb.put(payload);

        return bb.array();
    }

    /**
     * Builds a keepalive frame for router mode:
     * Uses special segment "__keepalive__" with empty payload
     */
    public static byte[] buildKeepaliveFrame(int clrId) {
        return prependRouterHeader(KEEPALIVE_SEGMENT, false, clrId, new byte[0]);
    }

    public static class FrameData {
        public final ProtocolTypes.Header header;
        public final byte[] payload;

        FrameData(ProtocolTypes.Header header, byte[] payload) {
            this.header = header;
            this.payload = payload;
        }
    }

    public static FrameData drainFrame(InputStream input) throws RoomzinException, IOException {
        byte[] fix = new byte[9];
        readFully(input, fix);

        if (fix[0] != SHARD_MAGIC) {
            throw RoomzinException.of("Bad magic byte: " + fix[0]);
        }

        ByteBuffer bb = ByteBuffer.wrap(fix).order(ByteOrder.LITTLE_ENDIAN);
        int clrId = bb.getInt(1);
        int payloadLen = bb.getInt(5);

        byte[] payload = new byte[payloadLen];
        readFully(input, payload);

        if (payload.length < 1) {
            throw RoomzinException.of("Short frame: no statusLen");
        }

        int statusLen = Byte.toUnsignedInt(payload[0]);
        if (payload.length < 1 + statusLen + 2) {
            throw RoomzinException.of("Short frame: missing status or fieldCount");
        }

        String status = new String(payload, 1, statusLen);
        int fieldCnt = ByteBuffer.wrap(payload, 1 + statusLen, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

        return new FrameData(new ProtocolTypes.Header(clrId, status, fieldCnt), payload);
    }

    public static List<ProtocolTypes.Field> parseFields(byte[] data, int fieldCount) throws RoomzinException {
        List<ProtocolTypes.Field> fields = new ArrayList<>(fieldCount);
        int offset = 0;

        for (int i = 0; i < fieldCount; i++) {
            if (offset + 7 > data.length) {
                throw RoomzinException.of("Short frame: not enough bytes for field header at field " + i);
            }

            int id = ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            byte fieldType = data[offset + 2];
            int length = ByteBuffer.wrap(data, offset + 3, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            offset += 7;

            if (offset + length > data.length) {
                throw RoomzinException.of("Short frame: not enough data for field payload at field " + i);
            }

            byte[] fieldData = new byte[length];
            System.arraycopy(data, offset, fieldData, 0, length);
            fields.add(new ProtocolTypes.Field(id, fieldType, fieldData));
            offset += length;
        }

        if (offset != data.length) {
            throw RoomzinException.of("Extra bytes after parsing fields: " + (data.length - offset));
        }

        return fields;
    }

    private static void readFully(InputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read == -1) {
                throw new IOException("Unexpected EOF");
            }
            offset += read;
        }
    }
}