package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.MessagePattern;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorParams;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Страховка словарей: у каждого языка есть все словари, во всех одинаковые ключи и подстановки,
 * шаблоны разбираются ICU, и каждая константа из *TextKeys есть в словарях.
 * Плюс тексты категорий блюд и ключи {{pdf.…}} из HTML-шаблонов отчётов.
 */
class TextBundlesConsistencyTest {

    /** Классы с константами ключей. Новый класс ключей — сюда же. */
    private static final List<Class<?>> KEY_CLASSES = List.of(BotTextKeys.class, PdfTextKeys.class);
    /** HTML-шаблоны PDF с текстами {{pdf.…}}. */
    static final List<String> PDF_TEMPLATES = List.of("templates/report.html", "templates/weeklyreport.html");

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
            for (String key : stringConstants(keyClass)) {
                if (!allKeys.contains(key)) missing.add(keyClass.getSimpleName() + ": " + key);
            }
        }
        assertThat(missing).as("key constants without texts").isEmpty();
    }

    @Test
    void everyErrorStatusHasText() throws IOException {
        Properties errors = load("i18n/calorie/errors", AppLanguage.RU);
        List<String> missing = new ArrayList<>();
        for (ErrorStatus status : ErrorStatus.values()) {
            if (!errors.containsKey(status.getTextKey())) missing.add(status.getTextKey());
        }
        assertThat(missing).as("ErrorStatus without user text").isEmpty();
    }

    @Test
    void everyFoodCategoryHasText() throws IOException {
        Properties bot = load("i18n/calorie/bot", AppLanguage.RU);
        List<String> missing = new ArrayList<>();
        for (Dish.FoodCategory category : Dish.FoodCategory.values()) {
            if (!bot.containsKey(category.getTextKey())) missing.add(category.getTextKey());
        }
        assertThat(missing).as("FoodCategory without user text").isEmpty();
    }

    /** {{pdf.…}} в HTML-шаблонах отчётов подставляется по ключу — опечатка оставила бы в PDF фигурные скобки. */
    @Test
    void everyTemplateKeyExistsInPdfBundle() throws IOException {
        Properties pdf = load("i18n/calorie/pdf", AppLanguage.RU);
        for (String template : PDF_TEMPLATES) {
            Set<String> keys = PdfTexts.templateKeys(read(template));

            assertThat(keys).as("keys of %s", template).isNotEmpty();
            assertThat(pdf.stringPropertyNames()).as("pdf_ru has all keys of %s", template).containsAll(keys);
        }
    }

    /**
     * В текстах ошибок — только подстановки из ErrorParams: {maxSizeMB} вместо {maxSizeMb}
     * не подставится, и пользователь увидит фигурные скобки. И наоборот: каждый параметр где-то используется.
     */
    @Test
    void errorTextsUseOnlyErrorParams() throws IOException, IllegalAccessException {
        Set<String> paramNames = stringConstants(ErrorParams.class);
        Set<String> used = new TreeSet<>();
        for (AppLanguage language : AppLanguage.values()) {
            Properties errors = load("i18n/calorie/errors", language);
            for (String key : errors.stringPropertyNames()) {
                Set<String> arguments = arguments(errors.getProperty(key));
                assertThat(paramNames)
                        .as("'%s' in errors_%s uses names that are not in ErrorParams", key, language.getCode())
                        .containsAll(arguments);
                used.addAll(arguments);
            }
        }
        assertThat(used).as("ErrorParams used in error texts").containsAll(paramNames);
    }

    /** Значения всех public static final String полей класса — так читаем константы ключей и имён. */
    private static Set<String> stringConstants(Class<?> type) throws IllegalAccessException {
        Set<String> values = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod) && field.getType() == String.class) {
                values.add((String) field.get(null)); // null — поле статическое, объект не нужен
            }
        }
        return values;
    }

    static String read(String resource) throws IOException {
        try (InputStream in = TextBundlesConsistencyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("resource %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    /**
     * Имена подстановок в шаблоне: "Максимум — {max}" → [max], "{0} из {1}" → [0, 1].
     * Шаблон разбирает сам ICU (MessagePattern) — так же, как при выводе текста, поэтому находятся
     * и подстановки внутри вариантов plural, а сами варианты (one{…}, few{…}) подстановками не считаются.
     */
    private static Set<String> arguments(String pattern) {
        MessagePattern parsed = new MessagePattern(pattern);
        Set<String> names = new TreeSet<>();
        for (int i = 0; i < parsed.countParts(); i++) {
            MessagePattern.Part part = parsed.getPart(i);
            boolean isArgument = part.getType() == MessagePattern.Part.Type.ARG_NAME
                    || part.getType() == MessagePattern.Part.Type.ARG_NUMBER;
            if (isArgument) {
                names.add(parsed.getSubstring(part));
            }
        }
        return names;
    }
}
