package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.GoogleCacheableCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleCacheableCalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
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
    private static final String DEL = AssistantTelegramBot.DELIMETER;
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

    @Override
    public void handle(Update update, TelegramUser telegramUser) {

        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        } else if (update.hasMessage()) {
            processMessage(update, telegramUser);
        }

    }

    public void handleGoogleCallback(AssistantGoogleOAuth auth) {
        try {
            List<GoogleCacheableCalendar> calendarList = calendarService
                    .listUserCalendarsOrNull(auth.getTelegramId());

            String response = """
                    Успешная авторизация!
                    email: %s
                    """.formatted(auth.getEmail());
            telegramBot.sendReturnedMessage(auth.getTelegramId(), response, getCalendarListKeyboard(calendarList), null);
        } catch (Exception ignore) {
            telegramBot.sendReturnedMessage(auth.getTelegramId(),
                    GOOGLE_AUTH_CALLBACK_ERROR_MESSAGE+ Command.ASSISTANT_CHOOSE_CALENDAR.getCommandText());
        }
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();

        try {
            log.info("handler listUserCalendarsOrNull");
            List<GoogleCacheableCalendar> calendarList = calendarService
                    .listUserCalendarsOrNull(telegramUser.getTelegramId());
            //TODO
//            List<GoogleCacheableCalendar> calendarList = List.of(
//                    new GoogleCacheableCalendar().setId(1L).setSummary("calendar1"),
//                    new GoogleCacheableCalendar().setId(1L).setSummary("calendar1"),
//                    new GoogleCacheableCalendar().setId(1L).setSummary("calendar1")
//            );

            log.info("calendarList.size(): " + calendarList.size());
            StringBuilder sb = new StringBuilder();
            for (GoogleCacheableCalendar calendar : calendarList) {
                sb.append(calendar.getSummary()).append("\n");
            }
            telegramBot.sendReturnedMessage(chatId, sb.toString(), getCalendarListKeyboard(calendarList), null);
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
