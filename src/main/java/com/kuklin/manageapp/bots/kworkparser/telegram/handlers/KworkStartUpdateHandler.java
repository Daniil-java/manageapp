package com.kuklin.manageapp.bots.kworkparser.telegram.handlers;

import com.kuklin.manageapp.bots.kworkparser.telegram.KworkParserTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class KworkStartUpdateHandler implements KworkUpdateHandler {

    private static final String START_MESSAGE = "Отправь ссылку, которая начинается на https://kwork.ru/projects";
    private final KworkParserTelegramBot kworkParserTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        kworkParserTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), START_MESSAGE);
    }

    @Override
    public String getHandlerListName() {
        return Command.KWORK_START.getCommandText();
    }
}
