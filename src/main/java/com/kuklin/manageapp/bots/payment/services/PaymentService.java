package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.models.common.Currency;
import com.kuklin.manageapp.bots.payment.models.yookassa.YookassaPaymentResponse;
import com.kuklin.manageapp.bots.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceIllegalOperationDataException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotEnoughBalanceException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.bots.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.bots.payment.services.exceptions.payment.*;
import com.kuklin.manageapp.bots.payment.services.exceptions.subscription.SubscriptionInvalidDataException;
import com.kuklin.manageapp.bots.payment.services.exceptions.subscription.SubscriptionNotFound;
import com.kuklin.manageapp.bots.payment.services.exceptions.subscription.SubscriptionNotSubscribeException;
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
 * - обработка случаев успешной оплаты и возврата
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
    private final PaymentFailedLogService paymentFailedLogService;

    //Создание новой записи о платеже
    public Payment createNewPayment(Long telegramId, PricingPlan pricingPlan, Payment.Provider provider) {

        Integer starsAmount = pricingPlan.getCurrency().equals(Currency.XTR)
                ? pricingPlan.getPriceMinor() :
                0;
        Integer amount = pricingPlan.getCurrency().equals(Currency.XTR)
                ? 0 :
                pricingPlan.getPriceMinor();

        Payment payment = paymentRepository.save(new Payment()
                .setTelegramId(telegramId)
                .setCurrency(pricingPlan.getCurrency())
                .setStatus(Payment.PaymentStatus.CREATED)
                .setProviderStatus(Payment.ProviderStatus.NEW)
                .setPricingPlanId(pricingPlan.getId())
                .setDescription(pricingPlan.getDescription())
                .setAmount(amount)
                .setProvider(provider)
                .setStarsAmount(starsAmount));

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
    public Payment cancelPayment(Long paymentId) throws PaymentNotFoundException {
        Payment payment = paymentRepository.findById(paymentId)
                .orElse(null);
        if (payment == null) {
            log.error(PaymentNotFoundException.DEF_MSG);
            throw new PaymentNotFoundException();
        }

        return paymentRepository.save(payment.setStatus(Payment.PaymentStatus.FAILED));
    }

    //Валидация оплаты
    public Boolean checkPreCheckoutQuery(PreCheckoutQuery query) {
        Payment payment = null;
        try {
            payment = getValidPayment(
                    query.getInvoicePayload(),
                    query.getFrom().getId(),
                    query.getCurrency(),
                    query.getTotalAmount()
            );
            return true;
        } catch (PaymentValidationDataException e) {
            return false;
        }
    }

    // Валидация успешной оплаты
    public Payment processTelegramSuccessfulPaymentAndGet(SuccessfulPayment successfulPayment, Long telegramId) throws PaymentValidationDataException, GenerationBalanceIllegalOperationDataException, GenerationBalanceNotEnoughBalanceException, PricingPlanNotFoundException, GenerationBalanceNotFoundException {
        Payment payment = getValidPayment(
                successfulPayment.getInvoicePayload(),
                telegramId,
                successfulPayment.getCurrency(),
                successfulPayment.getTotalAmount()
        );

        payment = paymentRepository.save(
                payment.setStatus(Payment.PaymentStatus.SUCCESS)
        );

        generationBalanceOperationService.increaseBalanceByPayment(payment);

        return payment;
    }

    private Payment getValidPayment(String invoicePayload,
                                    Long telegramId,
                                    String currency,
                                    Integer totalAmount) throws PaymentValidationDataException {

        Payment payment = paymentRepository.findByTelegramInvoicePayload(invoicePayload).orElse(null);

        log.info("For invoice payload: {} Payment not null: {}", invoicePayload, payment != null);

        if (payment == null
                || !invoicePayload.equals(payment.getTelegramInvoicePayload())
                || !telegramId.equals(payment.getTelegramId())
                || !currency.equals(payment.getCurrency().name())
                || !totalAmount.equals(payment.getAmount())) {
            log.error(PaymentValidationDataException.DEF_MSG);
            throw new PaymentValidationDataException();
        }

        return payment;
    }

    //Получение записи о платеже по идентификатору из провайдера
    public Payment findByProviderPaymentId(
            String externalPaymentId, YookassaPaymentResponse response)
            throws PaymentNotFoundException, PaymentNotFoundByProviderPaymentIdException {
        //Если существует - возврат
        Payment payment = paymentRepository.findByProviderPaymentId(externalPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException());
        if (payment != null) {
            return payment;
        }

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

        throw new PaymentNotFoundByProviderPaymentIdException();
    }

    /**
     * В зависимости, от статуса - устанавливает статус для существуещего платежа
     * <p>
     * В зависимости от пришедшего статуса, обновляет соответствующие поля в записи о платеже
     *
     * @param payment           запись существуещего платежа
     * @param newStatus         новый статус платежа
     * @param providerPaymentId идентификатор платежа с ЮКассы
     */
    public void changeStatus(Payment payment,
                             Payment.PaymentStatus newStatus,
                             String providerPaymentId) throws PaymentException {
        try {
            switch (newStatus) {
                //Успешный платеж
                case SUCCESS -> handleSuccess(payment, providerPaymentId);
                //Возврат средств
                case REFUNDED -> handleRefunded(payment);
                //Отмена платежа
                case CANCEL -> handleCanceled(payment);
            }
        } catch (Exception e) {
            handleFailed(payment, e.getMessage());
            throw new PaymentException(e.getMessage());
        }

    }

    //Обработка успешного платежа
    private void handleSuccess(Payment payment, String providerPaymentId)
            throws PricingPlanNotFoundException,
            SubscriptionNotSubscribeException,
            SubscriptionInvalidDataException,
            GenerationBalanceNotFoundException,
            GenerationBalanceIllegalOperationDataException,
            GenerationBalanceNotEnoughBalanceException {

        //Получение тарифнового плана
        PricingPlan plan = pricingPlanService
                .getPricingPlanById(payment.getPricingPlanId());

        //Предуссматривается 2 типа
        //- Подписка - по факту доступ на N-дней
        //- Количество генераций
        switch (plan.getPayloadType()) {
            case SUBSCRIPTION ->
                //Создание подписки
                    userSubscriptionService.createSubscriptionByPayment(payment);
            case GENERATION_REQUEST ->
                //Увеличение баланса генераций пользователя
                    generationBalanceOperationService.increaseBalanceByPayment(payment);
        }

        payment
                .setProviderStatus(Payment.ProviderStatus.SUCCEEDED)
                .setStatus(Payment.PaymentStatus.SUCCESS)
                .setProviderPaymentId(providerPaymentId)
                .setPaidAt(OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime());
        payment = paymentRepository.save(payment);
        //Оповещение пользователя в телеграмме
        webhookSuccessfulPaymentUpdateHandler.handleYooKassaSuccess(payment);
    }

    //Обработка случая возврата средств
    private void handleRefunded(Payment payment)
            throws PricingPlanNotFoundException,
            SubscriptionNotFound,
            GenerationBalanceNotFoundException,
            GenerationBalanceIllegalOperationDataException,
            GenerationBalanceNotEnoughBalanceException {
        payment.setProviderStatus(Payment.ProviderStatus.REFUND_SUCCEEDED);

        //Получение тарифного плана
        PricingPlan plan = pricingPlanService.getPricingPlanById(payment.getPricingPlanId());
        //Предуссматривается 2 типа
        //- Подписка - по факту доступ на N-дней
        //- Количество генераций
        switch (plan.getPayloadType()) {
            case SUBSCRIPTION -> {
                //Отмена подписки
                userSubscriptionService.cancelByPayment(payment);
            }
            case GENERATION_REQUEST -> {
                //Списание оплаченных генераций
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
    }

    //Отмена платежа (не возврат)
    private void handleCanceled(Payment payment) {
        payment.setProviderStatus(Payment.ProviderStatus.CANCELED)
                .setStatus(Payment.PaymentStatus.FAILED)
                .setCanceledAt(OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime());
        // по желанию: посмотреть причину в paymentApi.get("cancellation_details")
        paymentRepository.save(payment);

    }

    //Запись лога об ошибке в БД
    private void handleFailed(Payment payment, String error) {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment = paymentRepository.save(payment);

        paymentFailedLogService.createLog(payment.getId(), error);
    }

    public Payment setProviderPaymentId(Payment payment, String providerOrderId) {
        return paymentRepository.save(payment.setProviderPaymentId(providerOrderId));
    }
}