package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.google.TokenService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthStatusUpdateHandler implements UpdateHandler {
    private final TokenService tokenService;
    private final AssistantTelegramBot telegramBot;
    private final UserMessagesLogService userMessagesLogService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();

        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                update.getMessage().getText()
        );
        try {
            AssistantGoogleOAuth acc = tokenService.findByTelegramIdOrNull(telegramUser.getTelegramId());

            telegramBot.sendReturnedMessage(chatId, """
                    ✅ Google подключён
                    Email: %s
                    """.formatted(
                    Optional.ofNullable(acc.getEmail()).orElse("—")
                    )
            );

            // Дополнительно можно «протестировать» refresh прямо тут:
            try {
                tokenService.ensureAccessTokenOrNull(chatId);
            } catch (Exception e) {
                telegramBot.sendReturnedMessage(chatId, "⚠️ Не удалось обновить access_token: " + e.getMessage());
            }

        } catch (Exception notAuthorized) {
            log.error(notAuthorized.getMessage());
            telegramBot.sendReturnedMessage(chatId, "❌ Google ещё не подключен. Набери /auth для начала авторизации.");
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_AUTH_STATUS.getCommandText();
    }
}
