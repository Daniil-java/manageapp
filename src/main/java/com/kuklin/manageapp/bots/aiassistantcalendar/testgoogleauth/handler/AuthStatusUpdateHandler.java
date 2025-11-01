package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.google.api.services.calendar.model.CalendarListEntry;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.CalendarService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthStatusUpdateHandler implements AssistantUpdateHandler {
    private final TokenService tokenService;
    private final CalendarService calendarService;
    private final AssistantTelegramBot telegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();
        try {
            AssistantGoogleOAuth acc = tokenService.findByTelegramIdOrNull(telegramUser.getTelegramId());

//            CalendarListEntry calendarListEntry =  calendarService.getCalendarOrNull(telegramUser.getTelegramId());
            telegramBot.sendReturnedMessage(chatId, """
                    ✅ Google подключён
                    Email: %s
                    Календарь: %s
                    """.formatted(
                    Optional.ofNullable(acc.getEmail()).orElse("—")
//                    Optional.ofNullable(calendarListEntry.getSummary()).orElse("-")
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
        return "/auth_status";
    }
}
