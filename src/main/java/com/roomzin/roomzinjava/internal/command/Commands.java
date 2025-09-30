package com.roomzin.roomzinjava.internal.command;

import com.roomzin.roomzinjava.internal.protocol.Helpers;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Commands {
    private static class FieldDef {
        short id;
        byte typ; // 0x01=string, 0x02=uint8/short, 0x03=uint32/uint64/float64
        byte[] data;

        FieldDef(short id, byte typ, byte[] data) {
            this.id = id;
            this.typ = typ;
            this.data = data != null ? data.clone() : new byte[0];
        }
    }

    private static byte[] buildPayload(String cmdName, Object[] fields) {
        // Implementation assumed correct: writes cmd length (1 byte), cmd name,
        // field count (uint16), then each field (id uint16, type byte, len uint32,
        // data)
        ByteBuffer buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) cmdName.length());
        buf.put(cmdName.getBytes(StandardCharsets.UTF_8));
        buf.putShort((short) fields.length);
        for (Object f : fields) {
            FieldDef field = (FieldDef) f;
            buf.putShort(field.id);
            buf.put(field.typ);
            buf.putInt(field.data.length);
            buf.put(field.data);
        }
        byte[] result = new byte[buf.position()];
        System.arraycopy(buf.array(), 0, result, 0, result.length);
        return result;
    }

    public static byte[] buildSetPropPayload(Codecs codecs, SetPropPayload p) throws RoomzinException {
        p.validate(codecs);

        String cmdName = "SETPROP";
        String amenityStr = String.join(",", p.getAmenities());
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getSegment().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getArea().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x03, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x04, (byte) 0x01, p.getPropertyType().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x05, (byte) 0x01, p.getCategory().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x06, (byte) 0x02, Helpers.makeU8(p.getStars())),
                new FieldDef((short) 0x07, (byte) 0x03, Helpers.makeF64(p.getLatitude())),
                new FieldDef((short) 0x08, (byte) 0x03, Helpers.makeF64(p.getLongitude())),
                new FieldDef((short) 0x09, (byte) 0x01, amenityStr.getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static void parseSetPropResponse(String status, List<ProtocolTypes.Field> fields) throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildSearchPropPayload(Codecs codecs, SearchPropPayload p) throws RoomzinException {
        p.validate(codecs);

        String cmdName = "SEARCHPROP";
        List<Object> fields = new ArrayList<>();
        fields.add(new FieldDef((short) 0x01, (byte) 0x01, p.getSegment().getBytes(StandardCharsets.UTF_8)));

        if (p.getArea() != null && !p.getArea().isEmpty()) {
            fields.add(new FieldDef((short) 0x02, (byte) 0x01, p.getArea().getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getType() != null && !p.getType().isEmpty()) {
            fields.add(new FieldDef((short) 0x03, (byte) 0x01, p.getType().getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getStars() != null) {
            fields.add(new FieldDef((short) 0x04, (byte) 0x02, Helpers.makeU8(p.getStars())));
        }
        if (p.getCategory() != null && !p.getCategory().isEmpty()) {
            fields.add(new FieldDef((short) 0x05, (byte) 0x01, p.getCategory().getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getAmenities() != null && !p.getAmenities().isEmpty()) {
            String amenityStr = String.join(",", p.getAmenities());
            fields.add(new FieldDef((short) 0x06, (byte) 0x01, amenityStr.getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getLongitude() != null) {
            fields.add(new FieldDef((short) 0x07, (byte) 0x03, Helpers.makeF64(p.getLongitude())));
        }
        if (p.getLatitude() != null) {
            fields.add(new FieldDef((short) 0x08, (byte) 0x03, Helpers.makeF64(p.getLatitude())));
        }
        if (p.getLimit() != null) {
            fields.add(new FieldDef((short) 0x09, (byte) 0x03, Helpers.makeU64(p.getLimit())));
        }

        return buildPayload(cmdName, fields.toArray());
    }

    public static List<String> parseSearchPropResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).id == 0x01 && fields.get(0).fieldType == 0x01
                    && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            ProtocolTypes.Field f = fields.get(i);
            if (f.id != i + 1 || f.fieldType != 0x01) {
                throw RoomzinException.of("RESPONSE_ERROR: invalid field ID " + f.id + ": expected " + (i + 1)
                        + ", or type: expected 0x01, got " + f.fieldType);
            }
            ids.add(Helpers.bytesToPropertyID(f.data));
        }
        return ids;
    }

    public static byte[] buildSearchAvailPayload(Codecs codecs, SearchAvailPayload p) throws RoomzinException {
        p.validate(codecs);

        String cmdName = "SEARCHAVAIL";
        List<Object> fields = new ArrayList<>();
        fields.add(new FieldDef((short) 0x01, (byte) 0x01, p.getSegment().getBytes(StandardCharsets.UTF_8)));
        fields.add(new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)));

        if (p.getArea() != null && !p.getArea().isEmpty()) {
            fields.add(new FieldDef((short) 0x03, (byte) 0x01, p.getArea().getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getPropertyId() != null && !p.getPropertyId().isEmpty()) {
            fields.add(new FieldDef((short) 0x04, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getType() != null && !p.getType().isEmpty()) {
            fields.add(new FieldDef((short) 0x05, (byte) 0x01, p.getType().getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getStars() != null) {
            fields.add(new FieldDef((short) 0x06, (byte) 0x02, Helpers.makeU8(p.getStars())));
        }
        if (p.getCategory() != null && !p.getCategory().isEmpty()) {
            fields.add(new FieldDef((short) 0x07, (byte) 0x01, p.getCategory().getBytes(StandardCharsets.UTF_8)));
        }
        if (!p.getAmenities().isEmpty()) {
            String amenityStr = String.join(",", p.getAmenities());
            fields.add(new FieldDef((short) 0x08, (byte) 0x01, amenityStr.getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getLongitude() != null) {
            fields.add(new FieldDef((short) 0x09, (byte) 0x03, Helpers.makeF64(p.getLongitude())));
        }
        if (p.getLatitude() != null) {
            fields.add(new FieldDef((short) 0x0A, (byte) 0x03, Helpers.makeF64(p.getLatitude())));
        }
        if (!p.getDates().isEmpty()) {
            String dateStr = String.join(",", p.getDates());
            fields.add(new FieldDef((short) 0x0B, (byte) 0x01, dateStr.getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getAvailability() != null) {
            fields.add(new FieldDef((short) 0x0C, (byte) 0x02, Helpers.makeU8(p.getAvailability())));
        }
        if (p.getFinalPrice() != null) {
            fields.add(new FieldDef((short) 0x0D, (byte) 0x03, Helpers.makeU32(p.getFinalPrice())));
        }
        if (!p.getRateFeature().isEmpty()) {
            String rateFeatureStr = String.join(",", p.getRateFeature());
            fields.add(new FieldDef((short) 0x0E, (byte) 0x01, rateFeatureStr.getBytes(StandardCharsets.UTF_8)));
        }
        if (p.getLimit() != null) {
            fields.add(new FieldDef((short) 0x0F, (byte) 0x03, Helpers.makeU64(p.getLimit())));
        }

        return buildPayload(cmdName, fields.toArray());
    }

    public static List<PropertyAvail> parseSearchAvailResponse(Codecs codecs, String status,
            List<ProtocolTypes.Field> fields) throws RoomzinException {

        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }

        if (fields.isEmpty() || fields.get(0).id != 1 || fields.get(0).fieldType != 0x02
                || fields.get(0).data.length != 2) {
            throw RoomzinException.of("RESPONSE_ERROR: expected num_days field (id=1, type=0x02, len=2)");
        }

        int numDays = ByteBuffer.wrap(fields.get(0).data).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

        List<PropertyAvail> out = new ArrayList<>();
        int idx = 1;

        while (idx < fields.size()) {
            ProtocolTypes.Field f = fields.get(idx);
            if (f.fieldType != 0x01) {
                throw RoomzinException.of("RESPONSE_ERROR: expected property field at index=" + idx + ", got type=0x"
                        + String.format("%02x", f.fieldType) + " id=" + f.id);
            }

            String propId = Helpers.bytesToPropertyID(f.data);
            idx++;

            if (idx >= fields.size()) {
                throw RoomzinException.of("RESPONSE_ERROR: property " + propId + " missing days data");
            }

            ProtocolTypes.Field daysField = fields.get(idx);
            if (daysField.fieldType != 0x08) {
                throw RoomzinException.of(
                        "RESPONSE_ERROR: expected days vector field for property " + propId + ", got type=0x"
                                + String.format("%02x", daysField.fieldType));
            }
            idx++;

            byte[] data = daysField.data;
            if (data.length < 2) {
                throw RoomzinException.of("RESPONSE_ERROR: property " + propId + " days vector too short");
            }

            int daysCount = ByteBuffer.wrap(data, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (daysCount != numDays) {
                throw RoomzinException.of(
                        "RESPONSE_ERROR: property " + propId + " days count mismatch: expected " + numDays + ", got "
                                + daysCount);
            }

            // Updated: 11 bytes per day (date 2 + avail 1 + price 4 + rate_feature u32 4)
            int expectedDataLen = 2 + (11 * daysCount);
            if (data.length != expectedDataLen) {
                throw RoomzinException.of(
                        "RESPONSE_ERROR: property " + propId + " days vector length mismatch: expected "
                                + expectedDataLen + ", got " + data.length);
            }

            List<DayAvail> days = new ArrayList<>();
            int dataCursor = 2;

            for (int d = 0; d < daysCount; d++) {
                if (dataCursor + 11 > data.length) {
                    throw RoomzinException.of("RESPONSE_ERROR: property " + propId + " day " + d + " data truncated");
                }

                short datePacked = ByteBuffer.wrap(data, dataCursor, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                dataCursor += 2;

                short availability = (short) (data[dataCursor] & 0xFF);
                dataCursor += 1;

                int finalPrice = ByteBuffer.wrap(data, dataCursor, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                dataCursor += 4;

                // Now reading full u32 for rate_feature
                int rateFeatureMask = ByteBuffer.wrap(data, dataCursor, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                dataCursor += 4;

                String dateStr = datePackedToString(datePacked);
                List<String> rateFeatureList = bitmaskToRateFeature(codecs, rateFeatureMask);

                days.add(new DayAvail(dateStr, availability, finalPrice, rateFeatureList));
            }

            out.add(new PropertyAvail(propId, days));
        }

        if (idx != fields.size()) {
            throw RoomzinException.of(
                    "RESPONSE_ERROR: extra fields after parsing: consumed=" + idx + " total=" + fields.size());
        }

        return out;
    }

    private static String datePackedToString(short datePacked) {
        int yearOffset = (datePacked >> 9) & 0b111;
        int month = ((datePacked >> 5) & 0b1111) + 1;
        int day = (datePacked & 0b11111) + 1;

        int baseYear = LocalDate.now().getYear();
        try {
            LocalDate date = LocalDate.of(baseYear + yearOffset, month, day);
            // Validate that the date components match (handles invalid dates like Feb 30)
            if (date.getMonthValue() != month || date.getDayOfMonth() != day) {
                throw new IllegalArgumentException("RESPONSE_ERROR: Invalid packed date");
            }
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("RESPONSE_ERROR: Invalid packed date: " + datePacked, e);
        }
    }

    public static List<String> bitmaskToRateFeature(Codecs codecs, int bitmask) {
        if (codecs == null || codecs.getRateFeatures().isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        List<String> rateFeatures = codecs.getRateFeatures();

        for (int i = 0; i < 24 && i < rateFeatures.size(); i++) {
            if ((bitmask & (1 << i)) != 0) {
                result.add(rateFeatures.get(i));
            }
        }

        return result;
    }

    public static byte[] buildSetRoomPkgPayload(Codecs codecs, SetRoomPkgPayload p) throws RoomzinException {
        p.validate(codecs);

        String cmdName = "SETROOMPKG";
        List<Object> fields = new ArrayList<>();
        fields.add(new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)));
        fields.add(new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)));
        fields.add(new FieldDef((short) 0x03, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8)));

        if (p.getAvailability() != null) {
            fields.add(new FieldDef((short) 0x04, (byte) 0x02, Helpers.makeU8(p.getAvailability())));
        }
        if (p.getFinalPrice() != null) {
            fields.add(new FieldDef((short) 0x05, (byte) 0x03, Helpers.makeU32(p.getFinalPrice())));
        }
        if (!p.getRateFeature().isEmpty()) {
            String rateFeatureStr = String.join(",", p.getRateFeature());
            fields.add(new FieldDef((short) 0x06, (byte) 0x01, rateFeatureStr.getBytes(StandardCharsets.UTF_8)));
        }

        return buildPayload(cmdName, fields.toArray());
    }

    public static void parseSetRoomPkgResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildSetRoomAvlPayload(UpdRoomAvlPayload p) throws RoomzinException {
        p.validate();

        String cmdName = "SETROOMAVL";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x03, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x04, (byte) 0x02, Helpers.makeU8(p.getAmount()))
        };
        return buildPayload(cmdName, fields);
    }

    public static short parseSetRoomAvlResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        if (!fields.isEmpty() && fields.get(0).data.length == 1) {
            return (short) (fields.get(0).data[0] & 0xFF);
        }
        throw RoomzinException.of("RESPONSE_ERROR: missing or invalid scalar value");
    }

    public static byte[] buildIncRoomAvlPayload(UpdRoomAvlPayload p) throws RoomzinException {
        p.validate();

        String cmdName = "INCROOMAVL";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x03, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x04, (byte) 0x02, Helpers.makeU8(p.getAmount()))
        };
        return buildPayload(cmdName, fields);
    }

    public static short parseIncRoomAvlResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        if (!fields.isEmpty() && fields.get(0).data.length == 1) {
            return (short) (fields.get(0).data[0] & 0xFF);
        }
        throw RoomzinException.of("RESPONSE_ERROR: missing or invalid scalar value");
    }

    public static byte[] buildDecRoomAvlPayload(UpdRoomAvlPayload p) throws RoomzinException {
        p.validate();

        String cmdName = "DECROOMAVL";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x03, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x04, (byte) 0x02, Helpers.makeU8(p.getAmount()))
        };
        return buildPayload(cmdName, fields);
    }

    public static short parseDecRoomAvlResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        if (!fields.isEmpty() && fields.get(0).data.length == 1) {
            return (short) (fields.get(0).data[0] & 0xFF);
        }
        throw RoomzinException.of("RESPONSE_ERROR: missing or invalid scalar value");
    }

    public static byte[] buildPropExistPayload(String propertyId) throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required");
        }

        String cmdName = "PROPEXIST";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, propertyId.trim().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static boolean parsePropExistResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        return !fields.isEmpty() && fields.get(0).data[0] == 1;
    }

    public static byte[] buildPropRoomExistPayload(PropRoomExistPayload p) throws RoomzinException {
        p.validate();

        String cmdName = "PROPROOMEXIST";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static boolean parsePropRoomExistResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).data != null) {
                throw RoomzinException.of(
                        new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        return !fields.isEmpty() && fields.get(0).data[0] == 1;
    }

    public static byte[] buildPropRoomListPayload(String propertyId) throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required");
        }

        String cmdName = "PROPROOMLIST";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, propertyId.trim().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static List<String> parsePropRoomListResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        List<String> list = new ArrayList<>();
        for (ProtocolTypes.Field f : fields) {
            list.add(new String(f.data, StandardCharsets.UTF_8));
        }
        return list;
    }

    public static byte[] buildPropRoomDateListPayload(PropRoomDateListPayload p) throws RoomzinException {
        p.validate();

        String cmdName = "PROPROOMDATELIST";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static List<String> parsePropRoomDateListResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        List<String> out = new ArrayList<>();
        for (ProtocolTypes.Field f : fields) {
            String s = new String(f.data, StandardCharsets.UTF_8);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    public static byte[] buildDelPropPayload(String propertyId) throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required");
        }

        String cmdName = "DELPROP";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, propertyId.trim().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static void parseDelPropResponse(String status, List<ProtocolTypes.Field> fields) throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildDelSegmentPayload(String segment) throws RoomzinException {
        if (segment == null || segment.trim().isEmpty()) {
            throw RoomzinException.of("Segment is required");
        }

        String cmdName = "DELSEGMENT";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, segment.trim().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static void parseDelSegmentResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildDelPropDayPayload(DelPropDayRequest p) throws RoomzinException {
        p.validate();

        String cmdName = "DELPROPDAY";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static void parseDelPropDayResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildDelPropRoomPayload(DelPropRoomPayload p) throws RoomzinException {
        p.validate();

        String cmdName = "DELPROPROOM";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static void parseDelPropRoomResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildDelRoomDayPayload(DelRoomDayRequest p) throws RoomzinException {
        p.validate();

        String cmdName = "DELROOMDAY";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x03, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static void parseDelRoomDayResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
    }

    public static byte[] buildGetPropRoomDayPayload(GetRoomDayRequest p) throws RoomzinException {
        p.validate();

        String cmdName = "GETPROPROOMDAY";
        Object[] fields = new Object[] {
                new FieldDef((short) 0x01, (byte) 0x01, p.getPropertyId().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x02, (byte) 0x01, p.getRoomType().getBytes(StandardCharsets.UTF_8)),
                new FieldDef((short) 0x03, (byte) 0x01, p.getDate().getBytes(StandardCharsets.UTF_8))
        };
        return buildPayload(cmdName, fields);
    }

    public static GetRoomDayResult parseGetPropRoomDayResponse(Codecs codecs, String status,
            List<ProtocolTypes.Field> fields) throws RoomzinException {

        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }

        if (fields.size() < 5) {
            throw RoomzinException.of("RESPONSE_ERROR: expected 5 fields");
        }

        ProtocolTypes.Field[] chunk = fields.subList(0, 5).toArray(new ProtocolTypes.Field[0]);

        String propId = new String(chunk[0].data, StandardCharsets.UTF_8);
        String date = new String(chunk[1].data, StandardCharsets.UTF_8);
        short avail = (short) (chunk[2].data[0] & 0xFF);
        int price = ByteBuffer.wrap(chunk[3].data).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // rate_feature is now u32 (4 bytes)
        if (chunk[4].data.length != 4) {
            throw RoomzinException.of("RESPONSE_ERROR: invalid rate_feature field length");
        }
        int rateFeatureMask = ByteBuffer.wrap(chunk[4].data).order(ByteOrder.LITTLE_ENDIAN).getInt();

        List<String> rateFeature = bitmaskToRateFeature(codecs, rateFeatureMask);

        return new GetRoomDayResult(propId, date, avail, price, rateFeature);
    }

    public static byte[] buildGetSegmentsPayload() {
        String cmdName = "GETSEGMENTS";
        Object[] fields = new Object[0];
        return buildPayload(cmdName, fields);
    }

    public static List<SegmentInfo> parseGetSegmentsResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }
        if (fields.size() % 2 != 0) {
            throw RoomzinException.of("RESPONSE_ERROR: invalid field count: expected pairs of segment and propCount");
        }
        List<SegmentInfo> list = new ArrayList<>();
        for (int i = 0; i < fields.size(); i += 2) {
            if (fields.get(i).fieldType != 0x01) {
                throw RoomzinException.of(
                        "RESPONSE_ERROR: expected string segment at field " + i + ", got type "
                                + fields.get(i).fieldType);
            }
            String segment = new String(fields.get(i).data, StandardCharsets.UTF_8);
            if (i + 1 >= fields.size()) {
                throw RoomzinException.of("missing propCount field for segment " + segment);
            }
            if (fields.get(i + 1).fieldType != 0x03 || fields.get(i + 1).data.length != 4) {
                throw RoomzinException.of(
                        "RESPONSE_ERROR: expected u32 propCount at field " + (i + 1) + ", got type "
                                + fields.get(i + 1).fieldType);
            }
            int propCount = ByteBuffer.wrap(fields.get(i + 1).data).order(ByteOrder.LITTLE_ENDIAN).getInt();
            list.add(new SegmentInfo(segment, propCount));
        }
        return list;
    }

    public static byte[] buildGetCodecsPayload() {
        String cmdName = "GETCODECS";
        Object[] fields = new Object[0];
        return buildPayload(cmdName, fields);
    }

    public static Codecs parseGetCodecsResponse(String status, List<ProtocolTypes.Field> fields)
            throws RoomzinException {
        if (!"SUCCESS".equals(status)) {
            if (!fields.isEmpty() && fields.get(0).fieldType == 0x01) {
                throw RoomzinException.of(new String(fields.get(0).data, StandardCharsets.UTF_8));
            }
            throw RoomzinException.of("RESPONSE_ERROR");
        }

        if (fields.size() != 1) {
            throw RoomzinException.of("RESPONSE_ERROR: invalid field count: expected 1 field, got " + fields.size());
        }

        ProtocolTypes.Field field = fields.get(0);
        if (field.fieldType != 0x09) {
            throw RoomzinException.of(
                    "RESPONSE_ERROR: expected binary data field (type 0x09), got type " + field.fieldType);
        }

        try {
            String data = new String(field.data, StandardCharsets.UTF_8);
            List<String> rateFeatures = Arrays.asList(data.split(","));
            return new Codecs(rateFeatures);
        } catch (Exception e) {
            throw RoomzinException.of("RESPONSE_ERROR: failed to parse codecs data: " + e.getMessage());
        }
    }
}