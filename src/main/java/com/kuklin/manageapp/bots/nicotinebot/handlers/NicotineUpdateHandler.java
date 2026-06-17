package com.kuklin.manageapp.bots.nicotinebot.handlers;

import com.kuklin.manageapp.bots.nicotinebot.NicotineTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface NicotineUpdateHandler extends UpdateHandler {

    @Autowired
    default void registerMyself(NicotineTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }

}
