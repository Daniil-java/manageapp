package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthStatusUpdateHandler implements AssistantUpdateHandler {
    private final TokenService tokenService;
    private final AssistantTelegramBot telegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();
        try {
            AssistantGoogleOAuth acc = tokenService.get(chatId);
            Instant exp = Optional.ofNullable(acc.getAccessExpiresAt()).orElse(Instant.EPOCH);
            long leftSec = Math.max(0, Duration.between(Instant.now(), exp).getSeconds());

            telegramBot.sendReturnedMessage(chatId, """
                    ✅ Google подключён
                    Email: %s
                    Scope: %s
                    Access token истекает через ~%d сек.
                    """.formatted(
                    Optional.ofNullable(acc.getEmail()).orElse("—"),
                    Optional.ofNullable(acc.getScope()).orElse("—"),
                    leftSec));

            // Дополнительно можно «протестировать» refresh прямо тут:
            try {
                tokenService.ensureAccessToken(chatId);
            } catch (Exception e) {
                telegramBot.sendReturnedMessage(chatId, "⚠️ Не удалось обновить access_token: " + e.getMessage());
            }

        } catch (Exception notAuthorized) {
            telegramBot.sendReturnedMessage(chatId, "❌ Google ещё не подключен. Набери /auth для начала авторизации.");
        }
    }

    @Override
    public String getHandlerListName() {
        return "/auth_status";
    }
}
