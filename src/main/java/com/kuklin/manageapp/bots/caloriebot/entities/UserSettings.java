package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    // --- Служебные поля ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- Утилиты ---

    public ZoneId getZoneId() {
        return timezoneId != null ? ZoneId.of(timezoneId) : ZoneId.of("UTC");
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

    public String toPrettyText() {
        StringBuilder sb = new StringBuilder();

        // Общий статус
        sb.append(
                remindersEnabled
                        ? "🔔 Напоминания: 🟢 включены"
                        : "🔕 Напоминания: 🔴 выключены"
        );

        sb.append("\n");
        sb.append("🌍 Таймзона: ").append(getZoneId().getId());

        sb.append("\n\n");

        // --- Daily summary ---
        if (!dailySummaryEnabled) {
            sb.append("📊 Итоги дня: 🔴 выключены");
        } else {
            sb.append("📊 Итоги дня: 🟢 включены\n");
            sb.append("⏰ Время: ")
                    .append(String.format("%02d:00", dailySummaryHour))
                    .append(" (локальное)");
        }

        sb.append("\n\n");

        // --- Meal reminder ---
        if (!mealReminderEnabled) {
            sb.append("🍽 Напоминания о еде: 🔴 выключены");
        } else {
            sb.append("🍽 Напоминания о еде: 🟢 включены\n");

            int minutes = mealReminderIntervalMinutes;
            if (minutes % 60 == 0) {
                sb.append("⏳ Интервал без еды: ")
                        .append(minutes / 60)
                        .append(" ч");
            } else {
                sb.append("⏳ Интервал без еды: ")
                        .append(minutes)
                        .append(" мин");
            }
        }

        return sb.toString();
    }

}
