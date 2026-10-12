package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.ibm.icu.text.MessageFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Страховка словарей: у каждого языка есть все словари, во всех одинаковые ключи и подстановки,
 * шаблоны разбираются ICU, и каждая константа из *TextKeys есть в словарях.
 */
class TextBundlesConsistencyTest {

    /** Классы с константами ключей. Новый класс ключей — сюда же. */
    private static final List<Class<?>> KEY_CLASSES = List.of(BotTextKeys.class);

    private static final Pattern ARGUMENT = Pattern.compile("\\{\\s*(\\d+)");

    @Test
    void everyLanguageHasSameKeysAsDefault() throws IOException {
        for (String bundle : CalorieTextsService.BUNDLES) {
            Properties reference = load(bundle, AppLanguage.RU);
            for (AppLanguage language : AppLanguage.values()) {
                assertThat(load(bundle, language).stringPropertyNames())
                        .as("keys of %s_%s", bundle, language.getCode())
                        .containsExactlyInAnyOrderElementsOf(reference.stringPropertyNames());
            }
        }
    }

    @Test
    void translationsUseSameArguments() throws IOException {
        for (String bundle : CalorieTextsService.BUNDLES) {
            Properties reference = load(bundle, AppLanguage.RU);
            for (AppLanguage language : AppLanguage.values()) {
                Properties texts = load(bundle, language);
                for (String key : reference.stringPropertyNames()) {
                    assertThat(arguments(texts.getProperty(key)))
                            .as("arguments of '%s' in %s_%s", key, bundle, language.getCode())
                            .isEqualTo(arguments(reference.getProperty(key)));
                }
            }
        }
    }

    @Test
    void everyPatternIsValidIcu() throws IOException {
        for (String bundle : CalorieTextsService.BUNDLES) {
            for (AppLanguage language : AppLanguage.values()) {
                Properties texts = load(bundle, language);
                for (String key : texts.stringPropertyNames()) {
                    String pattern = texts.getProperty(key);
                    try {
                        new MessageFormat(pattern, language.getLocale());
                    } catch (IllegalArgumentException e) {
                        throw new AssertionError("Bad pattern '%s' in %s_%s: %s"
                                .formatted(key, bundle, language.getCode(), e.getMessage()), e);
                    }
                }
            }
        }
    }

    @Test
    void everyKeyConstantExistsInBundles() throws IOException, IllegalAccessException {
        Set<String> allKeys = new TreeSet<>();
        for (String bundle : CalorieTextsService.BUNDLES) {
            allKeys.addAll(load(bundle, AppLanguage.RU).stringPropertyNames());
        }
        List<String> missing = new ArrayList<>();
        for (Class<?> keyClass : KEY_CLASSES) {
            for (Field field : keyClass.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) && Modifier.isFinal(mod) && field.getType() == String.class) {
                    String key = (String) field.get(null);
                    if (!allKeys.contains(key)) missing.add(keyClass.getSimpleName() + "." + field.getName() + " = " + key);
                }
            }
        }
        assertThat(missing).as("key constants without texts").isEmpty();
    }

    private static Properties load(String bundle, AppLanguage language) throws IOException {
        String path = bundle + "_" + language.getCode() + ".properties";
        try (InputStream in = TextBundlesConsistencyTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("bundle %s", path).isNotNull();
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static Set<String> arguments(String pattern) {
        Set<String> result = new TreeSet<>();
        Matcher m = ARGUMENT.matcher(pattern);
        while (m.find()) result.add(m.group(1));
        return result;
    }
}
