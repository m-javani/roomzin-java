package com.roomzin.roomzinjava.types;

import java.util.List;
import java.util.Objects;

public class DayAvail {
    private final String date;
    private final short availability; // uint8 as short
    private final int finalPrice; // uint32 as int
    private final List<String> rateFeature;

    public DayAvail(String date, short availability, int finalPrice, List<String> rateFeature) {
        this.date = date;
        this.availability = availability;
        this.finalPrice = finalPrice;
        this.rateFeature = rateFeature != null ? List.copyOf(rateFeature) : List.of();
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
        DayAvail dayAvail = (DayAvail) o;
        return availability == dayAvail.availability &&
                finalPrice == dayAvail.finalPrice &&
                Objects.equals(date, dayAvail.date) &&
                Objects.equals(rateFeature, dayAvail.rateFeature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, availability, finalPrice, rateFeature);
    }
}