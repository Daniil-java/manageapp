package com.kuklin.manageapp.bots.kworkparser.telegram;

import com.kuklin.manageapp.bots.kworkparser.configurations.TelegramKworkParserBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class KworkParserTelegramBot extends TelegramBot {
    public static final BotIdentifier BOT_IDENTIFIER = BotIdentifier.KWORK;
    @Autowired
    private KworkTelegramFacade kworkTelegramFacade;

    public KworkParserTelegramBot(TelegramKworkParserBotKeyComponents components) {
        super(components.getKey());
    }
    @Override
    public void onUpdateReceived(Update update) {
        kworkTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BOT_IDENTIFIER;
    }

    @Override
    public String getBotUsername() {
        return BOT_IDENTIFIER.getBotUsername();
    }
}
