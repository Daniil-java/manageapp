package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Экран настроек (раньше — UserSettings.toPrettyText) и тексты напоминаний. */
class SettingsTextsTest {

    private final SettingsTexts settingsTexts =
            new SettingsTexts(new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class)));

    private static UserSettings settings() {
        return new UserSettings()
                .setRemindersEnabled(true)
                .setTimezoneId("Europe/Moscow")
                .setDailySummaryEnabled(true).setDailySummaryHour(21)
                .setMealReminderEnabled(true).setMealReminderIntervalMinutes(180);
    }

    @Test
    void russianSettingsLookAsBefore() {
        assertThat(settingsTexts.buildSettingsText(settings(), AppLanguage.RU)).isEqualTo("""
                🔔 Напоминания: 🟢 включены
                🌍 Таймзона: Europe/Moscow

                📊 Итоги дня: 🟢 включены
                ⏰ Время: 21:00 (локальное)

                🍽 Напоминания о еде: 🟢 включены
                ⏳ Интервал без еды: 3 ч""");
    }

    @Test
    void mealIntervalInHoursOrMinutes() {
        assertThat(settingsTexts.buildMealReminderText(settings().setMealReminderIntervalMinutes(90), AppLanguage.RU))
                .endsWith("⏳ Интервал без еды: 90 мин");
        assertThat(settingsTexts.buildMealReminderText(settings(), AppLanguage.EN))
                .isEqualTo("🍽 Meal reminders: 🟢 on\n⏳ Interval without food: 3 h");
    }

    @Test
    void remindersScreenHasNoLocalMark() {
        String text = settingsTexts.buildRemindersText(settings(), AppLanguage.RU);

        assertThat(text).contains("⏰ Время: 21:00").doesNotContain("(локальное)").doesNotContain("Таймзона");
    }
}
