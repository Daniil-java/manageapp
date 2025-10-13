package com.kuklin.manageapp.bots.hhparserbot.telegram;

import com.kuklin.manageapp.bots.hhparserbot.configurations.TelegramHhParserBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HhTelegramBot extends TelegramBot {
    @Autowired
    private HhTelegramFacade hhTelegramFacade;
    public HhTelegramBot(TelegramHhParserBotKeyComponents components) {
        super(components.getKey());
    }
    @Override
    public void onUpdateReceived(Update update) {
        hhTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.HH_BOT;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.HH_BOT.getBotUsername();
    }
}
