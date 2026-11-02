package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.common.telegram.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface CalorieBotUpdateHandler extends UpdateHandler {

    @Autowired
    default void registerMyself(TelegramCalorieBotFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
