package com.kuklin.manageapp.bots.pomidorotimer.telegram;

import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.services.PomidoroUserService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.InputMessageHandler;
import com.kuklin.manageapp.common.library.tgmodels.TelegramFacade;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class PomidoroTelegramFacade {
    private final TelegramUserService telegramUserService;
    private final PomidoroUserService pomidoroUserService;
    private final InputMessageHandler inputMessageHandler;

    public BotApiMethod handleUpdate(Update update) {
        Message message;
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();

            message = callbackQuery.getMessage();
            message.setMessageId(callbackQuery.getMessage().getMessageId());
            message.setFrom(callbackQuery.getFrom());
            message.setText(callbackQuery.getData());
        } else {
            message = update.getMessage();
        }
        telegramUserService.createOrGetUserByTelegram(BotIdentifier.POMIDORO_BOT, message.getFrom());
        return handleInputMessage(message);
    }

    private BotApiMethod handleInputMessage(Message message) {
        String inputMsg = message.getText();
        BotState botState = null;
        if (!BotState.fromCommand(inputMsg).equals(BotState.PROCESSING)) {
            botState = BotState.fromCommand(message.getText());
        }
        UserEntity userEntity = pomidoroUserService.getOrCreateUser(message.getFrom(), botState);
        return inputMessageHandler.processInputMessage(message, userEntity);
    }
}
