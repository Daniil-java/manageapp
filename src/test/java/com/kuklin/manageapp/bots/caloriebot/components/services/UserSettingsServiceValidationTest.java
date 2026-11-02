package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserSettingsDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSettingsServiceValidationTest {

    @Test
    void validSettingsPass() {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setTimezone("Asia/Yekaterinburg");
        dto.setDailySummaryHour(0);
        dto.setMealReminderIntervalMinutes(180);

        assertThatCode(() -> UserSettingsService.validateOrThrow(dto)).doesNotThrowAnyException();
    }

    @Test
    void emptyDtoPasses() {
        // null — поле не меняется
        assertThatCode(() -> UserSettingsService.validateOrThrow(new UserSettingsDto())).doesNotThrowAnyException();
    }

    @Test
    void unknownTimezoneRejected() {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setTimezone("Foo/Bar");

        assertThatThrownBy(() -> UserSettingsService.validateOrThrow(dto))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.INVALID_TIMEZONE));
    }

    @Test
    void hourOutOfRangeRejected() {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setDailySummaryHour(24);

        assertThatThrownBy(() -> UserSettingsService.validateOrThrow(dto))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.INVALID_DAILY_SUMMARY_HOUR));
    }

    @Test
    void intervalOutOfRangeRejected() {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setMealReminderIntervalMinutes(0);

        assertThatThrownBy(() -> UserSettingsService.validateOrThrow(dto))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.INVALID_MEAL_REMINDER_INTERVAL));
    }

    @Test
    void savedInvalidTimezoneFallsBackToDefault() {
        UserSettings settings = new UserSettings().setTimezoneId("Foo/Bar");

        assertThat(settings.getZoneId()).isEqualTo(ZoneId.of(UserSettings.DEF_TIMEZONE));
    }
}
