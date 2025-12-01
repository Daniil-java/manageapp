package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface AssistantUpdateHandler extends UpdateHandler {

    @Autowired
    default void registerMyself(AssistantTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
