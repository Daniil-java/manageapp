package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.ibm.icu.text.MessageFormat;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
public class CalorieTexts {
    /** Словари: i18n/calorie/<имя>_<код языка>.properties. Новый словарь — сюда же. */
    static final List<String> BUNDLES = List.of(
            "i18n/calorie/bot",
            "i18n/calorie/errors",
            "i18n/calorie/pdf",
            "i18n/calorie/plans"
    );

    private final I18nProperties properties;
    private final UserSettingsService userSettingsService;
    // Свой словарь калорийного бота, не общий бин MessageSource — собирается здесь, в конструктор не попадает
    private final MessageSource messages = buildMessageSource();

    /** Текст на языке пользователя приложения (appUserId). */
    public String get(Long appUserId, String key, Object... args) {
        return get(languageOf(appUserId), key, args);
    }

    /** Текст на заданном языке — когда пользователя нет (лендинг) или язык уже известен. */
    public String get(AppLanguage language, String key, Object... args) {
        Template template = findTemplate(language, key);
        return template == null ? key : format(template.language(), template.pattern(), args);
    }

    /**
     * Текст на заданном языке или null, если ключа нет ни в словаре языка, ни в словаре по умолчанию.
     * Для ключей, которых может не быть (тарифы из БД), — без ошибки в лог: вызывающий берёт свой запасной текст.
     */
    public String findOrNull(AppLanguage language, String key, Object... args) {
        String pattern = findPattern(language, key);
        if (pattern == null) {
            language = getDefaultLanguage();
            pattern = findPattern(language, key);
        }
        return pattern == null ? null : format(language, pattern, args);
    }

    /** Текст с именованными подстановками ({max}, {waitMinutes}) на языке пользователя. */
    public String get(Long appUserId, String key, Map<String, ?> params) {
        return get(languageOf(appUserId), key, params);
    }

    /** Текст с именованными подстановками на заданном языке. */
    public String get(AppLanguage language, String key, Map<String, ?> params) {
        Template template = findTemplate(language, key);
        return template == null ? key : format(template.language(), template.pattern(), params);
    }

    /** Текст ошибки для пользователя (словарь errors_*) — со значениями из params исключения. */
    public String error(Long appUserId, ErrorResponseException e) {
        return get(appUserId, e.getErrorStatus().getTextKey(), e.getParams());
    }

    /**
     * Текст ошибки для поля message в ответе API — всегда по-английски (errors_en): фронт сам переводит по коду
     * и params, а английский показывает как есть. Переносы строк (они для бота) склеиваем в пробел — в API одна строка.
     */
    public String errorForApi(ErrorResponseException e) {
        String text = get(AppLanguage.EN, e.getErrorStatus().getTextKey(), e.getParams());
        return String.join(" ", text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList());
    }

    /** Текст ошибки без значений — когда исключения нет, а код ошибки известен. */
    public String error(Long appUserId, ErrorStatus status) {
        return get(appUserId, status.getTextKey(), Map.of());
    }

    /** Язык пользователя: выбранный в настройках или язык по умолчанию. */
    public AppLanguage languageOf(Long appUserId) {
        if (appUserId == null) return getDefaultLanguage();
        return AppLanguage.fromCode(userSettingsService.findLanguageCode(appUserId))
                .orElseGet(this::getDefaultLanguage);
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
        return properties.getDefaultLanguage();
    }

    static String format(AppLanguage language, String pattern, Object... args) {
        return new MessageFormat(pattern, language.getLocale()).format(args == null ? new Object[0] : args);
    }

    static String format(AppLanguage language, String pattern, Map<String, ?> params) {
        // ICU принимает только Map<String, Object> — копируем значения в мапу этого типа
        return new MessageFormat(pattern, language.getLocale()).format(new HashMap<String, Object>(params));
    }

    /** Шаблон на нужном языке; нет ключа — из словаря по умолчанию; нет и там — null (и ошибка в лог). */
    private Template findTemplate(AppLanguage language, String key) {
        String pattern = findPattern(language, key);
        AppLanguage defaultLanguage = getDefaultLanguage();
        if (pattern == null && language != defaultLanguage) {
            log.warn("No text '{}' for language {}, using {}", key, language, defaultLanguage);
            language = defaultLanguage;
            pattern = findPattern(language, key);
        }
        if (pattern == null) {
            log.error("No text '{}' in any bundle", key);
            return null;
        }
        return new Template(language, pattern);
    }

    private record Template(AppLanguage language, String pattern) {
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
