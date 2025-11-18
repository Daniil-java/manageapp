package com.kuklin.manageapp.bots.hhparserbot.telegram.handlers;

import com.kuklin.manageapp.bots.hhparserbot.telegram.HhTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface HhUpdateHandler extends UpdateHandler {
    @Autowired
    default void registerMyself(HhTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }

}
