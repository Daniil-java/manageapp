package com.kuklin.manageapp.bots.pomidorotimer.telegram;

import com.kuklin.manageapp.bots.pomidorotimer.configurations.TelegramPomidoroTimerBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@Slf4j
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
        boolean result = doAsync(asyncService, update, u ->  {
            BotApiMethod method = pomidoroTelegramFacade.handleUpdate(update);

            if (method != null) {
                try {
                    execute(method);
                } catch (TelegramApiException e) {
                    log.error("Pomidoro timer: telegram execute error!");
                }
            }
        });

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
