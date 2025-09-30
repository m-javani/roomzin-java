package com.roomzin.roomzinjava.types;

import java.util.List;
import java.util.Objects;

public class PropertyAvail {
    private final String propertyId;
    private final List<DayAvail> days;

    public PropertyAvail(String propertyId, List<DayAvail> days) {
        this.propertyId = propertyId;
        this.days = days != null ? List.copyOf(days) : List.of();
    }

    public String getPropertyId() {
        return propertyId;
    }

    public List<DayAvail> getDays() {
        return days;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PropertyAvail that = (PropertyAvail) o;
        return Objects.equals(propertyId, that.propertyId) &&
                Objects.equals(days, that.days);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyId, days);
    }
}