package com.kuklin.manageapp.bots.kworkparser.telegram;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
public class KworkTelegramFacade extends TelegramFacade {

    @Autowired
    private TelegramUserService telegramUserService;
    @Override
    public void handleUpdate(Update update) {
        if (!update.hasCallbackQuery() && !update.hasMessage()) return;
        User user = update.getMessage() != null ?
                update.getMessage().getFrom() :
                update.getCallbackQuery().getFrom();

        TelegramUser telegramUser = telegramUserService
                .createOrGetUserByTelegram(BotIdentifier.KWORK, user);

        processInputUpdate(update).handle(update, telegramUser);
    }

    public UpdateHandler processInputUpdate(Update update) {
        if (update.hasMessage()) {
            if (update.getMessage().getText().equals(Command.KWORK_START.getCommandText())) {
                return getUpdateHandlerMap().get(Command.KWORK_START.getCommandText());
            }
        }
        return getUpdateHandlerMap().get(Command.KWORK_URL.getCommandText());

    }
}
