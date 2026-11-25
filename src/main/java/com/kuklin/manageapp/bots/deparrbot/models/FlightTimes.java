package com.kuklin.manageapp.bots.deparrbot.models;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;

/**
 * Время рейса хранится строкой "dd.MM HH:mm" (локальное время аэропорта, без года).
 */
public final class FlightTimes {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm yyyy");

    private FlightTimes() {
    }

    /**
     * Сколько минут между двумя временами (to - from). null, если какое-то не разобрать.
     * Год не хранится, поэтому переход через Новый год учитываем вручную.
     */
    public static Long getMinutesBetweenOrNull(String from, String to) {
        LocalDateTime a = parseOrNull(from);
        LocalDateTime b = parseOrNull(to);
        if (a == null || b == null) return null;
        if (Duration.between(a, b).toDays() > 183) b = b.minusYears(1);
        if (Duration.between(b, a).toDays() > 183) b = b.plusYears(1);
        return Duration.between(a, b).toMinutes();
    }

    /** "08:40", если дата та же, иначе "26.11 00:40". */
    public static String getShortTime(String reference, String time) {
        if (time == null || reference == null || time.length() < 11 || reference.length() < 11) return time;
        return time.substring(0, 5).equals(reference.substring(0, 5)) ? time.substring(6) : time;
    }

    private static LocalDateTime parseOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim() + " " + Year.now().getValue(), FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
