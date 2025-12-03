package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface PaymentUpdateHandler extends UpdateHandler {

    @Autowired
    default void registerMyself(PaymentTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }

    @Autowired
    default void registerMyself(TelegramCalorieBotFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
