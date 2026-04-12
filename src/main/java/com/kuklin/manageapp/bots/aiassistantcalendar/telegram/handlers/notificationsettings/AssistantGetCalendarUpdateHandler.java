package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.notificationsettings;

import com.google.api.services.calendar.model.Calendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AssistantGetCalendarUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final CalendarService calendarService;
    private final UserMessagesLogService userMessagesLogService;
    private static final String TOKEN_ERROR_MSG = """
                Проверьте статус вашей авторизации!
            """;
    private static final String GOOGLE_ERROR_MSG = """
                Ошибка соединения с Google! Попробуйте повторить действие позже!
            """;
    private static final String NULL_CALENDAR_MSG = """
            Вам нужно авторизоваться или поставить календарь вручную! Введите команду /start!
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);
        try {
            if (update.hasMessage()) processMessage(update, telegramUser);
            else if (update.hasCallbackQuery()) processCallback(update, telegramUser);
        } catch (TokenRefreshException e) {
            assistantTelegramBot.sendReturnedMessage(chatId, TOKEN_ERROR_MSG);
        } catch (IOException e) {
            assistantTelegramBot.sendReturnedMessage(chatId, GOOGLE_ERROR_MSG);
        }

    }

    private void processMessage(Update update, TelegramUser telegramUser) throws TokenRefreshException, IOException {
        Long chatId = update.getMessage().getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);

        userMessagesLogService.createLog(telegramUser, update.getMessage().getText());
        Calendar calendar = calendarService.getCalendarByTelegramIdOrNull(telegramUser.getTelegramId());
        if (calendar == null) {
            assistantTelegramBot.sendReturnedMessage(
                    chatId, NULL_CALENDAR_MSG);
            return;
        }
        assistantTelegramBot.sendReturnedMessage(
                chatId, getCalendarString(calendar),
                buildKeyboard(calendar.getId()), null);
    }

    private void processCallback(Update update, TelegramUser telegramUser) throws TokenRefreshException, IOException {
        //Извлечение данных
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        userMessagesLogService.createLog(telegramUser, callbackQuery.getData());
        Calendar calendar = calendarService.getCalendarByTelegramIdOrNull(telegramUser.getTelegramId());
        assistantTelegramBot.sendEditMessage(
                chatId, getCalendarString(calendar),
                messageId, buildKeyboard(calendar.getId()));
    }

    private String getCalendarString(Calendar calendar) {


        StringBuilder sb = new StringBuilder();

        sb
                .append("Календарь: ")
                .append(calendar.getSummary()).append("\n")
                .append("Часовой пояс: ").append(calendar.getTimeZone());

        return sb.toString();
    }

    private InlineKeyboardMarkup buildKeyboard(String calendarId) {
        String calendarUrl = "https://calendar.google.com/calendar/u/0/r?cid="
                + URLEncoder.encode(calendarId, StandardCharsets.UTF_8);

        InlineKeyboardButton openCalendarButton = InlineKeyboardButton.builder()
                .text("Открыть календарь")
                .url(calendarUrl)
                .build();

        List<InlineKeyboardButton> row = List.of(openCalendarButton);
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_GET_CALENDAR.getCommandText();
    }
}
