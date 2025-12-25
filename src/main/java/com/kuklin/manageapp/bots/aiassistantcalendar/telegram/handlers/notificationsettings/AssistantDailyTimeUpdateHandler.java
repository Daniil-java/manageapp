package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.notificationsettings;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserNotificationSettings;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserNotificationSettingsService;
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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantDailyTimeUpdateHandler implements AssistantUpdateHandler {

    private final AssistantTelegramBot assistantTelegramBot;
    private final UserNotificationSettingsService userNotificationSettingsService;
    private final UserMessagesLogService userMessagesLogService;

    private static final String CMD = Command.ASSISTANT_DAILY_TIME.getCommandText();

    private static final String MSG = "Выбери время ежедневного уведомления:";
    private static final String TIME_SET_MSG = "Время ежедневного уведомления установлено: ";
    private static final String ERROR_MSG = "Не получилось определить время, попробуй ещё раз.";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            // Пришло сообщение (команда) – отправляем новое сообщение с клавиатурой
            processMessage(update, telegramUser);
        } else if (update.hasCallbackQuery()) {
            // Пришёл callback от кнопки – разбираем и сохраняем время
            processCallback(update, telegramUser);
        }
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);

        userMessagesLogService.createLog(telegramUser, update.getMessage().getText());
        UserNotificationSettings settings = userNotificationSettingsService
                .getOrCreate(telegramUser.getTelegramId());

        assistantTelegramBot.sendReturnedMessage(
                chatId,
                getDailyMessageSettings(settings),
                buildTimeKeyboard(settings),
                null
        );
    }

    private String getDailyMessageSettings(UserNotificationSettings settings) {
        StringBuilder sb = new StringBuilder();

        sb
                .append("Время уведомления: ").append(settings.getDailyTime()).append("\n")
                .append("Статус: ").append(settings.isDailyEnabled() ? "включены" : "выключены")
                .append("\n\n").append(MSG)
        ;

        return sb.toString();
    }

    public void sendDefMessage(Long telegramId) {
        UserNotificationSettings settings = userNotificationSettingsService
                .getOrCreate(telegramId);
        assistantTelegramBot.sendReturnedMessage(
                telegramId,
                getDailyMessageSettings(settings),
                buildTimeKeyboard(settings),
                null
        );
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackData = callbackQuery.getData();

        userMessagesLogService.createLog(telegramUser, callbackData);
        UserNotificationSettings settings = userNotificationSettingsService
                .getOrCreate(telegramUser.getTelegramId());

        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        // Если пришёл только командный префикс — просто перерисуем клавиатуру
        if (parts.length == 1) {
            assistantTelegramBot.sendEditMessage(
                    chatId,
                    getDailyMessageSettings(settings),
                    messageId,
                    buildTimeKeyboard(settings)
            );
            return;
        }

        if (parts.length < 2 || !CMD.equals(parts[0])) {
            // Не наш callback – игнорируем
            return;
        }

        String part = parts[1];

        if (part.equals("true") || part.equals("false")) {
            settings = userNotificationSettingsService.updateDailyEnabled(
                    telegramUser.getTelegramId(),
                    !settings.isDailyEnabled()
            );

            // Обновляем исходное сообщение и убираем клавиатуру
            assistantTelegramBot.sendEditMessage(
                    chatId,
                    getDailyMessageSettings(settings),
                    messageId,
                    buildTimeKeyboard(settings)
            );
            return;
        }

        try {
            // Парсим "HH:mm" в LocalTime
            LocalTime time = LocalTime.parse(part);

            // Сохраняем время утреннего уведомления
            userNotificationSettingsService.updateDailyTime(
                    telegramUser.getTelegramId(),
                    time
            );

            String text = TIME_SET_MSG + part;

            // Обновляем исходное сообщение и убираем клавиатуру
            assistantTelegramBot.sendEditMessage(
                    chatId,
                    text,
                    messageId,
                    null
            );
        } catch (Exception e) {
            log.error("Ошибка парсинга времени из callbackData: {}", callbackData, e);
            assistantTelegramBot.sendReturnedMessage(
                    chatId,
                    ERROR_MSG
            );
        }
    }

    /**
     * Строим клавиатуру с временем от 00:00 до 23:30, шаг 30 минут.
     * callbackData: "<CMD><DELIM>HH:mm"
     */
    private InlineKeyboardMarkup buildTimeKeyboard(UserNotificationSettings settings) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 30) {
                String time = String.format("%02d:%02d", hour, minute);
                String text = time;
                String callbackData = CMD + TelegramBot.DEFAULT_DELIMETER + time;

                row.add(TelegramKeyboard.button(text, callbackData));

                // по 4 кнопки в ряд, чтобы не разъезжалось
                if (row.size() == 4) {
                    builder.row(row);
                    row = new ArrayList<>();
                }
            }
        }

        // добиваем остаток, если не кратно 4
        if (!row.isEmpty()) {
            builder.row(row);
        }

        String callbackData = CMD + TelegramBot.DEFAULT_DELIMETER + settings.isDailyEnabled();
        String text = settings.isDailyEnabled() ? "Выключить" : "Включить";
        builder.row(TelegramKeyboard.button(text, callbackData));

        builder.row("Закрыть", Command.ASSISTANT_CLOSE.getCommandText());
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return CMD;
    }
}
