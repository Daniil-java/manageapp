package com.kuklin.manageapp.bots.payment.components.providerprocessors;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Интерфейс, для провайдеров по ссылке
 *
 * Отвечает за:
 * - регистрацию реалзиаций интерфейса в фабрике
 * - метод для получения ссылки и идентификатора платежа
 */
public interface PaymentUrlProvider {

    ProviderResult getProviderResult(Payment payment,
                                  PricingPlan pricingPlan,
                                  Long chatId);

    Payment.Provider getProvider();

    @Autowired
    default void registerMyself(PaymentUrlProviderFactory invoiceFactory) {
        invoiceFactory.registerProvider(this);
    }
}

