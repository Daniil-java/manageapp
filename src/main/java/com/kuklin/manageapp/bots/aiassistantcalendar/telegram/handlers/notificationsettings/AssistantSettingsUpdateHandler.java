package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.notificationsettings;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserNotificationSettings;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserNotificationSettingsService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantSettingsUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final CalendarService calendarService;
    private final UserNotificationSettingsService userNotificationSettingsService;
    private final UserMessagesLogService userMessagesLogService;
    private static final String TZ_ERROR_MSG = """
                Не получилось определить часовой пояс! Попробуйте повторить действие позже!
            """;
    private static final String GOOGLE_ERROR_MSG = """
                Не получилось обновить время в гугл календаре! Попробуйте повторить действие позже!
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);
        assistantTelegramBot.sendDeleteMessage(chatId, update.getMessage().getMessageId());

        userMessagesLogService.createLog(telegramUser, update.getMessage().getText());
        try {
            assistantTelegramBot.sendReturnedMessage(
                    chatId,
                    getSettingsString(telegramUser.getTelegramId()),
                    buildSettingsKeyboard(),
                    null
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

    //TODO Ошибка если нет календаря
    private String getSettingsString(Long telegramId) throws TokenRefreshException, IOException {
        String calendar = calendarService.getCalendarSettingsString(telegramId);
        UserNotificationSettings settings = userNotificationSettingsService.getOrCreate(telegramId);
        StringBuilder sb = new StringBuilder();

        String notify = "выключены";
        if (settings.getDailyTime() != null && settings.isDailyEnabled()) {
            notify = settings.getDailyTime().toString();
        }
        sb
                .append(calendar).append("\n")
                .append("Ежедневыне уведомления: ").append(notify)
        ;

        return sb.toString();
    }

    private InlineKeyboardMarkup buildSettingsKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        String tzData = Command.ASSISTANT_TZ.getCommandText();
        String dailyNotifyData = Command.ASSISTANT_DAILY_TIME.getCommandText();

        builder.row(TelegramKeyboard.button("Часовой пояс", tzData));
        builder.row(TelegramKeyboard.button("Ежедневные уведомления", dailyNotifyData));
        builder.row("Закрыть", Command.ASSISTANT_CLOSE.getCommandText());

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_SETTINGS.getCommandText();
    }
}
