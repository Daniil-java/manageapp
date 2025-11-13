package com.kuklin.manageapp.bots.payment.models.providers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class InvoiceFactory {
    private final Map<Payment.Provider, InvoiceProvider> providers = new ConcurrentHashMap<>();

    public void registerProvider(InvoiceProvider provider) {
        providers.put(provider.getProviderName(), provider);
    }

    public SendInvoice build(Payment.Provider payProvider,
                             Long chatId,
                             String title,
                             String description,
                             String payload,
                             int amount) {

        InvoiceProvider provider = providers.get(payProvider);
        if (provider == null) {
            log.error("Unsupported provider: " + payProvider);
            throw new IllegalArgumentException("Unsupported provider: " + payProvider);
        }
        return provider.buildInvoice(chatId, title, description, payload, amount);
    }
}
