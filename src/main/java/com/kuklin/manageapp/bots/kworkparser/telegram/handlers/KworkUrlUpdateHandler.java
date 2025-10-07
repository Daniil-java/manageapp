package com.kuklin.manageapp.bots.kworkparser.telegram.handlers;

import com.kuklin.manageapp.bots.kworkparser.services.UrlService;
import com.kuklin.manageapp.bots.kworkparser.telegram.KworkParserTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.net.URL;

@Component
@RequiredArgsConstructor
public class KworkUrlUpdateHandler implements KworkUpdateHandler{
    private final KworkParserTelegramBot kworkParserTelegramBot;
    private final UrlService urlService;
    private static final String ERROR_MSG = "Не корректный URL";
    private static final String SUCCESS_MSG = "Ссылка сохранена";

    @Override
    public void handle(Update update, TelegramUser userEntity) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();

        if (!validateUrl(text)) {
            kworkParserTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
            return;
        }

        urlService.addUrlToUser(text.trim(), userEntity.getTelegramId());
        kworkParserTelegramBot.sendReturnedMessage(chatId, SUCCESS_MSG);
    }

    private boolean validateUrl(String text) {
        text = text.trim();
        try {
            URL url = new URL(text);
            url.toURI(); // проверка на корректность URI

            // Проверяем хост и путь
            return "kwork.ru".equalsIgnoreCase(url.getHost())
                    && url.getPath().startsWith("/projects");

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.KWORK_URL.getCommandText();
    }
}
