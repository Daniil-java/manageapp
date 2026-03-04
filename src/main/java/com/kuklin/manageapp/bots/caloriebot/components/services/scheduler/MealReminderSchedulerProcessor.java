package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Шедулер для напоминаний о приеме пищи
 */
@Component
@AllArgsConstructor
@Slf4j
public class MealReminderSchedulerProcessor implements ScheduleProcessor {
    private final UserSettingsService userSettingsService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final Integer QUIET_HOUR_START = 23;
    private final Integer QUIET_HOUR_END = 7;
    @Override
    public void process() {
        //Получение всех пользователей, у которых включены напоминания о приеме пищи
        List<UserSettings> users =
                userSettingsService.getAllUserSettingWithEnabledMealReminder();

        Instant nowUtc = Instant.now();

        for (UserSettings settings : users) {
            try {
                processUser(settings, nowUtc);
            } catch (Exception e) {
                log.error(
                        "[MealReminder] Error for userId={}",
                        settings.getUserId(),
                        e
                );
            }
        }
    }

    private void processUser(UserSettings settings, Instant nowUtc) {

        //Глобальный стоп
        if (!settings.isRemindersEnabled() || !settings.isMealReminderEnabled()) {
            return;
        }

        ZoneId zoneId = settings.getZoneId();
        ZonedDateTime nowUser = nowUtc.atZone(zoneId);
        // --- ТИХИЕ ЧАСЫ: 23:00 – 07:00 ---
        int hour = nowUser.getHour();
        if (hour >= QUIET_HOUR_START || hour < QUIET_HOUR_END) {
            return;
        }

        Instant lastReminderUtc = settings.getMealLastReminderUtc();

        //Если уже слали — проверяем интервал
        if (lastReminderUtc != null) {

            long minutesSinceLast =
                    java.time.Duration.between(lastReminderUtc, nowUtc).toMinutes();

            int intervalMinutes = settings.getMealReminderIntervalMinutes();

            // ещё рано
            if (minutesSinceLast < intervalMinutes) {
                return;
            }
        }

        // --- ОТПРАВКА ---
        calorieTelegramBot.sendReturnedMessage(
                settings.getUserId(),
                "🍽 Ты давно не ел.\nНе забудь добавить приём пищи 🙂"
        );

        // Фиксируем момент отправки (UTC)
        userSettingsService.updateMealLastReminder(settings.getUserId());
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
