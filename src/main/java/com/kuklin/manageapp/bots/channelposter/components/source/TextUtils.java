package com.kuklin.manageapp.bots.channelposter.components.source;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Общие помощники парсеров источников: HTML → текст, разбор дат лент.
 */
public final class TextUtils {

    private static final String BLOCKS = "p, h1, h2, h3, h4, li, blockquote";

    private static final List<DateTimeFormatter> FEED_DATE_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            // «Thu, 08 Oct 2026 10:16:25 EDT» — RFC_1123 не понимает названия зон
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
    );

    private TextUtils() {
    }

    /**
     * HTML-фрагмент → текст с переносами между абзацами.
     */
    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.select("script, style, img, figure, iframe").remove();
        StringBuilder sb = new StringBuilder();
        for (Element block : doc.body().select(BLOCKS)) {
            // вложенные блоки (li > p) не дублируем
            if (block.parents().stream().anyMatch(p -> p.is(BLOCKS))) continue;
            String text = block.text().trim();
            if (!text.isEmpty()) {
                sb.append(text).append('\n');
            }
        }
        String result = sb.length() > 0 ? sb.toString().trim() : doc.body().text().trim();
        return result.isEmpty() ? null : result;
    }

    public static Instant parseFeedDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", " ");
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter format : FEED_DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(value, format).toInstant();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
