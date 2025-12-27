package com.kuklin.manageapp.bots.payment.components.providerprocessors;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.services.YooKassaPaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Реализация интерфейса PaymentUrlProvider, для провайдера ЮКасса
 *
 * Отвечает за:
 * -возврат ссылки и идентификатора
 */
@Component
@RequiredArgsConstructor
public class YooKassaPaymentUrlProvider implements PaymentUrlProvider {
    private final PaymentTelegramBot paymentTelegramBot;
    private final YooKassaPaymentService yooKassaPaymentService;
    private final PaymentService paymentService;

    @Override
    public ProviderResult getProviderResult(Payment payment, PricingPlan pricingPlan, Long chatId) {
        YooKassaPaymentService.Created created = yooKassaPaymentService.create(
                payment.getAmount(),
                payment.getCurrency().name(),
                payment.getDescription(),
                payment.getTelegramId(),
                chatId,
                pricingPlan.getCodeForOrderId(),
                payment.getTelegramInvoicePayload()
        );

        return new ProviderResult(created.getConfirmationUrl(), created.getId());
    }

    @Override
    public Payment.Provider getProvider() {
        return Payment.Provider.YOOKASSA_URL;
    }
}
