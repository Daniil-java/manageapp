package com.kuklin.manageapp.bots.aiassistantcalendar.services.scheduler;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserGoogleCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.NotifiedEventService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.TokenService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.CalendarEventUpdateHandler;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class EventNotificationSchedulerProcessor implements ScheduleProcessor {
    private final CalendarService calendarService;
    private final TokenService tokenService;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final AssistantTelegramBot telegramBot;
    private final NotifiedEventService notifiedEventService;

    private static final Integer NOTIFICATED_TIME_MINUTES = 60;

    @Override
    public void process() {
        noAuthProcess();
        authProcess();
    }

    private void noAuthProcess() {
        List<UserGoogleCalendar> userGoogleCalendarList = userGoogleCalendarService.findAll();
        for (UserGoogleCalendar userCalendar: userGoogleCalendarList) {
            processUserCalendar(userCalendar.getTelegramId());
        }
    }

    private void authProcess() {
        List<AssistantGoogleOAuth> oAuthList = tokenService.getAll();
        for (AssistantGoogleOAuth auth: oAuthList) {
            processUserCalendar(auth.getTelegramId());
        }
    }

    private void processUserCalendar(Long telegramId) {
        try {
            CalendarService.CalendarContext context = calendarService
                    .getCalendarContext(telegramId);

            String tz = calendarService.getTimeZoneInCalendarOrNull(context);
            if (tz == null) return;

            List<Event> events = calendarService.getTodayEvents(telegramId);
            if (events.isEmpty()) return;

//            List<Event> soonEvents = getEventsLessThanTime(events, ZoneId.of(tz));
//            notificateUser(soonEvents, telegramId, context.getCalendarId());

            List<DueReminder> dueReminders = getDueReminders(events, ZoneId.of(tz));
            notificateUser(telegramId, context.getCalendarId(), dueReminders);
        } catch (IOException e) {
            log.error("Google execute request error!", e);
        } catch (TokenRefreshException ignore) {

        }
    }

    private void notificateUser(Long telegramId, String calendarId, List<DueReminder> reminders) {
        log.info("user notificated: {}", telegramId);

        for (DueReminder due : reminders) {
            Event event = due.event();
            int minutesBefore = due.minutesBefore();

            // уже слали уведомление по ЭТОМУ событию и ЭТОМУ ремайндера
            if (notifiedEventService.isNotified(calendarId, event.getId(), telegramId, minutesBefore)) {
                continue;
            }

            // можешь добавить инфу в текст "за N минут до", если хочешь
            Message message = telegramBot.sendReturnedMessage(
                    telegramId,
                    CalendarEventUpdateHandler.getResponseEventString(event)
                    // + "\n\n(напоминание за " + minutesBefore + " минут)"
            );

            if (message != null) {
                notifiedEventService.markAsNotified(calendarId, event.getId(), telegramId, minutesBefore);
            }

            ThreadUtil.sleep(100);
        }
    }


    private void notificateUser(List<Event> events, Long telegramId, String calendarId) {
        log.info("user notificated: " + telegramId);
        for (Event event: events) {

            //Если уже уведомляли, то пропускаем
            if (notifiedEventService.isNotified(calendarId, event.getId(), telegramId)) {
                continue;
            }

            //Отправляем сообщение в телеграм
            Message message = telegramBot.sendReturnedMessage(telegramId,
                    CalendarEventUpdateHandler.getResponseEventString(event));
            //Поемячем, что уведомили о мероприятии
            if (message != null) {
                notifiedEventService.markAsNotified(calendarId, event.getId(), telegramId);
            }
            ThreadUtil.sleep(100);
        }
    }
    private List<Event> getEventsLessThanTime(List<Event> events, ZoneId calendarZoneId) {
        List<Event> result = new ArrayList<>();
        ZonedDateTime nowInCalendarTz = ZonedDateTime.now(calendarZoneId);

        for (Event event : events) {
            try {
                EventDateTime start = event.getStart();
                if (start == null) continue;

                // Пропускаем all-day события (у них есть date вместо dateTime)
                DateTime date = start.getDate();
                DateTime dateTime = start.getDateTime();
                if (date != null || dateTime == null) {
                    // all-day или неизвестный формат — пропускаем
                    continue;
                }

                // dateTime.getValue() возвращает миллисекунды с epoch
                long millis = dateTime.getValue();
                Instant instant = Instant.ofEpochMilli(millis);

                // Представляем время начала в временной зоне календаря
                ZonedDateTime eventStart = ZonedDateTime.ofInstant(instant, calendarZoneId);

                long minutesUntil = Duration.between(nowInCalendarTz, eventStart).toMinutes();

                // вытаскиваем, за сколько минут до события стоит напоминать
                int reminderMinutes = resolveReminderMinutes(event);

                // если "момент напоминания" уже наступил (или только что наступает) —
                // кидаем событие в список
                if (minutesUntil <= reminderMinutes) {
                    result.add(event);
                }
            } catch (Exception ex) {
                log.warn("Can't parse event start time, skipping event: {} (reason: {})", event.getId(), ex.getMessage());
            }
        }

        return result;
    }

    private int resolveReminderMinutes(Event event) {
        Event.Reminders reminders = event.getReminders();
        if (reminders == null) {
            return NOTIFICATED_TIME_MINUTES;
        }

        // Если включены дефолтные гугловые напоминания — считаем, что у нас свой дефолт
        if (Boolean.TRUE.equals(reminders.getUseDefault())) {
            return NOTIFICATED_TIME_MINUTES;
        }

        List<EventReminder> overrides = reminders.getOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return NOTIFICATED_TIME_MINUTES;
        }

        int max = Integer.MIN_VALUE;
        for (EventReminder r : overrides) {
            if (r.getMinutes() != null) {
                max = Math.max(max, r.getMinutes());
            }
        }

        return (max == Integer.MIN_VALUE) ? NOTIFICATED_TIME_MINUTES : max;
    }

    public record DueReminder(Event event, int minutesBefore) {}

    private List<DueReminder> getDueReminders(List<Event> events, ZoneId calendarZoneId) {
        List<DueReminder> result = new ArrayList<>();
        ZonedDateTime nowInCalendarTz = ZonedDateTime.now(calendarZoneId);

        for (Event event : events) {
            try {
                EventDateTime start = event.getStart();
                if (start == null) continue;

                DateTime date = start.getDate();
                DateTime dateTime = start.getDateTime();
                if (date != null || dateTime == null) {
                    // all-day или странный формат — пропускаем
                    continue;
                }

                long millis = dateTime.getValue();
                Instant instant = Instant.ofEpochMilli(millis);
                ZonedDateTime eventStart = ZonedDateTime.ofInstant(instant, calendarZoneId);

                long minutesUntil = Duration.between(nowInCalendarTz, eventStart).toMinutes();
                if (minutesUntil < 0) {
                    // событие уже началось — ничего не шлём
                    continue;
                }

                // получаем список минут из ремайндера события
                List<Integer> reminderMinutesList = resolveReminderMinutesList(event);

                for (Integer rm : reminderMinutesList) {
                    if (rm == null) continue;

                    // Простая логика: как только до события осталось <= rm минут,
                    // считаем, что "время этого ремайндера пришло"
                    if (minutesUntil <= rm) {
                        result.add(new DueReminder(event, rm));
                    }
                }
            } catch (Exception ex) {
                log.warn("Can't parse event start time, skipping event: {} (reason: {})",
                        event.getId(), ex.getMessage());
            }
        }

        return result;
    }

    private List<Integer> resolveReminderMinutesList(Event event) {
        List<Integer> result = new ArrayList<>();

        Event.Reminders reminders = event.getReminders();
        if (reminders == null) {
            // вообще нет настроек — один "виртуальный" ремайндер по дефолту
            result.add(NOTIFICATED_TIME_MINUTES);
            return result;
        }

        if (Boolean.TRUE.equals(reminders.getUseDefault())) {
            // используются дефолтные гугловые — тоже один наш дефолт
            result.add(NOTIFICATED_TIME_MINUTES);
            return result;
        }

        List<EventReminder> overrides = reminders.getOverrides();
        if (overrides == null || overrides.isEmpty()) {
            result.add(NOTIFICATED_TIME_MINUTES);
            return result;
        }

        for (EventReminder r : overrides) {
            if (r.getMinutes() != null) {
                result.add(r.getMinutes());
            }
        }

        // если вдруг всё было null, всё равно не оставляем пустой список
        if (result.isEmpty()) {
            result.add(NOTIFICATED_TIME_MINUTES);
        }

        return result;
    }
    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
