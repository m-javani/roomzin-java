package com.roomzin.roomzinjava.types;

import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class PropRoomExistPayload {
    private final String propertyId;
    private final String roomType;

    private PropRoomExistPayload(Builder builder) {
        this.propertyId = builder.propertyId;
        this.roomType = builder.roomType;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getRoomType() {
        return roomType;
    }

    public void validate() throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required; ");
        }
        if (roomType == null || roomType.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: roomType is required; ");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PropRoomExistPayload that = (PropRoomExistPayload) o;
        return Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(roomType, that.roomType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, roomType);
    }

    public static class Builder {
        private String propertyId = "";
        private String roomType = "";

        public Builder propertyId(String propertyId) {
            this.propertyId = propertyId != null ? propertyId.trim() : "";
            return this;
        }

        public Builder roomType(String roomType) {
            this.roomType = roomType != null ? roomType.trim() : "";
            return this;
        }

        public PropRoomExistPayload build() {
            return new PropRoomExistPayload(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}