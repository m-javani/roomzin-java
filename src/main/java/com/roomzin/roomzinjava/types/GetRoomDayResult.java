package com.roomzin.roomzinjava.types;

import java.util.List;
import java.util.Objects;

public class GetRoomDayResult {
    private final String propertyId;
    private final String date;
    private final short availability;
    private final int finalPrice;
    private final List<String> rateFeature;

    public GetRoomDayResult(String propertyId, String date, short availability, int finalPrice,
            List<String> rateFeature) {
        this.propertyId = propertyId;
        this.date = date;
        this.availability = availability;
        this.finalPrice = finalPrice;
        this.rateFeature = rateFeature != null ? List.copyOf(rateFeature) : List.of();
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getDate() {
        return date;
    }

    public short getAvailability() {
        return availability;
    }

    public int getFinalPrice() {
        return finalPrice;
    }

    public List<String> getRateFeature() {
        return rateFeature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        GetRoomDayResult that = (GetRoomDayResult) o;
        return availability == that.availability &&
                finalPrice == that.finalPrice &&
                Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(date, that.date) &&
                Objects.equals(rateFeature, that.rateFeature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, date, availability, finalPrice, rateFeature);
    }
}