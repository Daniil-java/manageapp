package com.kuklin.manageapp.bots.pomidorotimer.telegram;

import com.kuklin.manageapp.bots.pomidorotimer.configurations.TelegramPomidoroTimerBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class PomidoroTelegramBot extends TelegramBot {
    @Autowired
    private PomidoroTelegramFacade pomidoroTelegramFacade;
    @Autowired
    private AsyncService asyncService;

    public PomidoroTelegramBot(TelegramPomidoroTimerBotKeyComponents components) {
        super(components.getKey());
    }
    @Override
    public void onUpdateReceived(Update update) {
        boolean result = doAsync(asyncService, update, u -> pomidoroTelegramFacade.handleUpdate(update));

        if (!result) {
            notifyAlreadyInProcess(update);
        }
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
