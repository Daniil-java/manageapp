package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
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
public class AssistantDeleteUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final CalendarService calendarService;
    private final UserMessagesLogService userMessagesLogService;
    private static final String ERROR_MSG = "Не получилось удалить мероприятие";
    private static final String GOOGLE_OTHER_ERROR_MESSAGE =
            "Попробуйте обратиться позже!";
    private static final String GOOGLE_AUTH_ERROR_MESSAGE =
            "Вам нужно пройти авторизацию заново!";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        String[] data = callback.getData().split(TelegramBot.DEFAULT_DELIMETER);
        String eventId = data[1];

        try {
            calendarService.removeEventInCalendar(
                    eventId, telegramUser.getTelegramId()
            );
            assistantTelegramBot.sendDeleteMessage(chatId, messageId);
            userMessagesLogService.createLog(
                    telegramUser.getTelegramId(),
                    telegramUser.getUsername(),
                    telegramUser.getFirstname(),
                    telegramUser.getLastname(),
                    "УДАЛЕНИЕ СОБЫТИЯ ИЗ КАЛЕНДАРЯ"
            );
        } catch (IOException e) {
            assistantTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
        } catch (TokenRefreshException e) {
            if (e.getReason().equals(TokenRefreshException.Reason.INVALID_GRANT)) {
                assistantTelegramBot.sendReturnedMessage(chatId, GOOGLE_AUTH_ERROR_MESSAGE);
            } else {
                assistantTelegramBot.sendReturnedMessage(chatId, GOOGLE_OTHER_ERROR_MESSAGE);
            }
        }
    }


    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_DELETE.getCommandText();
    }
}
