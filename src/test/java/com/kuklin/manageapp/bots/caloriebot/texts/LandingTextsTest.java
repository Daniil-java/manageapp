package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class LandingTextsTest {
    private static final String TEMPLATE = "templates/calorielanding.html";
    /** Вопросы FAQ шаблон перебирает списком: faq.<id>-q / faq.<id>-a. */
    private static final Set<String> FAQ_IDS = Set.of("install", "accuracy", "premium", "pay", "norm", "data");

    private final LandingTexts texts = new LandingTexts(new I18nProperties());

    @Test
    void everyTemplateKeyHasText() throws IOException {
        String template = new ClassPathResource(TEMPLATE).getContentAsString(StandardCharsets.UTF_8);

        Set<String> pageKeys = findKeys(template, "${t['", "']");
        Set<String> scriptKeys = findKeys(template, "T[\"", "\"]");
        FAQ_IDS.forEach(id -> {
            pageKeys.add("faq." + id + "-q");
            pageKeys.add("faq." + id + "-a");
        });

        assertThat(pageKeys).hasSizeGreaterThan(50);
        assertThat(scriptKeys).hasSizeGreaterThan(30);
        for (AppLanguage language : AppLanguage.values()) {
            assertThat(texts.getPageTexts(language).keySet()).as("page texts, %s", language).containsAll(pageKeys);
            assertThat(texts.getScriptTexts(language).keySet()).as("script texts, %s", language).containsAll(scriptKeys);
        }
    }

    @Test
    void textsComeInPageLanguageWithoutPrefixes() {
        assertThat(texts.getPageTexts(AppLanguage.RU).get("nav.pricing")).isEqualTo("Тарифы");
        assertThat(texts.getPageTexts(AppLanguage.EN).get("nav.pricing")).isEqualTo("Pricing");
        assertThat(texts.getScriptTexts(AppLanguage.EN).get("kcal")).isEqualTo("kcal");
        // Тексты скрипта не попадают в разметку и наоборот
        assertThat(texts.getPageTexts(AppLanguage.RU)).doesNotContainKey("js.kcal");
        assertThat(texts.getScriptTexts(AppLanguage.RU)).doesNotContainKey("nav.pricing");
    }

    @Test
    void htmlAndPlaceholdersStayAsIs() {
        assertThat(texts.getPageTexts(AppLanguage.EN).get("hero.title")).contains("&nbsp;");
        assertThat(texts.getScriptTexts(AppLanguage.RU).get("left")).isEqualTo("Осталось {n} ккал");
        assertThat(texts.getPageTexts(AppLanguage.EN).get("pricing.saving")).isEqualTo("Save {0}%");
    }

    /** Ключи между open и close: ${t['hero.title']} → hero.title. Динамические (с « + ») пропускаем. */
    private static Set<String> findKeys(String template, String open, String close) {
        Set<String> keys = new TreeSet<>();
        int from = template.indexOf(open);
        while (from >= 0) {
            int start = from + open.length();
            int end = template.indexOf(close, start);
            String key = template.substring(start, end);
            if (!key.contains("+")) keys.add(key);
            from = template.indexOf(open, end);
        }
        return keys;
    }
}
