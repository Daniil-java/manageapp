package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleOAuthService;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.LinkStateService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoogleAuthHandler implements AssistantUpdateHandler {

    private final LinkStateService linkStateService;
    private final AssistantTelegramBot telegramBot;
    private final GoogleOAuthService oAuthService;
    // TTL одноразовой ссылки:
    private static final Integer TTL_TIME_MINUTES = 15;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();

        UUID linkId = linkStateService.createLink(chatId, TTL_TIME_MINUTES);
        String url = "https://kuklin.dev/auth/google/start?linkId=" + linkId;

        telegramBot.sendReturnedMessage(chatId, """
                🔐 Подключение Google:
                1) Открой ссылку: %s
                2) Выбери аккаунт и выдай доступ
                После этого вернись в чат и набери /auth_status
                """.formatted(url));
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_AUTH.getCommandText(); // например "/auth"
    }
}
