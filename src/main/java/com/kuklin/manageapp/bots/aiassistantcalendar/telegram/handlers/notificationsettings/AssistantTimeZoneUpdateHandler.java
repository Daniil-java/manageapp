package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.notificationsettings;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserNotificationSettings;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserNotificationSettingsService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantTimeZoneUpdateHandler implements AssistantUpdateHandler {

    private final AssistantTelegramBot assistantTelegramBot;
    private final UserMessagesLogService userMessagesLogService;
    private final CalendarService calendarService;
    private final UserNotificationSettingsService userNotificationSettingsService;

    private static final String CMD = Command.ASSISTANT_TZ.getCommandText();
    private static final String MSG = "Выбери свой часовой пояс (формат UTC+N): ";
    private static final String TZ_MSG = "Часовой пояс установлен:\nUTC+";
    private static final String TZ_ERROR_MSG = """
                Не получилось определить часовой пояс! Попробуйте повторить действие позже!
            """;
    private static final String GOOGLE_ERROR_MSG = """
                Не получилось обновить время в гугл календаре! Попробуйте повторить действие позже!
            """;
    private static final String CALENDAR_ERROR_MSG = """
                У вас не установлен календарь!
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Если пришло сообщение - то отправляем новое сообщение
        if (update.hasMessage()) processMessage(update, telegramUser);
            //Если пришел колбэк, то значит надо отредактировать сообщение
            //Также отвечает за установки таймзоны в БД
        else if (update.hasCallbackQuery()) processCallback(update, telegramUser);
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);
        userMessagesLogService.createLog(telegramUser, update.getMessage().getText());

        UserNotificationSettings settings = userNotificationSettingsService.getOrCreate(telegramUser.getTelegramId());

        assistantTelegramBot.sendReturnedMessage(chatId, getTZSettings(settings), buildTimezoneKeyboard(), null);
    }

    public void sendDefTzMessage(Long telegramId) {
        UserNotificationSettings settings = userNotificationSettingsService.getOrCreate(telegramId);
        assistantTelegramBot.sendReturnedMessage(telegramId, getTZSettings(settings), buildTimezoneKeyboard(), null);
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        //Извлечение данных
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackData = callbackQuery.getData();

        userMessagesLogService.createLog(telegramUser, callbackData);

        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);
        //Если в колбэк-данных нет ничего, кроме комманды - то
        // возвращаем измененное сообщение
        if (parts.length == 1) {
            UserNotificationSettings settings = userNotificationSettingsService
                    .getOrCreate(telegramUser.getTelegramId());
            assistantTelegramBot.sendEditMessage(
                    chatId, getTZSettings(settings), messageId, buildTimezoneKeyboard());
            return;
        }
        if (parts.length < 2 || !CMD.equals(parts[0])) return;

        //Устанавливаем таймзону
        try {
            int offset = Integer.parseInt(parts[1]);

            // Сохраняем смещение в настройках пользователя
            UserNotificationSettings settings = userNotificationSettingsService.updateUtcOffset(
                    telegramUser.getTelegramId(),
                    offset
            );

            var newTime = calendarService
                    .setNewTimeZoneOrNull(
                            telegramUser.getTelegramId(),
                            offset);

            if (newTime == null) {
                assistantTelegramBot.sendReturnedMessage(
                        chatId, CALENDAR_ERROR_MSG);
                return;
            }

            String text = TZ_MSG + offset;

            // Обновляем исходное сообщение и убираем клавиатуру
            assistantTelegramBot.sendEditMessage(chatId, text, messageId, null);
        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга смещения часового пояса из callbackData: {}", callbackData, e);
            assistantTelegramBot.sendReturnedMessage(
                    chatId, TZ_ERROR_MSG
            );
        } catch (TokenRefreshException e) {
            log.error("Invalid google token: telegram_id:{}", telegramUser.getTelegramId(), e);
            assistantTelegramBot.sendReturnedMessage(
                    chatId, TZ_ERROR_MSG
            );
        } catch (IOException e) {
            log.error("Google connection error!: telegram_id{}", telegramUser.getTelegramId(), e);
            assistantTelegramBot.sendReturnedMessage(
                    chatId, GOOGLE_ERROR_MSG
            );
        }
    }

    private String getTZSettings(UserNotificationSettings settings) {
        StringBuilder sb = new StringBuilder();

        String tz = "UTC+3";
        if (settings.getUtcOffsetHours() != null) {
            tz = "UTC+" + settings.getUtcOffsetHours();
        }

        sb
                .append("Текущий часовой пояс: ").append(tz).append("\n")
                .append(MSG)
        ;

        return sb.toString();
    }

    /**
     * Строим клавиатуру с UTC+0 ... UTC+14, по 3 кнопки в строке.
     * callbackData: "/timezone <offset>"
     */
    private InlineKeyboardMarkup buildTimezoneKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (int offset = 0; offset <= 14; offset++) {
            String text = "UTC+" + offset;
            String callbackData = CMD + TelegramBot.DEFAULT_DELIMETER + offset;
            row.add(TelegramKeyboard.button(text, callbackData));

            // По 3 кнопки в строке
            if (row.size() == 3 || offset == 14) {
                builder.row(row);
                row = new ArrayList<>();
            }
        }
        builder.row("Закрыть", Command.ASSISTANT_CLOSE.getCommandText());

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_TZ.getCommandText();
    }
}
