package com.roomzin.roomzinjava.types;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

public final class Codecs {
    private final List<String> rateFeatures;

    public Codecs(List<String> rateFeatures) {
        this.rateFeatures = rateFeatures != null ? List.copyOf(rateFeatures) : List.of();
    }

    public List<String> getRateFeatures() {
        return rateFeatures;
    }

    public static void validateRateFeatures(Codecs codecs, List<String> input) throws RoomzinException {
        if (codecs == null)
            throw RoomzinException.of("VALIDATION_ERROR: Codecs not initialized");
        if (input == null)
            throw RoomzinException.of("VALIDATION_ERROR: Rate features list is null");

        List<String> invalid = input.stream()
                .filter(r -> !codecs.rateFeatures.contains(r))
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Invalid rate features: " + String.join(", ", invalid));
        }
    }

    public static void validateDate(String date) throws RoomzinException {
        if (date == null || date.isEmpty()) {
            throw RoomzinException.of("VALIDATION_ERROR: Date is null or empty");
        }
        try {
            // Use the same formatter that you use in tomorrow()
            LocalDate parsed = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate today = LocalDate.now();
            if (parsed.isBefore(today)) {
                throw RoomzinException.of("VALIDATION_ERROR: Date is in the past: " + date);
            }
            if (parsed.isAfter(today.plusYears(1))) {
                throw RoomzinException.of("VALIDATION_ERROR: Date is beyond 365 days: " + date);
            }
        } catch (DateTimeParseException e) {
            throw RoomzinException.of("VALIDATION_ERROR: Invalid date format (expected YYYY-MM-DD): " + date);
        }
    }

    public static void validateDates(List<String> dates) throws RoomzinException {
        if (dates == null) {
            throw RoomzinException.of("VALIDATION_ERROR: Dates list is null");
        }
        for (String date : dates) {
            validateDate(date);

        }
    }
}