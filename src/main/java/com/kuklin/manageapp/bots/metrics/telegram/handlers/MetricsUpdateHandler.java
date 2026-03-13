package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import com.kuklin.manageapp.bots.metrics.telegram.MetricsTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

public interface MetricsUpdateHandler extends UpdateHandler {
    @Autowired
    default void registerMyself(MetricsTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
