package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import lombok.Data;

import javax.swing.text.html.parser.Entity;
import java.time.ZoneId;

@Data
public class UserSettingsDto {
    private Boolean remindersEnabled;
    private String timezone; // Передаем как String ID (н-р, "Europe/Moscow")

    private Boolean dailySummaryEnabled;
    private Integer dailySummaryHour;

    private Boolean mealReminderEnabled;
    private Integer mealReminderIntervalMinutes;
    private UserSettings.UserTheme userTheme;

    public static UserSettingsDto fromEntity(UserSettings entity) {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setRemindersEnabled(entity.isRemindersEnabled());
        dto.setTimezone(entity.getZoneId().getId());
        dto.setDailySummaryEnabled(entity.isDailySummaryEnabled());
        dto.setDailySummaryHour(entity.getDailySummaryHour());
        dto.setMealReminderEnabled(entity.isMealReminderEnabled());
        dto.setMealReminderIntervalMinutes(entity.getMealReminderIntervalMinutes());
        dto.setUserTheme(entity.getUserTheme());
        return dto;
    }

    public UserSettings toEntity() {
        UserSettings entity = new UserSettings();
        entity.setRemindersEnabled(this.remindersEnabled);
        entity.setTimezoneId(this.timezone);
        entity.setDailySummaryEnabled(this.dailySummaryEnabled);
        entity.setDailySummaryHour(this.dailySummaryHour);
        entity.setMealReminderEnabled(this.mealReminderEnabled);
        entity.setMealReminderIntervalMinutes(this.mealReminderIntervalMinutes);
        entity.setUserTheme(this.userTheme);

        return entity;
    }

    public UserSettings mergeToEntity(UserSettings entity) {
        if (entity == null) {
            entity = new UserSettings();
        }

        if (remindersEnabled != null) {
            entity.setRemindersEnabled(remindersEnabled);
        }

        if (timezone != null) {
            entity.setTimezoneId(timezone);
        }

        if (dailySummaryEnabled != null) {
            entity.setDailySummaryEnabled(dailySummaryEnabled);
        }

        if (dailySummaryHour != null) {
            entity.setDailySummaryHour(dailySummaryHour);
        }

        if (mealReminderEnabled != null) {
            entity.setMealReminderEnabled(mealReminderEnabled);
        }

        if (mealReminderIntervalMinutes != null) {
            entity.setMealReminderIntervalMinutes(mealReminderIntervalMinutes);
        }

        if (userTheme != null) {
            entity.setUserTheme(userTheme);
        }

        return entity;
    }
}
