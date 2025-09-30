package com.roomzin.roomzinjava.types;

import java.util.List;
import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class SetPropPayload {
    private final String segment;
    private final String area;
    private final String propertyId;
    private final String propertyType;
    private final String category;
    private final short stars; // uint8
    private final double latitude;
    private final double longitude;
    private final List<String> amenities;

    private SetPropPayload(Builder builder) {
        this.segment = builder.segment;
        this.area = builder.area;
        this.propertyId = builder.propertyId;
        this.propertyType = builder.propertyType;
        this.category = builder.category;
        this.stars = builder.stars;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.amenities = builder.amenities != null ? List.copyOf(builder.amenities) : List.of();
    }

    public String getSegment() {
        return segment;
    }

    public String getArea() {
        return area;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getPropertyType() {
        return propertyType;
    }

    public String getCategory() {
        return category;
    }

    public short getStars() {
        return stars;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public List<String> getAmenities() {
        return amenities;
    }

    public void validate(Codecs codecs) throws RoomzinException {
        if (segment == null || segment.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Segment is required; ");
        }
        if (area == null || area.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Area is required; ");
        }
        if (propertyId == null || propertyId.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyId is required; ");
        }
        if (propertyType == null || propertyType.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: PropertyType is required; ");
        }
        if (category == null || category.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Category is required; ");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SetPropPayload that = (SetPropPayload) o;
        return stars == that.stars &&
                Double.compare(latitude, that.latitude) == 0 &&
                Double.compare(longitude, that.longitude) == 0 &&
                Objects.equals(segment, that.segment) &&
                Objects.equals(area, that.area) &&
                Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(propertyType, that.propertyType) &&
                Objects.equals(category, that.category) &&
                Objects.equals(amenities, that.amenities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segment, area, propertyId, propertyType, category, stars, latitude, longitude, amenities);
    }

    public static class Builder {
        private String segment = "";
        private String area = "";
        private String propertyId = "";
        private String propertyType = "";
        private String category = "";
        private short stars;
        private double latitude;
        private double longitude;
        private List<String> amenities = List.of();

        public Builder segment(String segment) {
            this.segment = segment != null ? segment.trim() : "";
            return this;
        }

        public Builder area(String area) {
            this.area = area != null ? area.trim() : "";
            return this;
        }

        public Builder propertyId(String propertyId) {
            this.propertyId = propertyId != null ? propertyId.trim() : "";
            return this;
        }

        public Builder propertyType(String propertyType) {
            this.propertyType = propertyType != null ? propertyType.trim() : "";
            return this;
        }

        public Builder category(String category) {
            this.category = category != null ? category.trim() : "";
            return this;
        }

        public Builder stars(short stars) {
            this.stars = stars;
            return this;
        }

        public Builder latitude(double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder amenities(List<String> amenities) {
            this.amenities = amenities != null ? amenities : List.of();
            return this;
        }

        public SetPropPayload build() {
            return new SetPropPayload(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}