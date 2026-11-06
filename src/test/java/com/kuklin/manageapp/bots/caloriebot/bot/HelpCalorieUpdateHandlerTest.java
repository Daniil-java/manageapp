package com.kuklin.manageapp.bots.caloriebot.bot;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelpCalorieUpdateHandlerTest {

    private static final Long APP_USER_ID = 1L;

    private final UserSettingsService userSettingsService = mock(UserSettingsService.class);
    private HelpCalorieUpdateHandler handler;

    @BeforeEach
    void setUp() {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), userSettingsService);
        handler = new HelpCalorieUpdateHandler(mock(CalorieTelegramBot.class), texts, mock(CalorieMenuButtons.class));
    }

    @Test
    void helpListsPublicCommandsWithoutItself() {
        String text = handler.buildHelpText(APP_USER_ID);

        assertThat(text)
                .contains("/menu — Меню бота", "/keyboard — Вернуть кнопки внизу", "/support — Написать в поддержку",
                        "/lang", "/terms", "/privacy")
                .doesNotContain("/help")
                // Подстановка {0} раскрыта
                .doesNotContain("{0}");
    }

    @Test
    void helpInUserLanguage() {
        when(userSettingsService.findLanguageCode(APP_USER_ID)).thenReturn(AppLanguage.EN.getCode());

        assertThat(handler.buildHelpText(APP_USER_ID)).contains("Help", "/menu — Bot menu");
    }
}
