package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Строка воды (раньше — WaterEntry.getWaterStatusText): русская — как раньше, английская — в ml. */
class WaterTextsTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;

    private final WaterTexts waterTexts;

    WaterTextsTest() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        waterTexts = new WaterTexts(new CalorieTexts(new I18nProperties(), settings));
    }

    @Test
    void russianWaterStatusLooksAsBefore() {
        assertThat(waterTexts.buildWaterStatusText(500, 2000, RU_USER))
                .isEqualTo("💧 [🟦🟦🟦⬜⬜⬜⬜⬜⬜⬜] 500 / 2000 мл");
    }

    @Test
    void englishWaterStatusAndDefaultTarget() {
        assertThat(waterTexts.buildWaterStatusText(2500, null, EN_USER))
                .isEqualTo("💧 [🟦🟦🟦🟦🟦🟦🟦🟦🟦🟦] 2500 / 2000 ml");
    }
}
