package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReportService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history.TodayUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalorieRemindersInactiveUsersTest {

    private static final long USER_ID = 7L;
    private static final long CHAT_ID = 700L;

    private final UserSettingsService userSettingsService = mock(UserSettingsService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final DishService dishService = mock(DishService.class);

    @BeforeEach
    void telegramUser() {
        TelegramUser tgUser = mock(TelegramUser.class);
        when(tgUser.getTelegramId()).thenReturn(CHAT_ID);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(tgUser));
    }

    private void lastDish(Instant at) {
        when(dishService.getLastDishTimeOrNull(USER_ID)).thenReturn(at);
    }

    @Nested
    class DailySummary {
        private final TodayUpdateHandler todayUpdateHandler = mock(TodayUpdateHandler.class);
        private final ReportService reportService = mock(ReportService.class);
        private final DailySummarySchedulerProcessor processor = new DailySummarySchedulerProcessor(
                userSettingsService, todayUpdateHandler, reportService, bot, telegramUserService, dishService);

        @BeforeEach
        void settings() {
            // Отчёт в 00:00 UTC — сейчас всегда после него, день отчёта — сегодня
            UserSettings settings = new UserSettings().setUserId(USER_ID).setTimezoneId("UTC")
                    .setRemindersEnabled(true).setDailySummaryEnabled(true).setDailySummaryHour(0);
            when(userSettingsService.getAllUserSettingWithEnabledDailySummary(true)).thenReturn(List.of(settings));
            when(reportService.getDayAiReport(USER_ID)).thenReturn(AccessResult.success("day report"));
        }

        @Test
        void dishTodaySendsReport() {
            lastDish(Instant.now().minusSeconds(1));

            processor.process();

            verify(todayUpdateHandler).sendTodayMessage(CHAT_ID, USER_ID);
            verify(reportService).getDayAiReport(USER_ID);
            verify(userSettingsService).updateDailyLastReminder(USER_ID);
        }

        @Test
        void noDishTodayNoReportNoAiButDayMarkedDone() {
            lastDish(ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).minusMinutes(1).toInstant());

            processor.process();

            verifyNoInteractions(todayUpdateHandler, reportService);
            verify(userSettingsService).updateDailyLastReminder(USER_ID);
        }

        @Test
        void neverLoggedFoodNoReport() {
            lastDish(null);

            processor.process();

            verifyNoInteractions(todayUpdateHandler, reportService);
        }
    }

    @Nested
    class MealReminder {
        private final MealReminderSchedulerProcessor processor =
                new MealReminderSchedulerProcessor(userSettingsService, bot, telegramUserService, dishService);

        @BeforeEach
        void settings() {
            // Пояс, где сейчас полдень — вне тихих часов 23:00–07:00
            int utcHour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
            UserSettings settings = new UserSettings().setUserId(USER_ID)
                    .setTimezoneId(ZoneOffset.ofHours(12 - utcHour).getId())
                    .setRemindersEnabled(true).setMealReminderEnabled(true).setMealReminderIntervalMinutes(180);
            when(userSettingsService.getAllUserSettingWithEnabledMealReminder()).thenReturn(List.of(settings));
        }

        @Test
        void ateLongerThanIntervalAgoGetsReminder() {
            lastDish(Instant.now().minus(Duration.ofHours(4)));

            processor.process();

            verify(bot).sendReturnedMessage(eq(CHAT_ID), anyString());
            verify(userSettingsService).updateMealLastReminder(USER_ID);
        }

        @Test
        void ateRecentlyNoReminder() {
            lastDish(Instant.now().minus(Duration.ofMinutes(30)));

            processor.process();

            verify(bot, never()).sendReturnedMessage(anyLong(), anyString());
            verify(userSettingsService, never()).updateMealLastReminder(any());
        }

        @Test
        void inactiveForWeekNoReminder() {
            lastDish(Instant.now().minus(Duration.ofDays(MealReminderSchedulerProcessor.INACTIVE_DAYS)).minusSeconds(60));

            processor.process();

            verify(bot, never()).sendReturnedMessage(anyLong(), anyString());
        }

        @Test
        void neverLoggedFoodNoReminder() {
            lastDish(null);

            processor.process();

            verify(bot, never()).sendReturnedMessage(anyLong(), anyString());
        }
    }
}
