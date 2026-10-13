package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import org.junit.jupiter.api.Test;

import static com.kuklin.manageapp.bots.caloriebot.components.controller.TelegramWebAppController.getLegalPageLanguage;
import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebAppControllerTest {

    @Test
    void langParamWinsOverAcceptLanguage() {
        assertThat(getLegalPageLanguage("en", "ru-RU,ru;q=0.9")).isEqualTo(AppLanguage.EN);
        assertThat(getLegalPageLanguage("ru", "en-US")).isEqualTo(AppLanguage.RU);
    }

    @Test
    void firstKnownLanguageFromAcceptLanguage() {
        assertThat(getLegalPageLanguage(null, "en-US,en;q=0.9,ru;q=0.8")).isEqualTo(AppLanguage.EN);
        assertThat(getLegalPageLanguage(null, "de-DE,ru;q=0.8,en;q=0.5")).isEqualTo(AppLanguage.RU);
    }

    @Test
    void unknownLanguagesGiveEnglish() {
        assertThat(getLegalPageLanguage("fr", "de-DE,de;q=0.9")).isEqualTo(AppLanguage.EN);
    }

    @Test
    void noHeaderOrBrokenHeaderGivesRussian() {
        assertThat(getLegalPageLanguage(null, null)).isEqualTo(AppLanguage.RU);
        assertThat(getLegalPageLanguage("", " ")).isEqualTo(AppLanguage.RU);
        assertThat(getLegalPageLanguage(null, ";;;q=")).isEqualTo(AppLanguage.RU);
    }
}
