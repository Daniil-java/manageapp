package com.kuklin.manageapp.bots.payment.models.providers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.models.common.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StarsInvoiceProvider implements InvoiceProvider {
    @Override
    public Payment.Provider getProviderName() {
        return Payment.Provider.STARS;
    }

    @Override
    public SendInvoice buildInvoice(Long chatId, String title, String description, String payload, int amount) {
        return SendInvoice.builder()
                .chatId(chatId.toString())
                .title(title)
                .description(description)
                .payload(payload)
                .providerToken("")
                .currency(Currency.XTR.name())
                .prices(List.of(new LabeledPrice("Оплата", amount)))
                .startParameter("stars_purchase")
                .build();
    }
}
