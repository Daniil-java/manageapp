package com.kuklin.manageapp.bots.deparrbot.telegram;

import com.kuklin.manageapp.bots.deparrbot.configurations.TelegramAviaBotKeyComponent;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class AviaTelegramBot extends TelegramBot {
    @Autowired
    private AviaTelegramFacade aviaTelegramFacade;

    public AviaTelegramBot(TelegramAviaBotKeyComponent components) {
        super(components.getKey());
    }
    @Override
    public void onUpdateReceived(Update update) {
        aviaTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.AVIA_BOT;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.AVIA_BOT.getBotUsername();
    }
}
