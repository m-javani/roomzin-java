package com.roomzin.roomzinjava.types;

import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class UpdRoomAvlPayload {
    private final String propertyId;
    private final String roomType;
    private final String date;
    private final short amount;

    private UpdRoomAvlPayload(Builder builder) {
        this.propertyId = builder.propertyId;
        this.roomType = builder.roomType;
        this.date = builder.date;
        this.amount = builder.amount;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getRoomType() {
        return roomType;
    }

    public String getDate() {
        return date;
    }

    public short getAmount() {
        return amount;
    }

    public void validate() throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required; ");
        }
        if (roomType == null || roomType.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: RoomType is required; ");
        }
        if (date == null || date.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Date is required; ");
        }
        Codecs.validateDate(date);
        if (amount < 0) {
            throw RoomzinException.of("VALIDATION_ERROR: Amount cannot be negative; ");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UpdRoomAvlPayload that = (UpdRoomAvlPayload) o;
        return amount == that.amount &&
                Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(roomType, that.roomType) &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, roomType, date, amount);
    }

    public static class Builder {
        private String propertyId = "";
        private String roomType = "";
        private String date = "";
        private short amount;

        public Builder propertyId(String propertyId) {
            this.propertyId = propertyId != null ? propertyId.trim() : "";
            return this;
        }

        public Builder roomType(String roomType) {
            this.roomType = roomType != null ? roomType.trim() : "";
            return this;
        }

        public Builder date(String date) {
            this.date = date != null ? date.trim() : "";
            return this;
        }

        public Builder amount(short amount) {
            this.amount = amount;
            return this;
        }

        public UpdRoomAvlPayload build() {
            return new UpdRoomAvlPayload(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}