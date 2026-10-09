package com.kuklin.manageapp.bots.caloriebot.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;

/**
 * Язык ответа ИИ для инсайтов — язык интерфейса миниаппки/сайта (?lang=ru).
 * Новый язык интерфейса — новая строка здесь.
 */
@Getter
@RequiredArgsConstructor
public enum InsightLanguage {
    EN("en", "English"),
    RU("ru", "Русский");

    /** Код как во фронте и в колонке calorie_ai_insight.language. */
    private final String code;
    /** Как назвать язык в промпте («язык ответа: …»). */
    private final String promptName;

    /** "ru", "RU", "ru-RU" → RU; пусто или неизвестный — EN (интерфейс по умолчанию английский). */
    public static InsightLanguage fromCode(String code) {
        if (code == null || code.isBlank()) return EN;
        String base = code.trim().toLowerCase(Locale.ROOT).split("[-_]")[0];
        return Arrays.stream(values()).filter(l -> l.code.equals(base)).findFirst().orElse(EN);
    }
}
