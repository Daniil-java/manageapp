package com.kuklin.manageapp.bots.hhparserbot.telegram;

import com.kuklin.manageapp.bots.hhparserbot.configurations.TelegramHhParserBotKeyComponents;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HhTelegramBot extends TelegramBot {
    public static final BotIdentifier BOT_IDENTIFIER = BotIdentifier.HH_BOT;
    @Autowired
    private HhTelegramFacade hhTelegramFacade;
    public HhTelegramBot(TelegramHhParserBotKeyComponents components) {
        super(components.getKey());
    }

    @Override
    public void handleUpdateDirectly(Update update) {
        hhTelegramFacade.handleUpdate(update);
    }

    @Override
    public void onUpdateReceived(Update update) {
        hhTelegramFacade.handleUpdate(update);
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
