package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.ActionKnot;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {
    private final OpenAiProviderProcessor openAiProviderProcessor;
    private final ObjectMapper objectMapper;
    private final Calendar calendarService;
    private final TelegramAiAssistantCalendarBotKeyComponents components;

    private static final String AI_REMOVE_REQUEST =
            """
                    Проанализируй список событий и строку поиска.
                                        
                    Список событий:
                    "%s"
                                        
                    Строка поиска:
                    "%s"
                                        
                    Правила:
                    1. Верни только JSON‑массив строк без лишнего текста, обрамлений или комментариев.
                    2. Нужно найти все события, у которых summary или description (или их комбинация) максимально совпадают со строкой поиска.
                    3. Ответ должен быть в формате:
                    [
                      "eventId1",
                      "eventId2"
                    ]
                    4. Если совпадений нет — верни пустой массив [].
                                        
                    """;

    public Event addEventInCalendar(CalendarEventAiResponse request, String calendarId) throws IOException {
        Event event = normalizeEventRequest(request, getTimeZoneInCalendar(calendarId));

        Event inserted = calendarService.events()
                .insert(calendarId, event)
                .execute();

        log.info("Запрос на создание эвента в GOOGLE: \nМероприятие {},\nОписание {},\nНачало {},\nКонец {},\nТаймзона {}",
                inserted.getId(),
                inserted.getSummary(),
                inserted.getDescription(),
                inserted.getStart(),
                inserted.getEnd()
        );

        return inserted;
    }

    private EventDateTime buildEventDateTime(LocalDateTime local, String calendarTimeZone) {
        ZoneId zoneId = ZoneId.of(calendarTimeZone);
        ZonedDateTime zoned = local.atZone(zoneId);

        return new EventDateTime()
                .setDateTime(new DateTime(
                        zoned.toInstant().toEpochMilli(),
                        zoned.getOffset().getTotalSeconds() / 60
                ))
                .setTimeZone(zoneId.toString());
    }

    private Event normalizeEventRequest(CalendarEventAiResponse request, String timeZone) {
        int defaultPlusTime = 1;

        ZoneId zoneId = ZoneId.of(timeZone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        ZonedDateTime start = (request.getStart() != null && !request.getStart().isBlank())
                ? parseWithZone(request.getStart(), zoneId)
                : now;

        ZonedDateTime end = (request.getEnd() != null && !request.getEnd().isBlank())
                ? parseWithZone(request.getEnd(), zoneId)
                : now.plusHours(defaultPlusTime);


        // приводим к зоне календаря
        start = start.withZoneSameInstant(zoneId);
        end = end.withZoneSameInstant(zoneId);

        EventDateTime startDT = new EventDateTime()
                .setDateTime(new DateTime(
                        start.toInstant().toEpochMilli(),
                        start.getOffset().getTotalSeconds() / 60
                ))
                .setTimeZone(timeZone);

        EventDateTime endDT = new EventDateTime()
                .setDateTime(new DateTime(
                        end.toInstant().toEpochMilli(),
                        end.getOffset().getTotalSeconds() / 60
                ))
                .setTimeZone(timeZone);

        return new Event()
                .setSummary(request.getSummary())
                .setDescription(request.getDescription())
                .setStart(startDT)
                .setEnd(endDT);
    }

    private ZonedDateTime parseWithZone(String input, ZoneId zoneId) {
        try {
            // если в строке уже есть смещение или зона
            return ZonedDateTime.parse(input);
        } catch (DateTimeParseException e1) {
            try {
                // если есть смещение, но нет зоны
                return OffsetDateTime.parse(input).atZoneSameInstant(zoneId);
            } catch (DateTimeParseException e2) {
                // если вообще "голое" время без смещения
                LocalDateTime ldt = LocalDateTime.parse(input);
                return ldt.atZone(zoneId);
            }
        }
    }

    private String getTimeZoneInCalendar(String calendarId) throws IOException {
        com.google.api.services.calendar.model.Calendar calendar =
                calendarService.calendars().get(calendarId).execute();

        return calendar.getTimeZone();
    }

    public List<Event> getTodayEvents(String calendarId) throws IOException {
        // Конвертируем в UTC для Google API
        ZoneId zoneId = ZoneId.of(getTimeZoneInCalendar(calendarId));

        //Начало дня
        ZonedDateTime startOfDay = LocalDate.now(zoneId).atStartOfDay(zoneId);
        //Конец дня
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        // смещение в минутах
        int tzShiftMinutes = startOfDay.getOffset().getTotalSeconds() / 60;
        int tzShiftEnd = endOfDay.getOffset().getTotalSeconds() / 60;

        DateTime timeMin = new DateTime(startOfDay.toInstant().toEpochMilli(), tzShiftMinutes);
        DateTime timeMax = new DateTime(endOfDay.toInstant().toEpochMilli(), tzShiftEnd);

        // Запрос к Google Calendar API
        Events events = calendarService.events().list(calendarId)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        return events.getItems();
    }

    public List<Event> removeEventInCalendarByMessage(String calendarId, ActionKnot actionKnot) throws IOException {
        List<Event> todayEvents = getTodayEvents(calendarId);

        String aiResponse = openAiProviderProcessor.fetchResponse(
                components.getAiKey(),
                String.format(
                        AI_REMOVE_REQUEST,
                        getRemoveRequestByEventsList(todayEvents),
                        actionKnot.getCalendarEventAiResponse().getSummary()
                        + actionKnot.getCalendarEventAiResponse().getDescription()
                )
        );
        List<String> eventIds = objectMapper.readValue(aiResponse, new TypeReference<List<String>>() {});

        // Используем Set для быстрого поиска
        Set<String> idsToRemove = new HashSet<>(eventIds);

        return todayEvents.stream()
                .filter(event -> idsToRemove.contains(event.getId()))
                .collect(Collectors.toList());
    }

    private String getRemoveRequestByEventsList(List<Event> events) {
        StringBuilder sb = new StringBuilder();
        for (Event event: events) {
            sb.append("eventId: ").append(event.getId()).append("\n");
            sb.append("summary: ").append(event.getSummary()).append("\n");
            sb.append("description: ").append(event.getDescription()).append("\n");
        }
        return sb.toString();
    }

    public void removeEventInCalendar(String eventId, String calendarId) throws IOException {
         calendarService.events()
                .delete(calendarId, eventId)
                .execute();
    }
}
