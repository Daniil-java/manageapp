package com.kuklin.manageapp.bots.deparrbot.providers;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Общее для провайдеров, которые читают JSON, встроенный в страницу сайта:
 * загрузка страницы, вырезание JSON-объекта и единый формат полей,
 * чтобы смена провайдера не выглядела для подписчика как изменение рейса.
 */
public final class FlightPageSupport {
    public static final String STATUS_SCHEDULED = "По расписанию";
    public static final String STATUS_EN_ROUTE = "В пути";
    public static final String STATUS_ARRIVED = "Прибыл";
    public static final String STATUS_CANCELLED = "Отменён";
    public static final String STATUS_DIVERTED = "Перенаправлен";

    private static final Duration STALE_AFTER = Duration.ofDays(2);
    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private FlightPageSupport() {
    }

    public static String fetchPage(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(TIMEOUT_MS)
                .maxBodySize(0)
                .execute()
                .body();
    }

    /**
     * Находит marker и возвращает первый JSON-объект после него (по балансу скобок, с учётом строк).
     */
    public static String extractJsonObjectOrNull(String html, String marker) {
        int markerIndex = html.indexOf(marker);
        if (markerIndex < 0) return null;
        int start = html.indexOf('{', markerIndex);
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return html.substring(start, i + 1);
        }
        return null;
    }

    /**
     * Если номер сейчас не летает, сайты показывают последний известный рейс (бывает, что
     * полугодовой давности). Такой результат считаем ненайденным.
     */
    public static boolean isStale(Instant scheduledDeparture) {
        return scheduledDeparture != null
                && scheduledDeparture.isBefore(Instant.now().minus(STALE_AFTER));
    }

    public static String formatLocalTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    public static String formatEpochTime(long epochSeconds, ZoneId zone) {
        if (epochSeconds <= 0) return null;
        return formatLocalTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone));
    }

    public static String buildAirport(String iata, String city) {
        if (iata == null || iata.isBlank()) return city;
        if (city == null || city.isBlank()) return iata;
        return iata + " " + city;
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
