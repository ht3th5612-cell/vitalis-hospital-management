package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class Formats {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy");

    private Formats() {
    }

    public static String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    public static String time(LocalTime value) {
        return value == null ? "" : TIME.format(value);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    public static String money(double value) {
        return (value < 0 ? "-" : "") + String.format("$%,.2f", Math.abs(value));
    }

    public static LocalTime parseTime(String text) {
        String value = text == null ? "" : text.trim();
        try {
            return LocalTime.parse(value, TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Time must use the HH:mm format (for example 09:30).");
        }
    }
}
