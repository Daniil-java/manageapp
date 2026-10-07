package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers;

import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public interface MessageHandler {
    BotApiMethod handle(Message message, UserEntity userEntity);

    List<BotState> getHandlerListName();

    @Autowired
    default void registerMyself(InputMessageHandler inputMessageHandler) {
        List<BotState> stateList = getHandlerListName();
        for (BotState state: stateList) {
            inputMessageHandler.register(state.name(), this);
        }
    }

}
