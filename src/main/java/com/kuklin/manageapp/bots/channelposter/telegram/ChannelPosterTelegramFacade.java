package com.kuklin.manageapp.bots.channelposter.telegram;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.TelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
@Slf4j
public class ChannelPosterTelegramFacade extends TelegramFacade {

    @Autowired
    private TelegramUserService telegramUserService;

    @Override
    public void handleUpdate(Update update) {
        if (!update.hasCallbackQuery() && !update.hasMessage()) return;
        User user = update.getMessage() != null ?
                update.getMessage().getFrom() :
                update.getCallbackQuery().getFrom();

        TelegramUser telegramUser = telegramUserService
                .createOrGetUserByTelegram(BotIdentifier.CHANNEL_POSTER, user);

        UpdateHandler updateHandler = processInputUpdate(update);
        if (updateHandler != null) {
            updateHandler.handle(update, telegramUser);
        }
    }

    public UpdateHandler processInputUpdate(Update update) {
        String request;
        if (update.hasCallbackQuery()) {
            request = update.getCallbackQuery().getData().split(TelegramBot.DEFAULT_DELIMETER)[0];
        } else {
            if (update.getMessage().hasDocument()) {
                return getUpdateHandlerMap().get(Command.POSTER_GET_ARTICLE.getCommandText());
            }
            request = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER)[0];
        }

        UpdateHandler updateHandler = getUpdateHandlerMap().get(request);
        if (updateHandler == null) {
            log.info("ChannelPosterFacade: no handler!");
        }
        return updateHandler;
    }
}
