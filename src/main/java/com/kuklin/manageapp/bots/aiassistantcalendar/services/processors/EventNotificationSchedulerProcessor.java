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
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
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

    private static final Integer NOTIFICATED_TIME_MINUTES = 30;

    @Override
    public void process() {
        noAuthProccess();
        authProccess();
    }

    private void noAuthProccess() {
        log.info("NO-AUTH SCHEDULE");
        List<UserGoogleCalendar> userGoogleCalendarList = userGoogleCalendarService.findAll();
        for (UserGoogleCalendar userCalendar: userGoogleCalendarList) {
            processUserCalendar(userCalendar.getTelegramId());
        }
    }

    private void authProccess() {
        log.info("AUTH SCHEDULE");
        List<AssistantGoogleOAuth> oAuthList = tokenService.getAll();
        for (AssistantGoogleOAuth auth: oAuthList) {
            processUserCalendar(auth.getTelegramId());
        }
    }

    private void processUserCalendar(Long telegramId) {
        log.info("SCHEDULE CORE METHOD");
        try {
            CalendarService.CalendarContext context = calendarService
                    .getCalendarContext(telegramId);

            String tz = calendarService.getTimeZoneInCalendar(context);

            List<Event> events = calendarService.getTodayEvents(telegramId);
            if (events.isEmpty()) return;

            List<Event> soonEvents = getEventsLessThanTime(events, ZoneId.of(tz));
            notificateUser(soonEvents, telegramId, context.getCalendarId());
        } catch (IOException e) {
            log.error("Google execute request error!", e);
        } catch (TokenRefreshException ignore) {

        }
    }

    private void notificateUser(List<Event> events, Long telegramId, String calendarId) {
        log.info("NOTIFICATE: " + telegramId);
        for (Event event: events) {

            //Если уже уведомляли, то пропускаем
            if (notifiedEventService.isNotified(calendarId, event.getId(), telegramId)) {
                continue;
            }

            //Отправляем сообщение в телеграм
            Message message = telegramBot.sendReturnedMessage(telegramId,
                    CalendarEventUpdateHandler.getResponseString(event));
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
