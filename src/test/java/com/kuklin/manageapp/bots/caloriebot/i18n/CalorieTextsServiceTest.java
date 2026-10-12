package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalorieTextsServiceTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;
    private static final Long NEW_USER = 3L;
    private static final Long ODD_USER = 4L;

    private UserSettingsService settings;
    private CalorieTextsService texts;

    @BeforeEach
    void setUp() {
        settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        when(settings.findLanguageCode(NEW_USER)).thenReturn(null);
        when(settings.findLanguageCode(ODD_USER)).thenReturn("xx");

        texts = new CalorieTextsService(new I18nProperties(), settings);
    }

    // ── Язык пользователя ──────────────────────────────────────────

    @Test
    void defaultLanguageIsRussian() {
        assertThat(texts.getDefaultLanguage()).isEqualTo(AppLanguage.RU);
    }

    @Test
    void chosenLanguageWins() {
        assertThat(texts.languageOf(EN_USER)).isEqualTo(AppLanguage.EN);
        assertThat(texts.hasChosenLanguage(EN_USER)).isTrue();
    }

    @Test
    void notChosenOrUnknownLanguageFallsBackToDefault() {
        assertThat(texts.languageOf(NEW_USER)).isEqualTo(AppLanguage.RU);
        assertThat(texts.languageOf(ODD_USER)).isEqualTo(AppLanguage.RU);
        assertThat(texts.languageOf(null)).isEqualTo(AppLanguage.RU);
        assertThat(texts.hasChosenLanguage(NEW_USER)).isFalse();
    }

    @Test
    void setLanguageStoresCodeAndResetStoresNull() {
        texts.setLanguage(NEW_USER, AppLanguage.EN);
        verify(settings).setLanguageCode(NEW_USER, "en");

        texts.setLanguage(NEW_USER, null);
        verify(settings).setLanguageCode(NEW_USER, null);
    }

    // ── Тексты ─────────────────────────────────────────────────────

    @Test
    void textComesInUserLanguageWithArguments() {
        assertThat(texts.get(RU_USER, BotTextKeys.DEV_LANG_CHANGED, "X")).isEqualTo("🌐 Язык переключён: X");
        assertThat(texts.get(EN_USER, BotTextKeys.DEV_LANG_CHANGED, "X")).isEqualTo("🌐 Language switched: X");
        assertThat(texts.get(NEW_USER, BotTextKeys.DEV_LANG_CHANGED, "X")).isEqualTo("🌐 Язык переключён: X");
    }

    @Test
    void unknownKeyReturnsKeyItself() {
        assertThat(texts.get(AppLanguage.EN, "no.such.key")).isEqualTo("no.such.key");
    }

    // ── Формат ICU ─────────────────────────────────────────────────

    @Test
    void pluralsFollowLanguageRules() {
        String ru = "{0, plural, one{# день} few{# дня} many{# дней} other{# дня}}";
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 1)).isEqualTo("1 день");
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 3)).isEqualTo("3 дня");
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 5)).isEqualTo("5 дней");
        assertThat(CalorieTextsService.format(AppLanguage.RU, ru, 21)).isEqualTo("21 день");

        String en = "{0, plural, one{# day} other{# days}}";
        assertThat(CalorieTextsService.format(AppLanguage.EN, en, 1)).isEqualTo("1 day");
        assertThat(CalorieTextsService.format(AppLanguage.EN, en, 5)).isEqualTo("5 days");
    }

    @Test
    void singleApostropheIsKeptAsIs() {
        assertThat(CalorieTextsService.format(AppLanguage.EN, "Don't forget {0}", "lunch")).isEqualTo("Don't forget lunch");
    }
}
