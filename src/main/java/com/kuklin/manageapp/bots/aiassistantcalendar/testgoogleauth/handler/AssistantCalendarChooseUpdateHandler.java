package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantCalendarChooseUpdateHandler implements AssistantUpdateHandler {
    private final CalendarService calendarService;
    private final AssistantTelegramBot telegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        try {
            List<String> calendarList = calendarService.listUserCalendars(telegramUser.getTelegramId());

            StringBuilder sb = new StringBuilder();
            for (String calendar: calendarList) {
                sb.append(calendar).append("\n");
            }
            telegramBot.sendReturnedMessage(chatId, sb.toString());
        } catch (Exception e) {
            telegramBot.sendReturnedMessage(chatId, "Ошибка получения календаря");
            log.error("Не получилось получить список календарей");
        }
    }

    @Override
    public String getHandlerListName() {
        return "/calendar";
    }
}
