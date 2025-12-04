package com.kuklin.manageapp.payment.components.providerprocessors;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;

/**
 * Класс для динамичной сборки SendInvoice
 * <p>
 * Отвечает за:
 * - возврат SendInvoice
 */
@Component
@RequiredArgsConstructor
public class SendInvoiceBuilder {
    // Если оплата проходит звёздами (XTR), Telegram не проверяет providerToken.
    // Библиотека не допускает null/пустую строку, поэтому кладём фиктивный токен.
    private final static String TOKEN_DUMMY = "xtr_dummy";

    public SendInvoice build(Payment.Provider provider,
                             Payment payment,
                             PricingPlan plan,
                             Long chatId,
                             String providerToken) {
        if (providerToken == null) providerToken = TOKEN_DUMMY;

        if (!provider.equals(Payment.Provider.STARS) && providerToken.equals(TOKEN_DUMMY)) {
            return null;
        }
        return TelegramBot.buildInvoiceOrNull(
                chatId,
                plan.getTitle(),
                plan.getDescription(),
                payment.getTelegramInvoicePayload(),
                providerToken,
                payment.getAmount(),
                plan.getCurrency(),
                provider,
                "Оплата"
        );
    }
}
