package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserAuthNotificationService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantStartUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final UserMessagesLogService userMessagesLogService;
    private final UserAuthNotificationService userAuthNotificationService;
    private static final String ADMIN_TELEGRAM_USERNAME = "kuklin_daniil";
    private static final Integer NOTIFY_AFTER_HOURS = 2;
    private static final String MSG = """
            Если вы уже отправили почту админу, и админ вам ответил и написал что “добавил вас”, то авторизуйтесь, введите команду /auth
            """;
    private static final String START_MESSAGE =
            """
                    Этот бот не является коммерческим и не создан для предоставления каких-либо услуг.    
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        assistantTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE,
                getAdminRedirectButton(),
                null
        );
        userAuthNotificationService.create(
                telegramUser.getTelegramId(),
                LocalDateTime.now().plusHours(NOTIFY_AFTER_HOURS),
                MSG
                );
        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                update.getMessage().getText()
        );
    }

    public InlineKeyboardMarkup getAuthButton() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        builder.row(TelegramKeyboard.button("Авторизация", Command.ASSISTANT_AUTH.getCommandText()));

        return builder.build();
    }

    public InlineKeyboardMarkup getAdminRedirectButton() {
        InlineKeyboardButton btn = InlineKeyboardButton.builder()
                .text("Написать админу")
                .url("https://t.me/" + ADMIN_TELEGRAM_USERNAME)   // ссылка на пользователя
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(btn)))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_START.getCommandText();
    }
}
