package com.roomzin.roomzinjava.internal.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class Login {
    public static byte[] buildLoginPayload(String token) {
        String cmdName = "LOGIN";
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);

        ByteBuffer bb = ByteBuffer.allocate(1 + cmdName.length() + 2 + 2 + 1 + 4 + tokenBytes.length)
                .order(ByteOrder.LITTLE_ENDIAN);

        bb.put((byte) cmdName.length());
        bb.put(cmdName.getBytes(StandardCharsets.UTF_8));
        bb.putShort((short) 1); // field count

        bb.putShort((short) 0x01); // field ID
        bb.put((byte) 0x01); // type string
        bb.putInt(tokenBytes.length);
        bb.put(tokenBytes);

        return bb.array();
    }
}