package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.entities.GoogleCacheableCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.GoogleCacheableCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.TokenService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.notificationsettings.AssistantDailyTimeUpdateHandler;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.notificationsettings.AssistantTimeZoneUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantCalendarChooseUpdateHandler implements AssistantUpdateHandler {
    private final CalendarService calendarService;
    private final AssistantTelegramBot telegramBot;
    private final TokenService tokenService;
    private final GoogleCacheableCalendarService cacheableCalendarService;
    private final UserMessagesLogService userMessagesLogService;
    private final AssistantTimeZoneUpdateHandler assistantTimeZoneUpdateHandler;
    private final AssistantDailyTimeUpdateHandler assistantDailyTimeUpdateHandler;
    private static final String DEL = AssistantTelegramBot.DEFAULT_DELIMETER;
    public static final String PREV_CMD = Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText() + DEL + "/prev" + DEL;
    //Команда навигации календаря
    public static final String NEXT_CMD = Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText() + DEL + "/next" + DEL;
    public static final String CHOOSE_CMD = Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText() + DEL + "/id" + DEL;
    public static final String CHOOSE_SUCCESS_MSG = "Выбор сохранен!";
    public static final String CHOOSE_ERROR_MSG = "Не получилось выбрать календарь! Авторизуйтесь!";
    private static final String GOOGLE_OTHER_ERROR_MESSAGE =
            "Попробуйте обратиться позже!";
    private static final String GOOGLE_AUTH_ERROR_MESSAGE =
            "Вам нужно пройти авторизацию заново!";
    private static final String GOOGLE_AUTH_CALLBACK_ERROR_MESSAGE =
            "Возникла ошибка! Проверьте свою авторизацию или напишите ";
    private static final String SUCCESS_MSG = """
            ✅ Календарь успешно подключен!
            Теперь вы можете отправить мне текстовое или голосовое сообщение, например:
            -   послезавтра записаться на стрижку
            -   19 ноября позвонить Сергею в 12:00 (напомнить ему о проекте)
                
            🔹А еще ты можешь:
            -  удалить задачу
            -  переслать задачу и написать “перенеси время на 14:00”
            -  написать /today и посмотреть все свои задачи на сегодня                 
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText()
        );

        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        } else if (update.hasMessage()) {
            processMessage(update, telegramUser);
        }

    }

    public void handleGoogleCallback(AssistantGoogleOAuth auth, boolean isCalendarSet) {
        try {
            if (!isCalendarSet) {
                List<GoogleCacheableCalendar> calendarList = calendarService
                        .listUserCalendarsOrNull(auth.getTelegramId());

                telegramBot.sendReturnedMessage(auth.getTelegramId(), SUCCESS_MSG, getCalendarListKeyboard(calendarList), null);
            } else {
                telegramBot.sendReturnedMessage(auth.getTelegramId(), SUCCESS_MSG);
            }

            assistantTimeZoneUpdateHandler.sendDefTzMessage(auth.getTelegramId());
        } catch (Exception ignore) {
            telegramBot.sendReturnedMessage(auth.getTelegramId(),
                    GOOGLE_AUTH_CALLBACK_ERROR_MESSAGE + Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText());
        }
    }

    public void sendProcessDeniedMessage(Long telegramId) {
        telegramBot.sendReturnedMessage(
                telegramId,
                "❌ Подключение не удалось. Попробуйте снова или обратитесь позже!"
        );
    }

    public void sendCalendarErrorMessage(Long telegramId) {
        telegramBot.sendReturnedMessage(
                telegramId,
                "❌ Не удалось подключить календарь автоматически! Подкилючите его в ручную при помощи команды " + Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText()
        );
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        telegramBot.sendChatActionTyping(chatId);

        try {
            List<GoogleCacheableCalendar> calendarList = calendarService
                    .listUserCalendarsOrNull(telegramUser.getTelegramId());

            log.info("calendarList.size(): " + calendarList.size());
            telegramBot.sendReturnedMessage(chatId, "\uD83D\uDCC5 Доступные календари", getCalendarListKeyboard(calendarList), null);
        } catch (TokenRefreshException e) {
            if (e.getReason().equals(TokenRefreshException.Reason.INVALID_GRANT)) {
                telegramBot.sendReturnedMessage(chatId, GOOGLE_AUTH_ERROR_MESSAGE);
            } else {
                telegramBot.sendReturnedMessage(chatId, GOOGLE_OTHER_ERROR_MESSAGE);
            }
        } catch (Exception e) {
            telegramBot.sendReturnedMessage(chatId, "Ошибка получения календаря");
            log.error("Failed to get list of calendars");
        }
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        String response = callbackQuery.getData();
        telegramBot.sendChatActionTyping(chatId);

        if (response.startsWith(CHOOSE_CMD)) {
            String id = response.substring(CHOOSE_CMD.length());

            GoogleCacheableCalendar googleCacheableCalendar = cacheableCalendarService
                    .findCalendarByIdAndTelegramIdOrNull(Long.valueOf(id), telegramUser.getTelegramId());

            var auth = tokenService.setDefaultCalendarOrNull(
                    telegramUser.getTelegramId(), googleCacheableCalendar.getCalendarId());
            if (auth == null) {
                telegramBot.sendReturnedMessage(chatId, CHOOSE_ERROR_MSG);
            }
            telegramBot.sendEditMessage(chatId, CHOOSE_SUCCESS_MSG, callbackQuery.getMessage().getMessageId(), null);
        }
    }

    public InlineKeyboardMarkup getCalendarListKeyboard(List<GoogleCacheableCalendar> calendarList) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (int i = 0; i < calendarList.size(); i += 2) {
            GoogleCacheableCalendar c1 = calendarList.get(i);
            InlineKeyboardButton btn1 = TelegramKeyboard.button(c1.getSummary(), CHOOSE_CMD + c1.getId());

            if (i + 1 < calendarList.size()) {
                GoogleCacheableCalendar c2 = calendarList.get(i + 1);
                InlineKeyboardButton btn2 = TelegramKeyboard.button(c2.getSummary(), CHOOSE_CMD + c2.getId());
                // Если row принимает два аргумента
                builder.row(btn1, btn2);
            } else {
                builder.row(btn1);
            }
        }

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText();
    }
}
