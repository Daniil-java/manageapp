package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.entities.WebhookEvent;
import com.kuklin.manageapp.bots.payment.models.yookassa.YookassaPaymentResponse;
import com.kuklin.manageapp.bots.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.bots.payment.telegram.handlers.WebhookSuccessfulPaymentUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Сервис управляющий записями платежей
 * <p>
 * Отвечает за:
 * - создание платежей
 * - изменение статуса платежей
 * - валидация приходящих платежей
 * - выдачу данных о платежах
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final GenerationBalanceOperationService generationBalanceOperationService;
    private final WebhookSuccessfulPaymentUpdateHandler webhookSuccessfulPaymentUpdateHandler;
    private final PricingPlanService pricingPlanService;
    private final UserSubscriptionService userSubscriptionService;

    //Создание новой записи о платеже
    public Payment createNewPaymentYooKassa(Long telegramId, PricingPlan pricingPlan) {
        Payment payment = paymentRepository.save(new Payment()
                .setTelegramId(telegramId)
                .setCurrency(pricingPlan.getCurrency())
                .setStatus(Payment.PaymentStatus.CREATED)
                .setProviderStatus(Payment.ProviderStatus.NEW)
                .setPricingPlanId(pricingPlan.getId())
                .setDescription(pricingPlan.getDescription())
                .setAmount(pricingPlan.getPriceMinor())
                .setProvider(Payment.Provider.YOOKASSA)
                .setStarsAmount(0));

        payment.setTelegramInvoicePayload(generateTelegramInvoicePayload(payment.getId(), pricingPlan));

        return paymentRepository.save(payment);
    }

    //Генерация уникального идентификатора платежа для телеграмма
    private String generateTelegramInvoicePayload(Long paymentId, PricingPlan pricingPlan) {
        //Название тарифного плана + ид платежа
        String invoicePayload = pricingPlan.getCodeForOrderId() + "-" + pricingPlan.getId() + "-" + paymentId;
        log.info("inoice payload: " + invoicePayload);
        return invoicePayload;
    }

    //Изменение статуса платежа на FAILED
    public Payment cancelPaymentOrNull(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) return null;

        return paymentRepository.save(payment.setStatus(Payment.PaymentStatus.FAILED));
    }

    //Валидация оплаты
    public Boolean checkPreCheckoutQuery(PreCheckoutQuery query) {
        Payment payment = getValidPaymentOrNull(
                query.getInvoicePayload(),
                query.getFrom().getId(),
                query.getCurrency(),
                query.getTotalAmount()
        );

        return payment != null;
    }

    // Валидация успешной оплаты
    public Payment processSuccessfulPaymentAndGetOrNull(SuccessfulPayment successfulPayment, Long telegramId) {
        Payment payment = getValidPaymentOrNull(
                successfulPayment.getInvoicePayload(),
                telegramId,
                successfulPayment.getCurrency(),
                successfulPayment.getTotalAmount()
        );

        if (payment == null) return null;

        return paymentRepository.save(
                payment.setStatus(Payment.PaymentStatus.SUCCESS)
        );
    }

    private Payment getValidPaymentOrNull(String invoicePayload,
                                          Long telegramId,
                                          String currency,
                                          Integer totalAmount) {

        Payment payment = paymentRepository.findByTelegramInvoicePayload(invoicePayload).orElse(null);

        log.info("For invoice payload: {} Payment not null: {}", invoicePayload, payment != null);

        if (payment == null) return null;
        if (!invoicePayload.equals(payment.getTelegramInvoicePayload())) return null;
        if (!telegramId.equals(payment.getTelegramId())) return null;
        if (!currency.equals(payment.getCurrency().name())) return null;
        if (!totalAmount.equals(payment.getAmount())) return null;

        return payment;
    }

    //Получение записи о платеже по идентификатору из провайдера
    public Payment findByProviderPaymentIdIdOrNull(String externalPaymentId, YookassaPaymentResponse response) {
        //Если существует - возврат
        Payment payment = paymentRepository.findByProviderPaymentId(externalPaymentId).orElse(null);
        if (payment != null) return payment;

        String invoicePayload = null;
        if (response.getMetadata() != null) {
            //Извлечение платжеа по идентификатору из провайдера платежа
            invoicePayload = response.getMetadata().getOrderId();
            log.info("INVOICE PAYLOAD: {}", invoicePayload);
        }

        if (invoicePayload != null && !invoicePayload.isBlank()) {

            Optional<Payment> byPayload = paymentRepository.findByTelegramInvoicePayload(invoicePayload);
            if (byPayload.isPresent()) {
                return byPayload.get();
            }
        }

        return null;
    }

    /**
     * В зависимости, от статуса в вебхуке - устанавливает статус для существуещего платежа
     * <p>
     * В зависимости от пришедшего статуса, обновляет соответствующие поля в записи о платеже
     *
     * @param payment           запись существуещего платежа
     * @param webhookEventType  вернувшийся с вебхуком - статус платежа
     * @param providerPaymentId идентификатор платежа с ЮКассы
     */
    public void setStatus(Payment payment,
                          WebhookEvent.WebhookEventType webhookEventType,
                          String providerPaymentId) {
        //TODO EXC
        switch (webhookEventType) {
            //Успешная оплата
            case PAYMENT_SUCCEEDED: {
                PricingPlan plan = pricingPlanService
                        .getPricingPlanByIdOrNull(payment.getPricingPlanId());

                try {
                    switch (plan.getPayloadType()) {
                        case SUBSCRIPTION -> {userSubscriptionService.createSubscriptionByPayment(payment);}
                        case GENERATION_REQUEST -> {generationBalanceOperationService
                                .increaseBalanceByPayment(payment);}
                    }
                } catch (Exception e) {
                    //TODO ERROR
                }

                payment
                        .setProviderStatus(Payment.ProviderStatus.SUCCEEDED)
                        .setStatus(Payment.PaymentStatus.SUCCESS)
                        .setProviderPaymentId(providerPaymentId)
                        .setPaidAt(OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime());
                payment = paymentRepository.save(payment);
                webhookSuccessfulPaymentUpdateHandler.handleYooKassaSuccess(payment);
                break;
            }
            //Отмененная оплата
            case PAYMENT_CANCELED: {
                payment.setProviderStatus(Payment.ProviderStatus.CANCELED)
                        .setStatus(Payment.PaymentStatus.FAILED)
                        .setCanceledAt(OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime());
                // по желанию: посмотреть причину в paymentApi.get("cancellation_details")
                paymentRepository.save(payment);
                break;
            }
            //Возврат средств
            case REFUND_SUCCEEDED: {
                payment.setProviderStatus(Payment.ProviderStatus.REFUND_SUCCEEDED);

                PricingPlan plan = pricingPlanService.getPricingPlanByIdOrNull(payment.getPricingPlanId());
                switch (plan.getPayloadType()) {
                    case SUBSCRIPTION -> {
                        userSubscriptionService.cancelByPayment(payment);
                    }
                    case GENERATION_REQUEST -> {
                        generationBalanceOperationService.createNewBalanceOperationDebit(
                                GenerationBalanceOperation.OperationSource.PAYMENT,
                                payment.getTelegramId(),
                                payment.getId(),
                                plan.getGenerationsCount(),
                                plan.getTitle(),
                                true
                        );
                    }
                }

                paymentRepository.save(payment);
                break;
            }
            //Неизвестный статус
            case UNKNOWN: {
                log.info("Unhandled YooKassa event!");
                break;
            }
        }
    }

    public Payment setProviderPaymentId(Payment payment, String providerOrderId) {
        return paymentRepository.save(payment.setProviderPaymentId(providerOrderId));
    }
}