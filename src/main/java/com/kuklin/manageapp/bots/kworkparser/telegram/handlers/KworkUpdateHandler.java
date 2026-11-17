package com.kuklin.manageapp.bots.kworkparser.telegram.handlers;

import com.kuklin.manageapp.bots.kworkparser.telegram.KworkTelegramFacade;
import com.kuklin.manageapp.common.telegram.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface KworkUpdateHandler extends UpdateHandler {
    @Autowired
    default void registerMyself(KworkTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
