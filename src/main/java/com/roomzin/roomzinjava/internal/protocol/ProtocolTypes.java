package com.roomzin.roomzinjava.internal.protocol;

import java.util.List;

public class ProtocolTypes {
    public static class Header {
        public final int clrId; // uint32
        public final String status;
        public final int fieldCnt; // uint16

        public Header(int clrId, String status, int fieldCnt) {
            this.clrId = clrId;
            this.status = status;
            this.fieldCnt = fieldCnt;
        }
    }

    public static class Field {
        public final int id; // uint16
        public final byte fieldType;
        public final byte[] data;

        public Field(int id, byte fieldType, byte[] data) {
            this.id = id;
            this.fieldType = fieldType;
            this.data = data.clone();
        }
    }

    public static class RawResult {
        public final String status;
        public final List<Field> fields;

        public RawResult(String status, List<Field> fields) {
            this.status = status;
            this.fields = List.copyOf(fields);
        }
    }
}