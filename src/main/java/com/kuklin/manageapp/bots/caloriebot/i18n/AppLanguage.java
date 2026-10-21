package com.kuklin.manageapp.bots.caloriebot.i18n;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Языки приложения. Новый язык = константа здесь + словари i18n/calorie/*_<code>.properties
 * (тест TextBundlesConsistencyTest проверит, что в них есть все ключи).
 */
@Getter
@RequiredArgsConstructor
public enum AppLanguage {
    RU("ru", "Русский", "🇷🇺"),
    EN("en", "English", "🇬🇧");

    /** Код как в user_settings.language, в Telegram language_code и во фронте. */
    private final String code;
    /** Название языка на нём самом — для списков выбора и промпта ИИ («отвечай на …»). */
    private final String nativeName;
    /** Флаг на кнопках выбора языка в боте. */
    private final String flag;

    /** «🇬🇧 English» — подпись кнопки выбора языка. */
    public String getLabel() {
        return flag + " " + nativeName;
    }

    public Locale getLocale() {
        return Locale.forLanguageTag(code);
    }

    /** "ru", "RU", "ru-RU", "ru_RU" → RU; пусто или неизвестный язык — empty. */
    public static Optional<AppLanguage> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String base = code.trim().toLowerCase(Locale.ROOT).split("[-_]")[0];
        return Arrays.stream(values()).filter(l -> l.code.equals(base)).findFirst();
    }
}
