package com.kuklin.manageapp.bots.payment.models.providers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;

//Интерфейс для провайдеров оплаты
public interface InvoiceProvider {
    Payment.Provider getProviderName(); // Например "STARS", "YOOKASSA"
    SendInvoice buildInvoice(Long chatId, String title, String description, String payload, int amount);
    @Autowired
    default void registerMyself(InvoiceFactory invoiceFactory) {
        invoiceFactory.registerProvider(this);
    }
}

