package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramFacade;
import com.kuklin.manageapp.common.telegram.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface BookingUpdateHandler extends UpdateHandler {
    @Autowired
    default void registerMyself(BookingTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
