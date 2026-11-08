package com.repospace.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateUtil {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateUtil() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public static String toDb(LocalDateTime value) {
        return value.toString();
    }

    public static LocalDateTime fromDb(String value) {
        return LocalDateTime.parse(value);
    }

    public static String format(LocalDateTime value) {
        return value == null ? "" : DISPLAY.format(value);
    }
}
