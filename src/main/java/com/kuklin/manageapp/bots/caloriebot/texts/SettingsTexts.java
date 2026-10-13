package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Тексты настроек: экран настроек (раньше — UserSettings.toPrettyText) и статусы напоминаний.
 * Строки про итоги дня и напоминания о еде общие для экрана настроек и экрана напоминаний.
 */
@Component
public class SettingsTexts extends AbstractBotTexts {

    public SettingsTexts(CalorieTexts texts) {
        super(texts);
    }

    /** Экран настроек: напоминания, таймзона, язык, итоги дня, напоминания о еде. */
    public String buildSettingsText(UserSettings settings, AppLanguage language) {
        return getRemindersStatus(settings, language) + "\n"
                + texts.get(language, BotTextKeys.SETTINGS_TIMEZONE, settings.getZoneId().getId()) + "\n"
                + texts.get(language, BotTextKeys.SETTINGS_LANGUAGE, language.getLabel())
                + "\n\n"
                + buildDailySummaryText(settings, language, true)
                + "\n\n"
                + buildMealReminderText(settings, language);
    }

    /** Экран напоминаний: общий статус, итоги дня, напоминания о еде. */
    public String buildRemindersText(UserSettings settings, AppLanguage language) {
        return getRemindersStatus(settings, language)
                + "\n\n"
                + buildDailySummaryText(settings, language, false)
                + "\n\n"
                + buildMealReminderText(settings, language);
    }

    /** «📊 Итоги дня: 🟢 включены / ⏰ Время: 21:00». withLocalMark — приписать «(локальное)», как на экране настроек. */
    public String buildDailySummaryText(UserSettings settings, AppLanguage language, boolean withLocalMark) {
        if (!settings.isDailySummaryEnabled()) {
            return texts.get(language, BotTextKeys.SETTINGS_DAILY_OFF);
        }
        String time = String.format("%02d:00", settings.getDailySummaryHour());
        return texts.get(language, BotTextKeys.SETTINGS_DAILY_ON) + "\n"
                + texts.get(language, withLocalMark ? BotTextKeys.SETTINGS_DAILY_TIME_LOCAL : BotTextKeys.SETTINGS_DAILY_TIME, time);
    }

    /** «🍽 Напоминания о еде: 🟢 включены / ⏳ Интервал без еды: 3 ч». */
    public String buildMealReminderText(UserSettings settings, AppLanguage language) {
        if (!settings.isMealReminderEnabled()) {
            return texts.get(language, BotTextKeys.SETTINGS_MEAL_OFF);
        }
        String interval = getDurationText(Duration.ofMinutes(settings.getMealReminderIntervalMinutes()), language);
        return texts.get(language, BotTextKeys.SETTINGS_MEAL_ON) + "\n"
                + texts.get(language, BotTextKeys.SETTINGS_MEAL_INTERVAL, interval);
    }

    private String getRemindersStatus(UserSettings settings, AppLanguage language) {
        return texts.get(language, settings.isRemindersEnabled()
                ? BotTextKeys.SETTINGS_REMINDERS_ON
                : BotTextKeys.SETTINGS_REMINDERS_OFF);
    }
}
