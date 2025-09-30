package com.roomzin.roomzinjava.internal.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Helpers {
    private static final List<String> RATE_CANCELS = List.of(
            "free_cancellation", "non_refundable", "pay_at_property", "includes_breakfast",
            "free_wifi", "no_prepayment", "partial_refund", "instant_confirmation");

    public static List<String> bitmaskToRateFeatureStrings(byte mask) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            if ((mask & (1 << i)) != 0) {
                out.add(RATE_CANCELS.get(i));
            }
        }
        return out;
    }

    public static String u16ToDate(short packed) throws RoomzinException { // uint16 as short
        int yearOffset = (packed >>> 9) & 0b111;
        int month = ((packed >>> 5) & 0b1111) + 1;
        int day = (packed & 0b11111) + 1;

        int baseYear = LocalDate.now().getYear();
        LocalDate date = LocalDate.of(baseYear + yearOffset, month, day);
        if (date.getMonthValue() != month || date.getDayOfMonth() != day) {
            throw RoomzinException.of("Invalid packed date");
        }
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static byte[] makeU8(short v) {
        return new byte[] { (byte) v };
    }

    public static byte[] makeU16(short v) {
        ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(v);
        return bb.array();
    }

    public static byte[] makeU32(int v) {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(v);
        return bb.array();
    }

    public static byte[] makeU64(long v) {
        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        bb.putLong(v);
        return bb.array();
    }

    public static byte[] makeF64(double v) {
        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        bb.putDouble(v);
        return bb.array();
    }

    public static byte[] makeBool(boolean v) {
        return new byte[] { (byte) (v ? 1 : 0) };
    }

    public static String bytesToPropertyID(byte[] data) {
        // 1. Too short → return empty
        if (data.length < 7) {
            return "";
        }

        // 2. Short string marker
        if (data[6] == (byte) 0xF0) {
            // Left segment: 0..5
            int leftLen = 0;
            for (int i = 0; i < 6; i++) {
                if (i >= data.length || data[i] == 0) {
                    break;
                }
                leftLen++;
            }

            // Right segment: 7..15
            int rightLen = 0;
            for (int i = 7; i < data.length; i++) {
                if (data[i] == 0) {
                    break;
                }
                rightLen++;
            }

            // Reconstruct original string
            byte[] result = new byte[leftLen + rightLen];
            System.arraycopy(data, 0, result, 0, leftLen);
            System.arraycopy(data, 7, result, leftLen, rightLen);
            return new String(result, java.nio.charset.StandardCharsets.US_ASCII);
        }

        // 3. UUID detection (valid version)
        int version = (data[6] & 0xF0) >>> 4;
        if (version == 1 || version == 2 || version == 3 || version == 4 || version == 5 || version == 7) {
            // Pad to 16 bytes if needed
            byte[] uuidBytes = new byte[16];
            int copyLen = Math.min(data.length, 16);
            System.arraycopy(data, 0, uuidBytes, 0, copyLen);

            try {
                ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
                UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
                return uuid.toString();
            } catch (Exception e) {
                // UUID parsing failed
            }
        }

        // This should never happen with proper server data
        return "";
    }
}