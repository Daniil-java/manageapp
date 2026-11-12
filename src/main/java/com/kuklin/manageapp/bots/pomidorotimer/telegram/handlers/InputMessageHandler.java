package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers;

import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.HashMap;
import java.util.Map;

@Component
public class InputMessageHandler {
    private Map<String, MessageHandler> map = new HashMap<>();

    public void register(String botState, MessageHandler messageHandler) {
        map.put(botState, messageHandler);
    }

    public BotApiMethod processInputMessage(Message message, UserEntity userEntity) {
        MessageHandler currentMessageHandler = map.get(userEntity.getBotState().name());
        return currentMessageHandler.handle(message, userEntity);
    }
}
