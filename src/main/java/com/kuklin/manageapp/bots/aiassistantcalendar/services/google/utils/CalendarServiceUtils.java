package com.kuklin.manageapp.bots.aiassistantcalendar.services.google.utils;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.CalendarEventAiResponse;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CalendarServiceUtils {
    private static final String DEFAULT_TZ = "Europe/Moscow";

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
                    .setDateTime(new DateTime(start.toInstant().toEpochMilli()))
                    .setTimeZone(timeZone);

            EventDateTime endDT = new EventDateTime()
                    .setDateTime(new DateTime(end.toInstant().toEpochMilli()))
                    .setTimeZone(timeZone);

            patch.setStart(startDT);
            patch.setEnd(endDT);
        } else if (hasStart && !hasEnd) {
            // Трактуем как «сделать целодневным на указанную дату»
            LocalDate d = parseWithZone(req.getStart(), zoneId).toLocalDate();
            patch.setStart(new EventDateTime().setDate(new DateTime(d.toString())));
            patch.setEnd(new EventDateTime().setDate(new DateTime(d.plusDays(1).toString())));
        }

        //Напоминания
        List<EventReminder> reminderList = new ArrayList<>();
        for (Integer notifyIn: req.getNotifyInMinutesList()) {
            reminderList.add(
                    new EventReminder()
                            .setMethod("popup")
                            .setMinutes(notifyIn)
            );
        }

        Event.Reminders reminders = new Event.Reminders()
                .setUseDefault(reminderList.size() == 0) // важно: отключаем дефолтные, иначе будут только стандартные Google
                .setOverrides(reminderList);
        patch.setReminders(reminders);

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

    private static Event.Reminders getReminders(CalendarEventAiResponse request) {
        //Напоминания
        List<EventReminder> reminderList = new ArrayList<>();
        for (Integer notifyIn: request.getNotifyInMinutesList()) {
            reminderList.add(
                    new EventReminder()
                            .setMethod("popup")
                            .setMinutes(notifyIn)
            );
        }

        return new Event.Reminders()
                .setUseDefault(reminderList.size() == 0) // важно: отключаем дефолтные, иначе будут только стандартные Google
                .setOverrides(reminderList);
    }

    public static Event normalizeEventRequest(CalendarEventAiResponse request, String timeZone) {
        //Дефолтное время длительности
        int defaultPlusTime = 1;

        Event.Reminders reminders = getReminders(request);

        ZoneId zoneId = ZoneId.of(timeZone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        if (request.getEnd() == null || request.getEnd().isBlank()) {
            LocalDate startDate = (request.getStart() != null && !request.getStart().isBlank())
                    ? parseWithZone(request.getStart(), zoneId).toLocalDate()
                    : now.toLocalDate(); // сегодня в TZ календаря

            EventDateTime startAllDay = new EventDateTime()
                    .setDate(new DateTime(startDate.toString())); // YYYY-MM-DD
            EventDateTime endAllDay = new EventDateTime()
                    .setDate(new DateTime(startDate.plusDays(1).toString())); // end эксклюзивно

            return new Event()
                    .setSummary(request.getSummary())
                    .setDescription(request.getDescription())
                    .setStart(startAllDay)
                    .setEnd(endAllDay)
                    .setReminders(reminders)
                    ;
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
                .setEnd(endDT)
                .setReminders(reminders)
                ;
    }

    /**
     * Превращаем смещение от UTC в часах в строку таймзоны
     * для Google Calendar.
     *
     * Используем семейство "Etc/GMT±N":
     *  - ВАЖНО: в этих зонах знак инвертирован:
     *      "Etc/GMT-3" = UTC+3
     *      "Etc/GMT+3" = UTC-3
     */
    public static String resolveTimeZoneFromUtcOffsetHours(Integer offset) {
        if (offset == null) {
            // Фолбэк — дефолтная таймзона приложения
            return DEFAULT_TZ;
        }

        return switch (offset) {
            case 0  -> "Etc/UTC";                       // UTC±0
            case 1  -> "Europe/Berlin";                 // UTC+1
            case 2  -> "Europe/Helsinki";               // UTC+2
            case 3  -> "Europe/Moscow";                 // UTC+3
            case 4  -> "Europe/Samara";                 // UTC+4
            case 5  -> "Asia/Yekaterinburg";            // UTC+5
            case 6  -> "Asia/Almaty";                   // UTC+6
            case 7  -> "Asia/Bangkok";                  // UTC+7
            case 8  -> "Asia/Shanghai";                 // UTC+8
            case 9  -> "Asia/Tokyo";                    // UTC+9
            case 10 -> "Australia/Sydney";              // UTC+10
            case 11 -> "Pacific/Noumea";                // UTC+11
            case 12 -> "Pacific/Auckland";              // UTC+12
            case 13 -> "Pacific/Tongatapu";             // UTC+13
            case 14 -> "Pacific/Kiritimati";            // UTC+14
            default -> "Europe/Moscow";
        };
    }

    public static CalendarListEntry getCalendarListEntryBySummaryOrNull(List<CalendarListEntry> items, String summary) {
        if (items == null) return null;
        for (CalendarListEntry entry : items) {
            if (summary.equals(entry.getSummary())) {
                return entry;
            }
        }
        return null;
    }
}
