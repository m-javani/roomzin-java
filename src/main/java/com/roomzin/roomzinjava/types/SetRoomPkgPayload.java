package com.roomzin.roomzinjava.types;

import java.util.List;
import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class SetRoomPkgPayload {
    private final String propertyId;
    private final String roomType;
    private final String date;
    private final Short availability;
    private final Integer finalPrice;
    private final List<String> rateFeature;

    private SetRoomPkgPayload(Builder builder) {
        this.propertyId = builder.propertyId;
        this.roomType = builder.roomType;
        this.date = builder.date;
        this.availability = builder.availability;
        this.finalPrice = builder.finalPrice;
        this.rateFeature = builder.rateFeature != null ? List.copyOf(builder.rateFeature) : List.of();
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

    public Short getAvailability() {
        return availability;
    }

    public Integer getFinalPrice() {
        return finalPrice;
    }

    public List<String> getRateFeature() {
        return rateFeature;
    }

    public void validate(Codecs codecs) throws RoomzinException {
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

        if (availability != null && availability < 0) {
            throw RoomzinException.of("VALIDATION_ERROR: Availability cannot be negative; ");
        }
        if (finalPrice != null && finalPrice < 0) {
            throw RoomzinException.of("VALIDATION_ERROR: FinalPrice cannot be negative; ");
        }
        Codecs.validateRateFeatures(codecs, rateFeature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SetRoomPkgPayload that = (SetRoomPkgPayload) o;
        return Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(roomType, that.roomType) &&
                Objects.equals(date, that.date) &&
                Objects.equals(availability, that.availability) &&
                Objects.equals(finalPrice, that.finalPrice) &&
                Objects.equals(rateFeature, that.rateFeature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, roomType, date, availability, finalPrice, rateFeature);
    }

    public static class Builder {
        private String propertyId = "";
        private String roomType = "";
        private String date = "";
        private Short availability;
        private Integer finalPrice;
        private List<String> rateFeature = List.of();

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

        public Builder availability(Short availability) {
            this.availability = availability;
            return this;
        }

        public Builder finalPrice(Integer finalPrice) {
            this.finalPrice = finalPrice;
            return this;
        }

        public Builder rateFeature(List<String> rateFeature) {
            this.rateFeature = rateFeature != null ? rateFeature : List.of();
            return this;
        }

        public SetRoomPkgPayload build() {
            return new SetRoomPkgPayload(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}