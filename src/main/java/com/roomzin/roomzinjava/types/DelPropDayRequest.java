package com.roomzin.roomzinjava.types;

import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class DelPropDayRequest {
    private final String propertyId;
    private final String date;

    private DelPropDayRequest(Builder builder) {
        this.propertyId = builder.propertyId;
        this.date = builder.date;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getDate() {
        return date;
    }

    public void validate() throws RoomzinException {
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required;");
        }
        Codecs.validateDate(date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DelPropDayRequest that = (DelPropDayRequest) o;
        return Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, date);
    }

    public static class Builder {
        private String propertyId = "";
        private String date = "";

        public Builder propertyId(String propertyId) {
            this.propertyId = propertyId != null ? propertyId.trim() : "";
            return this;
        }

        public Builder date(String date) {
            this.date = date != null ? date.trim() : "";
            return this;
        }

        public DelPropDayRequest build() {
            return new DelPropDayRequest(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}