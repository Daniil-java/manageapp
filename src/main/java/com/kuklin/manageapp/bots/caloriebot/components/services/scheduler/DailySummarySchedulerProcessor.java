package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReportService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history.TodayUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Шедулер для ежедневных отчетов.
 * Отчёт (экран «Сегодня» + ИИ-разбор дня) — только если за день отчёта есть хотя бы одно блюдо:
 * неактивным пользователям отчёты не уходят и ИИ на них не тратится (08.10 — 98 отчётов в 21:00 МСК,
 * при этом остальных обращений к ИИ за сутки — около десятка).
 * <p>
 * Отчёты уходят параллельно, по {@code calorie.daily-summary.parallelism} одновременно: ИИ отвечает 10–30 с,
 * и по одному 100 отчётов с одинаковым часом расходились 15–50 минут. Больше 5 сразу — риск упереться в лимиты OpenAI.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailySummarySchedulerProcessor implements ScheduleProcessor {
    private final UserSettingsService userSettingsService;
    private final TodayUpdateHandler todayUpdateHandler;
    private final ReportService reportService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final TelegramUserService telegramUserService;
    private final DishService dishService;

    @Value("${calorie.daily-summary.parallelism:5}")
    private int parallelism = 5;

    //TODO сделать отправку не только в телеграм
    @Override
    public void process() {
        boolean isEnabled = true;
        //Получение всех пользователей, у которых включены ежедневные отчеты
        List<UserSettings> users =
                userSettingsService.getAllUserSettingWithEnabledDailySummary(isEnabled);

        Instant nowUtc = Instant.now();

        // Кому пора и есть что отправлять — решаем здесь, быстрыми запросами к БД; долгое (ИИ) — в пуле
        List<UserSettings> toSend = new ArrayList<>();
        for (UserSettings settings : users) {
            try {
                if (isReportDue(settings, nowUtc)) toSend.add(settings);
            } catch (Exception e) {
                log.error("[DailySummary] Error for userId={}", settings.getUserId(), e);
            }
        }
        sendInParallel(toSend);
    }

    /**
     * Отправляет и ждёт, пока уйдут все: следующий запуск шедулера (через 30 мин) не начнётся раньше,
     * и отчёт, ещё не отмеченный отправленным, не уйдёт второй раз. Пул — на один прогон.
     */
    private void sendInParallel(List<UserSettings> toSend) {
        if (toSend.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(parallelism, toSend.size()));
        try {
            CompletableFuture.allOf(toSend.stream()
                    .map(settings -> CompletableFuture.runAsync(() -> sendAndMark(settings), pool))
                    .toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
        }
    }

    // Отмечаем после отправки, как и раньше: упала — день не отмечен, через 30 минут попробуем снова
    private void sendAndMark(UserSettings settings) {
        try {
            sendDailySummary(settings);
            userSettingsService.updateDailyLastReminder(settings.getUserId());
        } catch (Exception e) {
            log.error("[DailySummary] Error for userId={}", settings.getUserId(), e);
        }
    }

    /**
     * Пора ли слать отчёт. День без блюд отмечается обработанным сразу и отчёт не шлётся:
     * иначе проверка шла бы каждые 30 мин до полуночи, и блюдо, записанное в 23:40, дало бы «вечерний» отчёт в полночь.
     */
    private boolean isReportDue(UserSettings settings, Instant nowUtc) {
        // Глобальный стоп
        if (!settings.isRemindersEnabled() || !settings.isDailySummaryEnabled()) {
            return false;
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
                return false;
            }
        }

        if (hasDishesOnReportDay(settings.getUserId(), effectiveTarget)) {
            return true;
        }
        userSettingsService.updateDailyLastReminder(settings.getUserId());
        return false;
    }

    private boolean hasDishesOnReportDay(Long userId, ZonedDateTime effectiveTarget) {
        Instant lastDish = dishService.getLastDishTimeOrNull(userId);
        Instant startOfReportDay = effectiveTarget.toLocalDate().atStartOfDay(effectiveTarget.getZone()).toInstant();
        return lastDish != null && !lastDish.isBefore(startOfReportDay);
    }

    //TODO Универсальное средство отправки
    private void sendDailySummary(UserSettings settings) {
        try {
            Long appUserId = settings.getUserId();
            TelegramUser tgUser = telegramUserService
                    .findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT)
                    .orElse(null);
            if (tgUser == null) return;

            Long chatId = tgUser.getTelegramId();

            todayUpdateHandler.sendTodayMessage(chatId, appUserId);
            try {
                String dayReport = reportService.getDayAiReport(appUserId).getOrThrow();
                calorieTelegramBot.sendReturnedMessage(chatId, dayReport);
            } catch (MissingFeatureException e) {
                throw e;
            } catch (Exception e) {
                // Экран «Сегодня» уже ушёл — день считаем отправленным. Иначе при сбое ИИ каждые 30 минут
                // до полуночи пользователь снова получал бы «Сегодня». Попытку отчёта аспект уже вернул
                log.error("[DailySummary] AI day report failed for userId={}, day marked as sent", appUserId, e);
            }
        } catch (MissingFeatureException e) {
            log.warn("scheduler day report access denied!");
        }
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
