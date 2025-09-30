package com.roomzin.roomzinjava.types;

import java.util.List;
import java.util.Objects;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public class SearchAvailPayload {
    private final String segment;
    private final String roomType;
    private final String area;
    private final String propertyId;
    private final String type;
    private final Short stars;
    private final String category;
    private final List<String> amenities;
    private final Double longitude;
    private final Double latitude;
    private final List<String> dates;
    private final Short availability;
    private final Integer finalPrice;
    private final List<String> rateFeature;
    private final Long limit;

    private SearchAvailPayload(Builder builder) {
        this.segment = builder.segment;
        this.roomType = builder.roomType;
        this.area = builder.area;
        this.propertyId = builder.propertyId;
        this.type = builder.type;
        this.stars = builder.stars;
        this.category = builder.category;
        this.amenities = builder.amenities != null ? List.copyOf(builder.amenities) : List.of();
        this.longitude = builder.longitude;
        this.latitude = builder.latitude;
        this.dates = builder.dates != null ? List.copyOf(builder.dates) : List.of();
        this.availability = builder.availability;
        this.finalPrice = builder.finalPrice;
        this.rateFeature = builder.rateFeature != null ? List.copyOf(builder.rateFeature) : List.of();
        this.limit = builder.limit;
    }

    public String getSegment() {
        return segment;
    }

    public String getRoomType() {
        return roomType;
    }

    public String getArea() {
        return area;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getType() {
        return type;
    }

    public Short getStars() {
        return stars;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getAmenities() {
        return amenities;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public List<String> getDates() {
        return dates;
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

    public Long getLimit() {
        return limit;
    }

    public void validate(Codecs codecs) throws RoomzinException {
        if (segment == null || segment.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Segment is required; ");
        }
        if (roomType == null || roomType.trim().isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: RoomType is required; ");
        }
        Codecs.validateDates(dates);

        Codecs.validateRateFeatures(codecs, rateFeature);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SearchAvailPayload that = (SearchAvailPayload) o;
        return Objects.equals(segment, that.segment) &&
                Objects.equals(roomType, that.roomType) &&
                Objects.equals(area, that.area) &&
                Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(type, that.type) &&
                Objects.equals(stars, that.stars) &&
                Objects.equals(category, that.category) &&
                Objects.equals(amenities, that.amenities) &&
                Objects.equals(longitude, that.longitude) &&
                Objects.equals(latitude, that.latitude) &&
                Objects.equals(dates, that.dates) &&
                Objects.equals(availability, that.availability) &&
                Objects.equals(finalPrice, that.finalPrice) &&
                Objects.equals(rateFeature, that.rateFeature) &&
                Objects.equals(limit, that.limit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segment, roomType, area, propertyId, type, stars, category, amenities,
                longitude, latitude, dates, availability, finalPrice, rateFeature, limit);
    }

    public static class Builder {
        private String segment = "";
        private String roomType = "";
        private String area = "";
        private String propertyId;
        private String type;
        private Short stars;
        private String category;
        private List<String> amenities = List.of();
        private Double longitude;
        private Double latitude;
        private List<String> dates = List.of();
        private Short availability;
        private Integer finalPrice;
        private List<String> rateFeature = List.of();
        private Long limit;

        public Builder segment(String segment) {
            this.segment = segment != null ? segment.trim() : "";
            return this;
        }

        public Builder roomType(String roomType) {
            this.roomType = roomType != null ? roomType.trim() : "";
            return this;
        }

        public Builder area(String area) {
            this.area = area != null ? area.trim() : "";
            return this;
        }

        public Builder propertyId(String propertyId) {
            this.propertyId = propertyId != null ? propertyId.trim() : null;
            return this;
        }

        public Builder type(String type) {
            this.type = type != null ? type.trim() : null;
            return this;
        }

        public Builder stars(Short stars) {
            this.stars = stars;
            return this;
        }

        public Builder category(String category) {
            this.category = category != null ? category.trim() : null;
            return this;
        }

        public Builder amenities(List<String> amenities) {
            this.amenities = amenities != null ? amenities : List.of();
            return this;
        }

        public Builder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder dates(List<String> dates) {
            this.dates = dates != null ? dates : List.of();
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

        public Builder limit(Long limit) {
            this.limit = limit;
            return this;
        }

        public SearchAvailPayload build() {
            return new SearchAvailPayload(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}