package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface AviaUpdateHandler extends UpdateHandler {
    @Autowired
    default void registerMyself(AviaTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
