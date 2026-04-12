package com.kuklin.manageapp.bots.channelposter.telegram.handlers;

import com.kuklin.manageapp.bots.channelposter.telegram.ChannelPosterTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface ChannelPosterUpdateHandler extends UpdateHandler {

    @Autowired
    default void registerMyself(ChannelPosterTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }

}
