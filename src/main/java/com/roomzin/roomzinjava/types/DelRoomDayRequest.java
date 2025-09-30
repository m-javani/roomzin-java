package com.roomzin.roomzinjava.types;

import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class DelRoomDayRequest {
    private final String propertyId;
    private final String roomType;
    private final String date;

    private DelRoomDayRequest(Builder builder) {
        this.propertyId = builder.propertyId;
        this.roomType = builder.roomType;
        this.date = builder.date;
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

    public void validate() throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required; ");
        }
        if (roomType == null || roomType.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: roomType is required; ");
        }
        Codecs.validateDate(date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DelRoomDayRequest that = (DelRoomDayRequest) o;
        return Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(roomType, that.roomType) &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, roomType, date);
    }

    public static class Builder {
        private String propertyId = "";
        private String roomType = "";
        private String date = "";

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

        public DelRoomDayRequest build() {
            return new DelRoomDayRequest(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}