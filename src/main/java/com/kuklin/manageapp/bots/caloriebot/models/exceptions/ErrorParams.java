package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Параметры ошибок ({@link ErrorResponseException#getParams()}) — единственное место, где заданы их имена.
 * Имя — это контракт сразу для трёх мест: подстановки {…} в i18n/calorie/errors_*.properties,
 * поле params в ответе API (его читает фронт) и код, который бросает ошибку.
 * Поэтому в коде params собираем только методами отсюда, а не Map.of("…", …);
 * что в словарях нет подстановок не из этого списка, проверяет TextBundlesConsistencyTest.
 */
public final class ErrorParams {
    private ErrorParams() {
    }

    /** Лимит в штуках или символах: длина текста, блюд за сутки. */
    public static final String MAX = "max";
    /** Лимит размера файла или запроса в МБ, до десятых: 5, 0.5. */
    public static final String MAX_SIZE_MB = "maxSizeMb";
    /** Примерная длительность голосового, которая влезает в лимит размера, в минутах до десятых. */
    public static final String MAX_DURATION_MINUTES = "maxDurationMinutes";
    /** Допустимые форматы через запятую: "jpeg, png". */
    public static final String FORMATS = "formats";
    /** Суточный лимит обращений к ИИ, который применили к пользователю (с подпиской — больше). */
    public static final String DAILY_LIMIT = "dailyLimit";
    /** Через сколько секунд можно повторить — точно, для клиентов. */
    public static final String RETRY_AFTER_SECONDS = "retryAfterSeconds";
    /** То же время, округлённое вверх до минуты и разбитое на часы и минуты — для текста «через 1 час 2 минуты». */
    public static final String WAIT_HOURS = "waitHours";
    public static final String WAIT_MINUTES = "waitMinutes";

    /** Нижний предел допустимого значения: интервал напоминаний в минутах. */
    public static final String MIN = "min";

    /** {max}: лимит в штуках или символах. */
    public static Map<String, Object> max(int max) {
        return Map.of(MAX, max);
    }

    /** {min} + {max}: допустимый диапазон значения. */
    public static Map<String, Object> range(int min, int max) {
        return Map.of(MIN, min, MAX, max);
    }

    /** {maxSizeMb}: лимит размера. */
    public static Map<String, Object> maxSize(DataSize size) {
        return Map.of(MAX_SIZE_MB, toMegabytes(size));
    }

    /** {maxSizeMb} + {maxDurationMinutes}: лимит голосового — размер и примерно сколько это минут. */
    public static Map<String, Object> maxSizeAndDuration(DataSize size, Duration duration) {
        return Map.of(MAX_SIZE_MB, toMegabytes(size), MAX_DURATION_MINUTES, toMinutes(duration));
    }

    /** {formats}: допустимые форматы. */
    public static Map<String, Object> formats(Collection<String> formats) {
        return Map.of(FORMATS, String.join(", ", formats));
    }

    /** {retryAfterSeconds}, {waitHours}, {waitMinutes}: через сколько повторить. */
    public static Map<String, Object> retryAfter(long seconds) {
        long minutes = (seconds + 59) / 60;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(RETRY_AFTER_SECONDS, seconds);
        params.put(WAIT_HOURS, minutes / 60);
        params.put(WAIT_MINUTES, minutes % 60);
        return params;
    }

    /** Суточный лимит ИИ: через сколько повторить + {dailyLimit}. */
    public static Map<String, Object> dailyLimit(long retryAfterSeconds, int dailyLimit) {
        Map<String, Object> params = retryAfter(retryAfterSeconds);
        params.put(DAILY_LIMIT, dailyLimit);
        return params;
    }

    /** 5MB → 5.0, 500KB → 0.5 (до десятых). */
    private static double toMegabytes(DataSize size) {
        return Math.round(size.toBytes() * 10.0 / DataSize.ofMegabytes(1).toBytes()) / 10.0;
    }

    /** 2m → 2.0, 90s → 1.5 (до десятых). */
    private static double toMinutes(Duration duration) {
        return Math.round(duration.toSeconds() / 6.0) / 10.0;
    }
}
