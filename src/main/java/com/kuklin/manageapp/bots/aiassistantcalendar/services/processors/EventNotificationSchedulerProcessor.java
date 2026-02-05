package com.kuklin.manageapp.bots.aiassistantcalendar.services.processors;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserGoogleCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.NotifiedEventService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
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
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final AssistantTelegramBot telegramBot;
    private final NotifiedEventService notifiedEventService;

    private static final Integer NOTIFICATED_TIME_MINUTES = 30;

    @Override
    public void process() {
        List<UserGoogleCalendar> userGoogleCalendarList = userGoogleCalendarService.findAll();

        for (UserGoogleCalendar userCalendar: userGoogleCalendarList) {

            try {
                String tz = calendarService.getTimeZoneInCalendar(userCalendar.getCalendarId());

                List<Event> events = calendarService.getTodayEvents(userCalendar.getCalendarId());
                if (events.isEmpty()) continue;

                List<Event> soonEvents = getEventsLessThanTime(events, ZoneId.of(tz));
                notificateUser(userCalendar, soonEvents);

            } catch (IOException e) {
                log.error("Google execute request error!", e);
            }
        }
    }

    private void notificateUser(UserGoogleCalendar userGoogleCalendar, List<Event> events) {
        for (Event event: events) {

            //Если уже уведомляли, то пропускаем
            if (notifiedEventService.isNotified(userGoogleCalendar.getCalendarId(), event.getId(), userGoogleCalendar.getTelegramId())) {
                continue;
            }

            //Отправляем сообщение в телеграм
            Message message = telegramBot.sendReturnedMessage(userGoogleCalendar.getTelegramId(),
                    CalendarEventUpdateHandler.getResponseString(event));
            //Поемячем, что уведомили о мероприятии
            if (message != null) {
                notifiedEventService.markAsNotified(userGoogleCalendar.getCalendarId(), event.getId(), userGoogleCalendar.getTelegramId());
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

                // включаем только будущие события, которые начнутся в пределах thresholdMinutes
                if (minutesUntil >= 0 && minutesUntil <= NOTIFICATED_TIME_MINUTES) {
                    result.add(event);
                }
            } catch (Exception ex) {
                log.warn("Can't parse event start time, skipping event: {} (reason: {})", event.getId(), ex.getMessage());
            }
        }

        return result;
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
