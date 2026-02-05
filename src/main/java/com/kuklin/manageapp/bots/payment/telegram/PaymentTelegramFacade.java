package com.kuklin.manageapp.bots.payment.telegram;

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
public class PaymentTelegramFacade extends TelegramFacade {
    @Autowired
    private TelegramUserService telegramUserService;

    @Override
    public void handleUpdate(Update update) {
        if (!update.hasCallbackQuery()
                && !update.hasMessage()
                && !update.hasPreCheckoutQuery()) return;

        User user = update.hasMessage() ?
                update.getMessage().getFrom() :
                update.hasPreCheckoutQuery()
                        ? update.getPreCheckoutQuery().getFrom()
                        : update.getCallbackQuery().getFrom();

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

        if (update.hasPreCheckoutQuery()) {
            return getUpdateHandlerMap().get(Command.PAYMENT_PRE_CHECK_QUERY.getCommandText());
        }

        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            return getUpdateHandlerMap().get(Command.PAYMENT_SUCCESS.getCommandText());
        }

        if (update.hasCallbackQuery()) {
            request = update.getCallbackQuery().getData().split(TelegramBot.DEFAULT_DELIMETER)[0];

            return getUpdateHandlerMap().get(request);

        } else if (update.hasMessage()) {
            request = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER)[0];

            return getUpdateHandlerMap().get(request);
        }

        return null;
    }
}
