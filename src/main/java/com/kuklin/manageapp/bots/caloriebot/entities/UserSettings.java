package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

@Entity
@Table(name = "user_settings")
@Data
@Accessors(chain = true)
public class UserSettings {

    public static final boolean DEF_REMINDERS_ENABLED = true;
    public static final String DEF_TIMEZONE = "Europe/Moscow";

    // daily summary defaults
    public static final boolean DEF_DAILY_SUMMARY_ENABLED = true;
    public static final int DEF_DAILY_SUMMARY_HOUR = 21;        // 21:00

    // meal reminder defaults
    public static final boolean DEF_MEAL_REMINDER_ENABLED = false;
    public static final int DEF_MEAL_REMINDER_INTERVAL_MIN = 180; // 3 часа

    @Id
    private Long userId;

    @Column(name = "timezone_id", nullable = false)
    private String timezoneId;

    // Общий флаг "вообще присылать что-то этому юзеру"
    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled;

    // --- Ежедневный отчет ---
    @Column(name = "daily_summary_enabled", nullable = false)
    private boolean dailySummaryEnabled;

    /**
     * Час локального времени пользователя для ежедневного отчета (0-23).
     */
    @Column(name = "daily_summary_hour", nullable = false)
    private int dailySummaryHour;

    /**
     * Когда в последний раз был отправлен daily-summary (UTC).
     */
    @Column(name = "daily_last_reminder")
    private Instant dailyLastReminder;

    // --- Напоминание о еде, если давно не ел ---
    @Column(name = "meal_reminder_enabled", nullable = false)
    private boolean mealReminderEnabled;

    /**
     * Интервал в минутах без записей о еде, после которого шлём напоминание.
     */
    @Column(name = "meal_reminder_interval_minutes", nullable = false)
    private int mealReminderIntervalMinutes;

    /**
     * Когда в последний раз отправляли напоминание "ты давно не ел" (UTC).
     */
    @Column(name = "meal_last_reminder_utc")
    private Instant mealLastReminderUtc;

    @Column(name = "user_theme")
    @Enumerated(EnumType.STRING)
    private UserTheme userTheme;

    /**
     * Код языка пользователя (ru, en…). null — не выбирал, берётся язык по умолчанию
     * (calorie.i18n.default-language). Читать через {@link com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts}.
     */
    @Column(name = "language")
    private String language;

    // Когда последний раз предлагали пригласить друга (InvitePromptService). Пишется только запросом
    // UserSettingsRepository.claimInvitePrompt, поэтому при сохранении всей сущности не трогается
    @Column(name = "invite_prompt_last_at", insertable = false, updatable = false)
    private Instant invitePromptLastAt;

    // --- Служебные поля ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum UserTheme {
        DAY, NIGHT
    }

    // --- Утилиты ---

    public ZoneId getZoneId() {
        if (timezoneId == null) return ZoneId.of("UTC");
        try {
            return ZoneId.of(timezoneId);
        } catch (DateTimeException e) {
            // Сохранено до проверки в PUT /calorie/settings — не ломаем пользователю все запросы и шедулеры
            return ZoneId.of(DEF_TIMEZONE);
        }
    }

    public static UserSettings createDefaultSettings(Long userId) {
        return new UserSettings()
                .setUserId(userId)
                .setTimezoneId(DEF_TIMEZONE)
                .setRemindersEnabled(DEF_REMINDERS_ENABLED)

                .setDailySummaryEnabled(DEF_DAILY_SUMMARY_ENABLED)
                .setDailySummaryHour(DEF_DAILY_SUMMARY_HOUR)

                .setMealReminderEnabled(DEF_MEAL_REMINDER_ENABLED)
                .setMealReminderIntervalMinutes(DEF_MEAL_REMINDER_INTERVAL_MIN);
    }

}
