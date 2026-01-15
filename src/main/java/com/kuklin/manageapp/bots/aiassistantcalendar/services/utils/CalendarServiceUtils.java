package com.kuklin.manageapp.bots.aiassistantcalendar.services.utils;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;

public class CalendarServiceUtils {

    public static String getRequestByEventsList(List<Event> events) {
        StringBuilder sb = new StringBuilder();
        for (Event event: events) {
            sb.append("eventId: ").append(event.getId()).append("\n");
            sb.append("summary: ").append(event.getSummary()).append("\n");
            sb.append("description: ").append(event.getDescription()).append("\n");
            sb.append("date: ").append(event.getStart()).append("\n");
        }
        return sb.toString();
    }

    public static Event buildPatchFromRequest(CalendarEventAiResponse req, String timeZone) {
        Event patch = new Event();

        if (req.getSummary() != null && !req.getSummary().isBlank()) {
            patch.setSummary(req.getSummary());
        }
        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            patch.setDescription(req.getDescription());
        }

        // Время
        ZoneId zoneId = ZoneId.of(timeZone);

        boolean hasStart = req.getStart() != null && !req.getStart().isBlank();
        boolean hasEnd   = req.getEnd()   != null && !req.getEnd().isBlank();

        if (hasStart && hasEnd) {
            ZonedDateTime start = parseWithZone(req.getStart(), zoneId).withZoneSameInstant(zoneId);
            ZonedDateTime end   = parseWithZone(req.getEnd(),   zoneId).withZoneSameInstant(zoneId);

            EventDateTime startDT = new EventDateTime()
                    .setDateTime(new DateTime(start.toInstant().toEpochMilli(), start.getOffset().getTotalSeconds() / 60))
                    .setTimeZone(timeZone);
            EventDateTime endDT = new EventDateTime()
                    .setDateTime(new DateTime(end.toInstant().toEpochMilli(), end.getOffset().getTotalSeconds() / 60))
                    .setTimeZone(timeZone);

            patch.setStart(startDT);
            patch.setEnd(endDT);
        } else if (hasStart && !hasEnd) {
            // Трактуем как «сделать целодневным на указанную дату»
            LocalDate d = parseWithZone(req.getStart(), zoneId).toLocalDate();
            patch.setStart(new EventDateTime().setDate(new com.google.api.client.util.DateTime(d.toString())));
            patch.setEnd(new EventDateTime().setDate(new com.google.api.client.util.DateTime(d.plusDays(1).toString())));
        }

        return patch;
    }

    public static ZonedDateTime parseWithZone(String input, ZoneId zoneId) {
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

    public static EventDateTime buildEventDateTime(LocalDateTime local, String calendarTimeZone) {
        ZoneId zoneId = ZoneId.of(calendarTimeZone);
        ZonedDateTime zoned = local.atZone(zoneId);

        return new EventDateTime()
                .setDateTime(new DateTime(
                        zoned.toInstant().toEpochMilli(),
                        zoned.getOffset().getTotalSeconds() / 60
                ))
                .setTimeZone(zoneId.toString());
    }

    public static Event normalizeEventRequest(CalendarEventAiResponse request, String timeZone) {
        int defaultPlusTime = 1;

        ZoneId zoneId = ZoneId.of(timeZone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        if (request.getEnd() == null || request.getEnd().isBlank()) {
            LocalDate startDate = (request.getStart() != null && !request.getStart().isBlank())
                    ? parseWithZone(request.getStart(), zoneId).toLocalDate()
                    : now.toLocalDate(); // сегодня в TZ календаря

            EventDateTime startAllDay = new EventDateTime()
                    .setDate(new com.google.api.client.util.DateTime(startDate.toString())); // YYYY-MM-DD
            EventDateTime endAllDay = new EventDateTime()
                    .setDate(new com.google.api.client.util.DateTime(startDate.plusDays(1).toString())); // end эксклюзивно

            return new Event()
                    .setSummary(request.getSummary())
                    .setDescription(request.getDescription())
                    .setStart(startAllDay)
                    .setEnd(endAllDay);
        }

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

    public static void doSo(ZoneId zoneId) {
        // Старт: начало сегодняшнего дня в TZ календаря
        ZonedDateTime start = LocalDate.now(zoneId).atStartOfDay(zoneId);
        // Конец окна: ровно через год
        ZonedDateTime end = start.plusYears(1);

        int tzShiftStart = start.getOffset().getTotalSeconds() / 60;
        int tzShiftEnd   = end.getOffset().getTotalSeconds() / 60;

        DateTime timeMin = new DateTime(start.toInstant().toEpochMilli(), tzShiftStart);
        DateTime timeMax = new DateTime(end.toInstant().toEpochMilli(), tzShiftEnd);
    }
}
