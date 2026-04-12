package com.kuklin.manageapp.bots.nicotinebot;

import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterBotKeyComponent;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class NicotineTelegramBot extends TelegramBot {

    public NicotineTelegramBot(NicotineBotKeyComponent components) {
        super(components.getKey());
    }

    @Autowired
    private NicotineTelegramFacade nicotineTelegramFacade;

    @Override
    public void handleUpdateDirectly(Update update) {
        onUpdateReceived(update);
    }

    @Override
    public void onUpdateReceived(Update update) {
        nicotineTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.NICOTINE_BOT;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.NICOTINE_BOT.getBotUsername();
    }
}
