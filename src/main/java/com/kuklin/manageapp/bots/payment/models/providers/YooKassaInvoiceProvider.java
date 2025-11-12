package com.kuklin.manageapp.bots.payment.models.providers;

import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;

import java.util.List;

@Component
public class YooKassaInvoiceProvider implements InvoiceProvider {
    private final String providerToken;

    public YooKassaInvoiceProvider(TelegramCaloriesBotKeyComponents components) {
        this.providerToken = components.getProviderToken();
    }

    @Override
    public Payment.Provider getProviderName() {
        return Payment.Provider.YOOKASSA;
    }

    @Override
    public SendInvoice buildInvoice(Long chatId, String title, String description, String payload, int amountKopecks) {
        return SendInvoice.builder()
                .chatId(chatId.toString())
                .title(title)
                .description(description)
                .payload(payload)
                .providerToken(providerToken)
                .currency(Payment.Currency.RUB.name())
                .prices(List.of(new LabeledPrice("Оплата", amountKopecks)))
                .startParameter("yookassa_payment")
                .build();
    }
}
