package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тексты лендинга для шаблона calorielanding.html — словарь i18n/calorie/landing_*.properties целиком.
 * Страница просит десятки текстов разом, поэтому они отдаются картой, а не по ключу через CalorieTexts,
 * и как есть, без ICU: в них бывает HTML, а {n} в текстах скрипта подставляет сама страница.
 * Нет ключа в словаре языка — текст из словаря по умолчанию.
 */
@Component
@RequiredArgsConstructor
public class LandingTexts {
    static final String BUNDLE = "i18n/calorie/landing";
    private static final String PAGE_PREFIX = "landing.";
    private static final String SCRIPT_PREFIX = "landing.js.";

    private final I18nProperties properties;
    private final Map<AppLanguage, Map<String, String>> pageTexts = new ConcurrentHashMap<>();
    private final Map<AppLanguage, Map<String, String>> scriptTexts = new ConcurrentHashMap<>();

    /** Тексты разметки: «hero.title» → текст (ключ без «landing.»). В шаблоне — ${t['hero.title']}. */
    public Map<String, String> getPageTexts(AppLanguage language) {
        return pageTexts.computeIfAbsent(language, l -> buildTexts(l, false));
    }

    /** Тексты скрипта: «kcal» → текст (ключ без «landing.js.»). В скрипте — T["kcal"]. */
    public Map<String, String> getScriptTexts(AppLanguage language) {
        return scriptTexts.computeIfAbsent(language, l -> buildTexts(l, true));
    }

    private Map<String, String> buildTexts(AppLanguage language, boolean script) {
        Map<String, String> all = new HashMap<>(readBundle(properties.getDefaultLanguage()));
        all.putAll(readBundle(language));

        String prefix = script ? SCRIPT_PREFIX : PAGE_PREFIX;
        Map<String, String> result = new HashMap<>();
        all.forEach((key, text) -> {
            if (key.startsWith(prefix) && key.startsWith(SCRIPT_PREFIX) == script) {
                result.put(key.substring(prefix.length()), text);
            }
        });
        return Map.copyOf(result);
    }

    static Map<String, String> readBundle(AppLanguage language) {
        ClassPathResource resource = new ClassPathResource(BUNDLE + "_" + language.getCode() + ".properties");
        if (!resource.exists()) return Map.of();
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            Properties bundle = new Properties();
            bundle.load(reader);
            Map<String, String> result = new HashMap<>();
            bundle.forEach((key, text) -> result.put((String) key, (String) text));
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + resource.getPath(), e);
        }
    }
}
