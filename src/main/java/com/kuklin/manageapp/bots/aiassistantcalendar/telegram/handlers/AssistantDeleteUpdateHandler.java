package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class AssistantDeleteUpdateHandler implements AssistantUpdateHandler{
    private final AssistantTelegramBot assistantTelegramBot;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final CalendarService calendarService;
    private static final String ERROR_MSG = "Не получилось удалить мероприятие";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        String[] data = callback.getData().split(TelegramBot.DEFAULT_DELIMETER);
        String eventId = data[1];

        try {
            calendarService.removeEventInCalendar(
                    eventId,
                    userGoogleCalendarService.getUserCalendarIdByTelegramIdOrNull(telegramUser.getTelegramId())
            );
            assistantTelegramBot.sendDeleteMessage(chatId, messageId);
        } catch (IOException e) {
            assistantTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_DELETE.getCommandText();
    }
}
