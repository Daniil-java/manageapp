package com.kuklin.manageapp.payment.models;

import com.kuklin.manageapp.common.library.tgmodels.CreateInvoiceLinkWithTelegramSubscription;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;

public record PlanPaymentResult(
        PlanPaymentResultType type,
        String url,        // для REDIRECT_URL / TELEGRAM_SUBSCRIPTION_URL
        SendInvoice invoice,
        CreateInvoiceLinkWithTelegramSubscription createInvoiceLink, // для TELEGRAM_SUBSCRIPTION_URL
        Long paymentId
) {
}
