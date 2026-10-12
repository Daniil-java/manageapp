package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.ibm.icu.text.MessageFormat;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Тексты калорийного бота на языке пользователя — одна точка для бота, рассылок, PDF.
 * Обработчики просят текст по ключу (константы из *TextKeys), язык выясняет этот сервис.
 * <p>
 * Язык: user_settings.language → calorie.i18n.default-language.
 * Нет ключа в словаре языка — берём из словаря по умолчанию; нет и там — ключ как есть (и ошибка в лог).
 * <p>
 * Шаблоны — ICU MessageFormat: {0} — подстановка, склонения —
 * {0, plural, one{# день} few{# дня} many{# дней} other{# дня}}.
 * Одинарная кавычка экранирует только перед { } # |, так что «don't» писать можно.
 */
@Slf4j
@Service
public class CalorieTextsService {
    /** Словари: i18n/calorie/<имя>_<код языка>.properties. Новый словарь — сюда же. */
    static final List<String> BUNDLES = List.of(
            "i18n/calorie/bot"
    );

    private final MessageSource messages;
    private final UserSettingsService userSettingsService;
    private final AppLanguage defaultLanguage;

    public CalorieTextsService(I18nProperties properties, UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
        this.defaultLanguage = properties.getDefaultLanguage();
        this.messages = buildMessageSource();
    }

    /** Текст на языке пользователя приложения (appUserId). */
    public String get(Long appUserId, String key, Object... args) {
        return get(languageOf(appUserId), key, args);
    }

    /** Текст на заданном языке — когда пользователя нет (лендинг) или язык уже известен. */
    public String get(AppLanguage language, String key, Object... args) {
        String pattern = findPattern(language, key);
        if (pattern == null && language != defaultLanguage) {
            log.warn("No text '{}' for language {}, using {}", key, language, defaultLanguage);
            language = defaultLanguage;
            pattern = findPattern(language, key);
        }
        if (pattern == null) {
            log.error("No text '{}' in any bundle", key);
            return key;
        }
        return format(language, pattern, args);
    }

    /** Язык пользователя: выбранный в настройках или язык по умолчанию. */
    public AppLanguage languageOf(Long appUserId) {
        if (appUserId == null) return defaultLanguage;
        return AppLanguage.fromCode(userSettingsService.findLanguageCode(appUserId))
                .orElse(defaultLanguage);
    }

    /** Выбрал ли пользователь язык сам (иначе действует язык по умолчанию). */
    public boolean hasChosenLanguage(Long appUserId) {
        return AppLanguage.fromCode(userSettingsService.findLanguageCode(appUserId)).isPresent();
    }

    /** Сохранить язык пользователя; null — сбросить выбор. */
    public void setLanguage(Long appUserId, AppLanguage language) {
        userSettingsService.setLanguageCode(appUserId, language == null ? null : language.getCode());
    }

    public AppLanguage getDefaultLanguage() {
        return defaultLanguage;
    }

    static String format(AppLanguage language, String pattern, Object... args) {
        return new MessageFormat(pattern, language.getLocale()).format(args == null ? new Object[0] : args);
    }

    private String findPattern(AppLanguage language, String key) {
        try {
            // Без аргументов MessageSource отдаёт шаблон как есть — форматирует ICU в format()
            return messages.getMessage(key, null, language.getLocale());
        } catch (NoSuchMessageException e) {
            return null;
        }
    }

    private static MessageSource buildMessageSource() {
        // Свой экземпляр, не бин: общий messageSource занят помидор-ботом (messages_*.properties)
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(BUNDLES.stream().map(b -> "classpath:" + b).toArray(String[]::new));
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(false);
        return source;
    }
}
