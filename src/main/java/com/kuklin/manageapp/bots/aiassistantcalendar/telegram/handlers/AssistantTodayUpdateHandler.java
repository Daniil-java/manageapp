package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantTodayUpdateHandler implements AssistantUpdateHandler{
    private final AssistantTelegramBot assistantTelegramBot;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final CalendarService calendarService;
    private static final String ERROR_MSG = "Не получилось вернуть мероприятия на сегодня!";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String calendarId = userGoogleCalendarService.getUserCalendarIdByTelegramIdOrNull(telegramUser.getTelegramId());
        if (calendarId == null) {
            assistantTelegramBot.sendReturnedMessage(chatId, SetCalendarIdUpdateHandler.CALENDAR_IS_NULL_MSG);
        }

        String response = ERROR_MSG;
        try {
            List<Event> events = calendarService.getTodayEvents(calendarId);
            response = getTodayEventsString(events);
        } catch (IOException e) {
            log.error(response, e);
        }

        assistantTelegramBot.sendReturnedMessage(chatId, response);

    }

    private String getTodayEventsString(List<Event> events) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("На сегодня запланировано:  \n");
        for (Event event: events) {
            stringBuilder.append(getTimeHHMM(event.getStart()));
            stringBuilder.append(" - ");
            stringBuilder.append(getTimeHHMM(event.getEnd()));
            stringBuilder.append(" ").append(event.getSummary());

            stringBuilder.append("\n");
        }

        return stringBuilder.toString();
    }

    private String getTimeHHMM(EventDateTime eventDateTime) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        OffsetDateTime time = OffsetDateTime.parse(eventDateTime.getDateTime().toStringRfc3339());
        return time.format(timeFormatter);
    }


    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_TODAY.getCommandText();
    }
}
