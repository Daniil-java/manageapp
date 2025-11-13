package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.WebhookEvent;
import com.kuklin.manageapp.bots.payment.models.YooWebhook;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final GenerationBalanceService generationBalanceService;
    private final WebhookSuccessfulPaymentUpdateHandler webhookSuccessfulPaymentUpdateHandler;

    public Payment createNewPaymentYooKassa(Long telegramId, Payment.PaymentPayload payload) {
        Payment payment = paymentRepository.save(new Payment()
                .setTelegramId(telegramId)
                .setCurrency(Payment.Currency.RUB)
                .setStatus(Payment.PaymentStatus.CREATED)
                .setProviderStatus(Payment.ProviderStatus.NEW)
                .setPayload(payload)
                .setDescription(payload.getDescription())
                .setAmount(payload.getPrice())
                .setProvider(Payment.Provider.YOOKASSA)
                .setStarsAmount(0));

        payment.setTelegramInvoicePayload(generateTelegramInvoicePayload(payment.getId(), payload));

        return paymentRepository.save(payment);
    }

    private String generateTelegramInvoicePayload(Long paymentId, Payment.PaymentPayload payload) {
        String invoicePayload = payload.name() + "-" + paymentId;
        log.info("inoice payload: " + invoicePayload);
        return invoicePayload;
    }

    public Payment cancelPaymentOrNull(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) return null;

        return paymentRepository.save(payment.setStatus(Payment.PaymentStatus.FAILED));
    }

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

    public Payment.PaymentPayload getPlanByTelegramInvoiceOrNull(SuccessfulPayment successfulPayment) {
        Payment payment = paymentRepository
                .findByTelegramInvoicePayloadAndStatus(
                        successfulPayment.getInvoicePayload(), Payment.PaymentStatus.SUCCESS)
                .orElse(null);

        if (payment == null) return null;
        return payment.getPayload();
    }

    public Payment findByExternalPaymentIdOrNull(String externalPaymentId, YooWebhook hook) {
        Payment payment = paymentRepository.findByProviderPaymentId(externalPaymentId).orElse(null);
        if (payment != null) return payment;

        String invoicePayload = null;
        if (hook.getObject().getMetadata() != null) {
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
            case PAYMENT_CANCELED: {
                payment.setProviderStatus(Payment.ProviderStatus.CANCELED)
                        .setStatus(Payment.PaymentStatus.FAILED)
                        .setCanceledAt(OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime());
                // по желанию: посмотреть причину в paymentApi.get("cancellation_details")
                paymentRepository.save(payment);
                break;
            }
            case REFUND_SUCCEEDED: {
                payment.setProviderStatus(Payment.ProviderStatus.REFUND_SUCCEEDED);
                // если есть общий статус REFUNDED — можно выставить:
                // payment.setStatus(Payment.PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                break;
            }
            case UNKNOWN: {
                log.info("Unhandled YooKassa event: {}", hook.getEvent());
                break;
            }
        }
    }
}