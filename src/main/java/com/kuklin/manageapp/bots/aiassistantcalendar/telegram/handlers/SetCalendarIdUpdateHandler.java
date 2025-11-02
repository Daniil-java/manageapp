package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserGoogleCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class SetCalendarIdUpdateHandler implements AssistantUpdateHandler{
    private final AssistantTelegramBot assistantTelegramBot;
    private final UserGoogleCalendarService userGoogleCalendarService;
    private final CalendarService calendarService;
    private static final String SUCCESS_MSG = "Календарь установлен";
    private static final String ERROR_MSG = "Неверный формат команды";
    public static final String CALENDAR_IS_NULL_MSG = "Вам необходимо установить свой календарь! Для инструкций введите команду /start";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Ожидается сообщение формата /set calendarId
        Message message = update.getMessage();
        Long chatId = message.getChatId();

        String calendarId = extractCalendarId(message.getText());
        if (calendarId == null) {
            assistantTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
        }
        if (!checkCalendarConnection(telegramUser.getTelegramId())) {
            assistantTelegramBot.sendReturnedMessage(chatId, "Календарь или не существует, или к нему не установлен доступ!");
        }

        userGoogleCalendarService.setCalendarIdByTelegramId(telegramUser.getTelegramId(), calendarId);
        assistantTelegramBot.sendReturnedMessage(chatId, SUCCESS_MSG);
    }

    private boolean checkCalendarConnection(Long telegramId) {
        String calendarId = userGoogleCalendarService
                .getUserCalendarIdByTelegramIdOrNull(telegramId);
        return calendarService.existConnectionCalendarWithNoAuth(calendarId);
    }

    private String extractCalendarId(String message) {
        String[] parts = message.split(TelegramBot.DEFAULT_DELIMETER);

        if (parts.length != 2) return null;
        return parts[1];
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_SET_CALENDARID.getCommandText();
    }
}
