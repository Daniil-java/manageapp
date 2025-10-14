package com.kuklin.manageapp.bots.pomidorotimer.telegram;

import com.kuklin.manageapp.bots.pomidorotimer.configurations.TelegramPomidoroTimerBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class PomidoroTelegramBot extends TelegramBot {
    @Autowired
    private PomidoroTelegramFacade pomidoroTelegramFacade;

    public PomidoroTelegramBot(TelegramPomidoroTimerBotKeyComponents components) {
        super(components.getKey());
    }
    @Override
    public void onUpdateReceived(Update update) {
        sendMessage(pomidoroTelegramFacade.handleUpdate(update));
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.POMIDORO_BOT;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.POMIDORO_BOT.getBotUsername();
    }
}
