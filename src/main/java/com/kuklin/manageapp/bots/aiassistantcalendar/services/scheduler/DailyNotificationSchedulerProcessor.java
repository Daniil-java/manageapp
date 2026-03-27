package com.kuklin.manageapp.bots.aiassistantcalendar.services.scheduler;

import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserNotificationSettings;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserNotificationSettingsService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.CalendarEventUpdateHandler;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class DailyNotificationSchedulerProcessor implements ScheduleProcessor {

    private final UserNotificationSettingsService userNotificationSettingsService;
    private final AssistantTelegramBot telegramBot;
    private final CalendarService calendarService;

    /**
     * Максимальное количество минут "наперед",
     * за которое мы можем отправить уведомление.
     * То есть:
     *   - если до dailyTime <= 5 минут — можно слать заранее;
     *   - если уже позже dailyTime — тоже можно слать (если за этот день ещё не слали).
     */
    private static final int EARLY_WINDOW_MINUTES = 5;

    @Override
    public void process() {
        // Берём всех, у кого включено daily-уведомление
        List<UserNotificationSettings> settingsList =
                userNotificationSettingsService.findAllEnabled();

        for (UserNotificationSettings settings : settingsList) {
            try {
                processUser(settings);
            } catch (Exception e) {
                // Логируем, но не валим весь шедулер из-за одного пользователя
                log.error("Error processing daily notification for user {}", settings.getTelegramId(), e);
            }
        }
    }

    /**
     * Обрабатываем одного пользователя:
     * - считаем локальное "сейчас" по utcOffsetHours;
     * - если за этот локальный день уже слали — выходим;
     * - считаем разницу между "сейчас" и dailyTime;
     * - если до dailyTime больше 5 минут — рано, ждём;
     * - если до dailyTime <= 5 минут (или уже позже) — шлём уведомление и помечаем день.
     */
    private void processUser(UserNotificationSettings settings) {
        Long telegramId = settings.getTelegramId();

        // Если не задано время уведомления или смещение от UTC — нечего считать
        if (settings.getDailyTime() == null
                || settings.getUtcOffsetHours() == null) {
            return;
        }

        LocalTime dailyTime = settings.getDailyTime();
        Integer offsetHours = settings.getUtcOffsetHours();

        // Часовой пояс пользователя в виде UTC+N
        ZoneOffset userOffset = ZoneOffset.ofHours(offsetHours);

        // "Сейчас" в часовом поясе пользователя
        LocalDateTime nowUser = LocalDateTime.ofInstant(Instant.now(), userOffset);
        LocalDate userToday = nowUser.toLocalDate();
        LocalTime nowTime = nowUser.toLocalTime();

        // Если уже отправляли уведомление сегодня — выходим
        LocalDate last = settings.getLastDailyNotified();
        if (last != null && last.isEqual(userToday)) {
            return;
        }

        // Переводим время в минуты от начала суток, чтобы просто посчитать разницу
        int nowMinutes = nowTime.getHour() * 60 + nowTime.getMinute();
        int targetMinutes = dailyTime.getHour() * 60 + dailyTime.getMinute();

        // diff = сколько минут осталось до целевого времени:
        //   > 0  — ещё не наступило;
        //   = 0  — ровно время;
        //   < 0  — уже позже dailyTime.
        int diff = targetMinutes - nowMinutes;

        // Если до dailyTime больше, чем EARLY_WINDOW_MINUTES — ещё рано, выходим.
        // Пример: сейчас 05:40, dailyTime = 06:00, diff = 20 → рано, не шлём.
        if (diff > EARLY_WINDOW_MINUTES) {
            return;
        }

        try {
            List<Event> events = calendarService.getTodayEvents(telegramId);
            Calendar calendar = calendarService.getCalendarByTelegramIdOrNull(telegramId);
            telegramBot.sendReturnedMessage(
                    telegramId,
                    getTodayEventsString(events),
                    buildKeyboard(calendar.getId()),
                    null
            );
        } catch (IOException e) {
            telegramBot.sendReturnedMessage(
                    telegramId,
                    "Ежедневные уведомления: \nОшибка IOException"
            );
        } catch (TokenRefreshException e) {
            telegramBot.sendReturnedMessage(
                    telegramId,
                    "Ежедневные уведомления: \nОшибка авторизации"
            );
        }

        // Помечаем, что за этот локальный день уведомление уже отправили,
        // чтобы не дублировать при следующем проходе шедулера.
        userNotificationSettingsService.markDailyNotified(telegramId);

        log.info("Daily morning notification sent to user {} (localNow={}, dailyTime={}, diff={})",
                telegramId, nowTime, dailyTime, diff);
    }

    private InlineKeyboardMarkup buildKeyboard(String calendarId) {
        String calendarUrl = "https://calendar.google.com/calendar/u/0/r?cid="
                + URLEncoder.encode(calendarId, StandardCharsets.UTF_8);

        InlineKeyboardButton openCalendarButton = InlineKeyboardButton.builder()
                .text("Открыть календарь")
                .url(calendarUrl)
                .build();

        List<InlineKeyboardButton> row = List.of(openCalendarButton);
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    private String getTodayEventsString(List<Event> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("На сегодня запланировано: ").append("\n").append("───────").append("\n");
        int i = 1;
        for (Event event: events) {
            sb.append("" + i + ". ").append(CalendarEventUpdateHandler.getResponseEventString(event));
            i++;
            sb.append("\n");
        }

        return sb.append("\n").toString();
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
