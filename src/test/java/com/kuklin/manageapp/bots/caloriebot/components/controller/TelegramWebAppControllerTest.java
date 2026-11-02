package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import org.junit.jupiter.api.Test;

import static com.kuklin.manageapp.bots.caloriebot.components.controller.TelegramWebAppController.getPageLanguage;
import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebAppControllerTest {

    @Test
    void langParamWinsOverAcceptLanguage() {
        assertThat(getPageLanguage("en", "ru-RU,ru;q=0.9")).isEqualTo(AppLanguage.EN);
        assertThat(getPageLanguage("ru", "en-US")).isEqualTo(AppLanguage.RU);
    }

    @Test
    void firstKnownLanguageFromAcceptLanguage() {
        assertThat(getPageLanguage(null, "en-US,en;q=0.9,ru;q=0.8")).isEqualTo(AppLanguage.EN);
        assertThat(getPageLanguage(null, "de-DE,ru;q=0.8,en;q=0.5")).isEqualTo(AppLanguage.RU);
    }

    @Test
    void unknownLanguagesGiveEnglish() {
        assertThat(getPageLanguage("fr", "de-DE,de;q=0.9")).isEqualTo(AppLanguage.EN);
    }

    @Test
    void noHeaderOrBrokenHeaderGivesRussian() {
        assertThat(getPageLanguage(null, null)).isEqualTo(AppLanguage.RU);
        assertThat(getPageLanguage("", " ")).isEqualTo(AppLanguage.RU);
        assertThat(getPageLanguage(null, ";;;q=")).isEqualTo(AppLanguage.RU);
    }
}
