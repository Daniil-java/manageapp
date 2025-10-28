package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class GoogleAuthHandler implements AssistantUpdateHandler {

    private final OAuthLinkStateService linkState;
    private final AssistantTelegramBot assistantBot; // используем твой класс напрямую

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        long chatId = update.getMessage() != null
                ? update.getMessage().getChatId()
                : update.getCallbackQuery().getMessage().getChatId();

        // Создаём одноразовый link (TTL ~15 мин), чтобы связать браузер с нужным chatId
        String link = linkState.issueOneTimeLink(chatId);

        // Ссылка на наш старт OAuth. Важно открыть в системном браузере.
        String url = linkState.getBaseUrl() + "/auth/google/start?link=" + link;

        assistantBot.sendReturnedMessage(chatId,
                "Авторизация Google (Calendar):\n" + url +
                        "\nЕсли не открылось, скопируй ссылку в системный браузер.");
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_AUTH.getCommandText(); // например "/auth"
    }
}
