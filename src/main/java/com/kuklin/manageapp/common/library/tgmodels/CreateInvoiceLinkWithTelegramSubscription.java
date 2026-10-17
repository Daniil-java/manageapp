package com.kuklin.manageapp.common.library.tgmodels;

import lombok.Data;
import lombok.experimental.Accessors;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;

@Data
@Accessors(chain = true)
public class CreateInvoiceLinkWithTelegramSubscription extends CreateInvoiceLink {

    private final Integer subscriptionPeriod;
}
