package com.kuklin.manageapp.payment.components.providerprocessors;

import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.services.YooKassaPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Реализация PaymentUrlProvider для провайдера ЮКасса.
 *
 * Отвечает за:
 * - создание платежа через YooKassaPaymentService;
 * - возврат URL для редиректа и идентификатора платежа у провайдера.
 */
@Component
@RequiredArgsConstructor
public class YooKassaPaymentUrlProvider implements PaymentUrlProvider {
    private final YooKassaPaymentService yooKassaPaymentService;

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
