package com.kuklin.manageapp.bots.payment.telegram.handlers.providerprocessors;

import com.kuklin.manageapp.bots.payment.configurations.TelegramPaymentBotKeyComponents;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;

/**
 * Класс для динамичной сборки SendInvoice
 *
 * Отвечает за:
 * - возврат SendInvoice
 */
@Component
@RequiredArgsConstructor
public class SendInvoiceBuilder {
    //Если оплата проходит звездами, то токен провайдера телеграммом не проверяется.
    //Токен не должен быть null, но пустой строкой быть не может,
    // из-за особенносьей версии библиотеки телаграма
    private final static String TOKEN_DUMMY = "xtr_dummy";
    private final TelegramPaymentBotKeyComponents components;

    public SendInvoice build(Payment.Provider provider,
                             Payment payment,
                             PricingPlan plan,
                             Long chatId) {
        String providerToken = TOKEN_DUMMY;

        if (!provider.equals(Payment.Provider.STARS)) {
            switch (provider) {
                case YOOKASSA ->
                    providerToken = components.getProviderToken();
                default -> {return null;} //TODO EXC
            }
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
