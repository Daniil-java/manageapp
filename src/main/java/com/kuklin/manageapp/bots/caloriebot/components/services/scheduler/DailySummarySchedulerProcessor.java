package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReportService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.history.TodayUpdateHandler;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Шедулер для ежедневных отчетов
 */
@Component
@AllArgsConstructor
@Slf4j
public class DailySummarySchedulerProcessor implements ScheduleProcessor {
    private final UserSettingsService userSettingsService;
    private final TodayUpdateHandler todayUpdateHandler;
    private final ReportService reportService;
    private final CalorieTelegramBot calorieTelegramBot;

    //TODO сделать отправку не только в телеграм
    @Override
    public void process() {
        boolean isEnabled = true;
        //Получение всех пользователей, у которых включены ежедневные отчеты
        List<UserSettings> users =
                userSettingsService.getAllUserSettingWithEnabledDailySummary(isEnabled);

        Instant nowUtc = Instant.now();

        for (UserSettings settings : users) {
            try {
                processUser(settings, nowUtc);
            } catch (Exception e) {
                log.error(
                        "[DailySummary] Error for userId={}",
                        settings.getUserId(),
                        e
                );
            }
        }
    }

    private void processUser(UserSettings settings, Instant nowUtc) {
        // Глобальный стоп
        if (!settings.isRemindersEnabled() || !settings.isDailySummaryEnabled()) {
            return;
        }

        ZoneId zoneId = settings.getZoneId();
        ZonedDateTime nowUser = nowUtc.atZone(zoneId);

        int targetHour = settings.getDailySummaryHour();

        // Сегодняшнее плановое время
        ZonedDateTime todayTarget = nowUser
                .withHour(targetHour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        // Эффективное целевое время:
        // если ещё не дошли до сегодняшнего — берём вчерашнее
        ZonedDateTime effectiveTarget = nowUser.isBefore(todayTarget)
                ? todayTarget.minusDays(1)
                : todayTarget;

        // Уже отправляли для этого target или позже
        if (settings.getDailyLastReminder() != null) {
            ZonedDateTime last =
                    settings.getDailyLastReminder().atZone(zoneId);

            if (!last.isBefore(effectiveTarget)) {
                return;
            }
        }

        // --- ОТПРАВКА ---
        sendDailySummary(settings);

        // фиксируем факт отправки (UTC)
        userSettingsService.updateDailyLastReminder(settings.getUserId());
    }

    //TODO Универсальное средство отправки
    private void sendDailySummary(UserSettings settings) {
        try {
            todayUpdateHandler.sendTodayMessage(settings.getUserId());
            String dayReport = reportService.getDayAiReport(settings.getUserId()).getOrThrow();
            calorieTelegramBot.sendReturnedMessage(
                    settings.getUserId(),
                    dayReport
            );
        } catch (MissingFeatureException e) {
            log.warn("scheduler day report access denied!");
        }
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
