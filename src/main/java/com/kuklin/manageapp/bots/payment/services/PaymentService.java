package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.entities.WebhookEvent;
import com.kuklin.manageapp.bots.payment.models.YooWebhook;
import com.kuklin.manageapp.bots.payment.models.common.Currency;
import com.kuklin.manageapp.bots.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.bots.payment.entities.Payment;
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
 *
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
    private final GenerationBalanceService generationBalanceService;
    private final WebhookSuccessfulPaymentUpdateHandler webhookSuccessfulPaymentUpdateHandler;

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

        Payment payment = paymentRepository.findByTelegramInvoicePayload(query.getInvoicePayload()).orElse(null);

        log.info("For invoice payload: " + query.getInvoicePayload() + " Payment not null: " + (payment != null));
        if (payment == null) return false;

        if (!query.getInvoicePayload().equals(payment.getTelegramInvoicePayload())) return false;
        log.info("TelegramInvoicePayload checked");
        if (!query.getFrom().getId().equals(payment.getTelegramId())) return false;
        log.info("TelegramId checked");
        if (!query.getCurrency().equals(payment.getCurrency().name())) return false;
        log.info("Currency checked");
        if (!query.getTotalAmount().equals(payment.getAmount())) return false;
        log.info("Amount checked");

        return true;
    }

    //Валидация успешной оплаты
    public Payment processSuccessfulPaymentAndGetOrNull(SuccessfulPayment successfulPayment, Long telegramId) {
        Payment payment = paymentRepository.findByTelegramInvoicePayload(successfulPayment.getInvoicePayload()).orElse(null);
        if (payment == null) return null;

        if (!payment.getTelegramId().equals(telegramId)) return null;
        if (!successfulPayment.getInvoicePayload().equals(payment.getTelegramInvoicePayload())) return null;
        if (!successfulPayment.getCurrency().equals(payment.getCurrency().name())) return null;
        if (!successfulPayment.getTotalAmount().equals(payment.getAmount())) return null;

        return paymentRepository.save(
                payment.setStatus(Payment.PaymentStatus.SUCCESS)
        );
    }

    //Получение записи о платеже по идентификатору из провайдера
    public Payment findByProviderPaymentIdIdOrNull(String externalPaymentId, YooWebhook hook) {
        //Если существует - возврат
        Payment payment = paymentRepository.findByProviderPaymentId(externalPaymentId).orElse(null);
        if (payment != null) return payment;

        String invoicePayload = null;
        if (hook.getObject().getMetadata() != null) {
            //Извлечение платжеа по идентификатору из провайдера платежа
            Object raw = hook.getObject().getMetadata().get("invoice_payload");
            if (raw != null) {
                invoicePayload = String.valueOf(raw);
            }
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
     *
     * В зависимости от пришедшего статуса, обновляет соответствующие поля в записи о платеже
     *
     *
     * @param payment запись существуещего платежа
     * @param webhookEventType вернувшийся с вебхуком - статус платежа
     * @param hook пришедший вебхук с ЮКассы
     * @param objectId идентификатор платежа с ЮКассы
     */
    public void setStatus(Payment payment,
                          WebhookEvent.WebhookEventType webhookEventType,
                          YooWebhook hook,
                          String objectId) {
        switch (webhookEventType) {
//            case PAYMENT_WAITING_FOR_CAPTURE: {
//                // если у тебя есть общий статус AUTHORIZED — можно выставить здесь
//                // payment.setStatus(Payment.PaymentStatus.AUTHORIZED);
//                payment.setProviderStatus(Payment.ProviderStatus.WAITING_FOR_CAPTURE)
//                        .setProviderPaymentId(objectId); // оставляю твоё поле
//                paymentRepository.save(payment);
//                break;
//            }
            //Успешная оплата
            case PAYMENT_SUCCEEDED: {
                payment
                        .setProviderStatus(Payment.ProviderStatus.SUCCEEDED)
                        .setStatus(Payment.PaymentStatus.SUCCESS)
                        .setProviderPaymentId(objectId)
                        .setPaidAt(OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime());
                payment = paymentRepository.save(payment);

                GenerationBalance balance = generationBalanceService.increaseBalanceByPayment(payment);
                webhookSuccessfulPaymentUpdateHandler.handleYooKassaSuccess(payment, balance);
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
                // если есть общий статус REFUNDED — можно выставить:
                // payment.setStatus(Payment.PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                break;
            }
            //Неизвестный статус
            case UNKNOWN: {
                log.info("Unhandled YooKassa event: {}", hook.getEvent());
                break;
            }
        }
    }

    public Payment setProviderPaymentId(Payment payment, YooKassaPaymentService.Created created) {
        return paymentRepository.save(payment.setProviderPaymentId(created.getId()));
    }
}