package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.TelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Component
@Slf4j
public class TelegramCalorieBotFacade extends TelegramFacade {
    @Autowired
    private TelegramUserService telegramUserService;

    @Override
    public void handleUpdate(Update update) {
        if (!update.hasCallbackQuery() && !update.hasMessage()) return;
        User user = update.getMessage() != null ?
                update.getMessage().getFrom() :
                update.getCallbackQuery().getFrom();

        TelegramUser telegramUser = telegramUserService
                .createOrGetUserByTelegram(BotIdentifier.CALORIE_BOT, user);

        UpdateHandler updateHandler = processInputUpdate(update);
        if (updateHandler == null) {
            log.error("Не удалось найти подходящий хендлер. Ответа не будет");
        } else {
            updateHandler.handle(update, telegramUser);
        }
    }

    public UpdateHandler processInputUpdate(Update update) {
        String request = null;

        if (update.hasCallbackQuery()) {
            request = update.getCallbackQuery().getData().split(" ")[0];
            if (request == null) {
                return null;
            } else {
                return getUpdateHandlerMap().get(Command.CALORIE_DELETE.getCommandText());
            }
        } else if (update.hasMessage()) {
            var message = update.getMessage();

            if (message.hasPhoto()) {
                return getUpdateHandlerMap().get(Command.CALORIE_GENERAL.getCommandText());
            }

            request = message.getText();
        }

        // если request пустой (и это не callback), возвращаем GENERAL
        if (request == null) {
            return getUpdateHandlerMap().get(Command.CALORIE_GENERAL.getCommandText());
        }

        UpdateHandler updateHandler = getUpdateHandlerMap().get(request);
        return updateHandler == null ? getUpdateHandlerMap().get(Command.CALORIE_GENERAL.getCommandText()) : updateHandler;


    }
}
