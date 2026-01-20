package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramFacade;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Базовый интерфейс для payment-хендлеров.
 *
 * Каждый реализационный хендлер:
 * - реализует метод handle(...) из UpdateHandler;
 * - регистрируется в нескольких Telegram-фасадах (платёжный бот, калорийный бот и т.п.)
 *   через default-методы registerMyself(...);
 * - должен вернуть уникальное имя хендлера в getHandlerListName().
 */
public interface PaymentUpdateHandler extends UpdateHandler {

    // Регистрация хендлера в фасаде платёжного бота
    @Autowired
    default void registerMyself(PaymentTelegramFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }

    // Регистрация хендлера в фасаде другого бота (пример — CalorieBot)
    @Autowired
    default void registerMyself(TelegramCalorieBotFacade messageFacade) {
        messageFacade.register(getHandlerListName(), this);
    }
}
