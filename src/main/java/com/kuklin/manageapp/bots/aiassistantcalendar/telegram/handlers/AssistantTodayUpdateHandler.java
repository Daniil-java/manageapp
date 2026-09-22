package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
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
public class AssistantTodayUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final UserMessagesLogService userMessagesLogService;
    private final CalendarService calendarService;
    private static final String ERROR_MSG = "Не получилось вернуть мероприятия на сегодня!";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();

        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                update.getMessage().getText()
        );
        assistantTelegramBot.sendChatActionTyping(chatId);

        String response = ERROR_MSG;
        try {
            List<Event> events = calendarService.getTodayEvents(telegramUser.getTelegramId());
            response = getTodayEventsString(events);
            assistantTelegramBot.sendReturnedMessage(chatId, response);
        } catch (IOException e) {
            log.error(response, e);
        } catch (TokenRefreshException e) {
            log.error(response, e);
        }


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
