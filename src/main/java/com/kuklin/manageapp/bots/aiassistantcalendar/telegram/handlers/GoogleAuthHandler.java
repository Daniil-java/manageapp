package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.auth.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.LinkStateService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthHandler implements AssistantUpdateHandler {

    private final LinkStateService linkStateService;
    private final AssistantTelegramBot telegramBot;
    private final GoogleOAuthProperties props;
    private final UserMessagesLogService userMessagesLogService;
    // TTL одноразовой ссылки:
    private static final Integer TTL_TIME_MINUTES = 15;
    private static final String START_MSG =
            """
                    🔐 Подключение Google:
                    
                    1) Перейдите по ссылке и выдайте доступ.
                    Не пугайтесь, если будет написано “Эксперты Google не проверяли это приложение”. Бот находиться в тестовом режиме.\s
                    
                    2) Выбери аккаунт и разрешите доступ.
                    
                    Ваша одноразовая, персональная ссылка: 
                    %s
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        } else {
            processMessage(update, telegramUser);
        }
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        saveLog(update, telegramUser, Command.ASSISTANT_AUTH.getCommandText());
        String link = getUrl(telegramUser.getTelegramId());
        telegramBot.sendEditMessage(
                chatId,
                START_MSG.formatted(link),
                messageId,
                null
        );
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();

        saveLog(update, telegramUser, update.getMessage().getText());

        String link = getUrl(telegramUser.getTelegramId());
        telegramBot.sendReturnedMessage(
                chatId,
                START_MSG.formatted(link));
    }

    private void saveLog(Update update, TelegramUser telegramUser, String text) {
        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                text
        );
    }

    private String getUrl(Long telegramId) {
        UUID linkId = linkStateService.createLink(telegramId, TTL_TIME_MINUTES);
        return props.getStartUri() + linkId;
    }


    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_AUTH.getCommandText();
    }
}

