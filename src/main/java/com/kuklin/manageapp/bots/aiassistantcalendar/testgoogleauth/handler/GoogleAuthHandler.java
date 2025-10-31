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

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();

        // TTL одноразовой ссылки: 15 минут
        UUID linkId = linkStateService.createLink(chatId, 15);
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
        return Command.ASSISTANT_AUTH.getCommandText();
    }
}

//package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;
//
//        import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
//        import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
//        import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleOAuthService;
//        import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.LinkStateService;
//        import com.kuklin.manageapp.common.entities.TelegramUser;
//        import com.kuklin.manageapp.common.library.tgutils.Command;
//        import lombok.RequiredArgsConstructor;
//        import org.springframework.stereotype.Component;
//        import org.telegram.telegrambots.meta.api.objects.Update;
//
//        import java.util.UUID;

//@Component
//@RequiredArgsConstructor
//public class GoogleAuthHandler implements AssistantUpdateHandler {
//
//    private final LinkStateService linkStateService;
//    private final AssistantTelegramBot telegramBot;
//    private final GoogleOAuthService oAuthService;
//    // TTL одноразовой ссылки:
//    private static final Integer TTL_TIME_MINUTES = 15;
//    private static final String START_MSG =
//            """
//                    🔐 Подключение Google:
//                    1) Открой ссылку: %s
//                    2) Выбери аккаунт и выдай доступ
//                    После этого вернись в чат и набери /auth_status
//                    """;
//
//    @Override
//    public void handle(Update update, TelegramUser telegramUser) {
//        if (update.hasCallbackQuery()) {
//            processCallback(update, telegramUser);
//        } else {
//            processMessage(update, telegramUser);
//        }
//    }
//
//    private void processCallback(Update update, TelegramUser telegramUser) {
//        Long chatId = update.getCallbackQuery().getMessage().getChatId();
//        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
//        String link = getUrl(telegramUser.getTelegramId());
//        telegramBot.sendEditMessage(
//                chatId,
//                START_MSG.formatted(link),
//                messageId,
//                null
//        );
//    }
//
//    private void processMessage(Update update, TelegramUser telegramUser) {
//        Long chatId = update.getMessage().getChatId();
//        String link = getUrl(telegramUser.getTelegramId());
//        telegramBot.sendReturnedMessage(
//                chatId,
//                START_MSG.formatted(link));
//    }
//
//    private String getUrl(Long telegramId) {
//        UUID linkId = linkStateService.createLink(telegramId, TTL_TIME_MINUTES);
//        return "https://kuklin.dev/auth/google/start?linkId=" + linkId; //TODO Заменить на переменную окружения
//    }
//
//    @Override
//    public String getHandlerListName() {
//        return Command.ASSISTANT_AUTH.getCommandText();
//    }
//}

