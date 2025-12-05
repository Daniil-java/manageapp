package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleOAuthService;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.LinkStateService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
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
    // TTL одноразовой ссылки:
    private static final Integer TTL_TIME_MINUTES = 15;
    private static final String START_MSG =
            """
                    🔐 Подключение Google:
                    1) Открой ссылку: %s
                    Ссылка одноразовая!
                    2) Выбери аккаунт и выдай доступ
                    
                    После этого вернись в чат и набери /auth_status
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

        String link = getUrl(telegramUser.getTelegramId());
        telegramBot.sendReturnedMessage(
                chatId,
                START_MSG.formatted(link));
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

