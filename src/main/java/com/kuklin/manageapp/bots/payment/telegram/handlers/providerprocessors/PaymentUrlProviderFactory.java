package com.kuklin.manageapp.bots.payment.telegram.handlers.providerprocessors;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фабрика, для провайдеров, работающих по ссылке
 *
 * Отвечает за:
 * - возвращение ссылки из нужного провайдера
 */
@Component
@RequiredArgsConstructor
public class PaymentUrlProviderFactory {

    private final Map<Payment.Provider, PaymentUrlProvider> providers = new ConcurrentHashMap<>();

    public void registerProvider(PaymentUrlProvider provider) {
        providers.put(provider.getProvider(), provider);
    }

    public ProviderResult handle(Payment.Provider provider, Payment payment, PricingPlan pricingPlan, Long chatId) {
        return providers.get(provider).getProviderResult(payment, pricingPlan, chatId);
    }
}
