package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.models.yookassa.YookassaPaymentResponse;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceIllegalOperationDataException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotEnoughBalanceException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentNotFoundByProviderPaymentIdException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentValidationDataException;
import com.kuklin.manageapp.payment.services.exceptions.subscription.SubscriptionInvalidDataException;
import com.kuklin.manageapp.payment.services.exceptions.subscription.SubscriptionNotFound;
import com.kuklin.manageapp.payment.services.exceptions.subscription.SubscriptionNotSubscribeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

import java.time.Instant;
import java.util.Objects;
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
@Transactional(readOnly = true)
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final GenerationBalanceOperationService generationBalanceOperationService;
    private final PricingPlanService pricingPlanService;
    private final UserSubscriptionService userSubscriptionService;
    private final PaymentFailedLogService paymentFailedLogService;

    //Создание новой записи о платеже
    @Transactional
    public Payment createNewPayment(
            BotIdentifier botIdentifier,
            Long appUserId, PricingPlan pricingPlan,
            Payment.Provider provider
    ) {

        Integer starsAmount = pricingPlan.getCurrency().equals(Currency.XTR)
                ? pricingPlan.getPriceMinor() :
                0;
        Integer amount = pricingPlan.getCurrency().equals(Currency.XTR)
                ? 0 :
                pricingPlan.getPriceMinor();

        Payment payment = paymentRepository.save(
                new Payment()
                        .setAppUserId(appUserId)
                        .setCurrency(pricingPlan.getCurrency())
                        .setStatus(Payment.PaymentStatus.CREATED)
                        .setProviderStatus(Payment.ProviderStatus.NEW)
                        .setPricingPlanId(pricingPlan.getId())
                        .setDescription(pricingPlan.getDescription())
                        .setAmount(amount)
                        .setProvider(provider)
                        .setStarsAmount(starsAmount)
                        .setBotIdentifier(botIdentifier)
        );

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

    //Валидация оплаты
    public Boolean checkPreCheckoutQuery(PreCheckoutQuery query, Long appUserId) {
        try {
            Payment payment = getValidPayment(
                    query.getInvoicePayload(),
                    appUserId,
                    query.getCurrency(),
                    query.getTotalAmount()
            );
            // Ссылки на оплату у Telegram многоразовые: без этой проверки уже оплаченный (SUCCESS),
            // отменённый или несостоявшийся счёт можно оплатить ещё раз. pre_checkout — до списания,
            // отказ здесь безопасен. Автопродление подписки Telegram приходит без pre_checkout — его не задевает.
            if (payment.getStatus() != Payment.PaymentStatus.CREATED) {
                log.warn("Pre-checkout rejected: payment {} has status {}, payload={}",
                        payment.getId(), payment.getStatus(), query.getInvoicePayload());
                return false;
            }
            return true;
        } catch (PaymentValidationDataException e) {
            return false;
        } catch (PricingPlanNotFoundException e) {
            return false;
        }
    }

    // Обработка успешной оплаты
    @Transactional
    public Payment processTelegramSuccessfulPaymentAndGetOrNull(
            SuccessfulPayment successfulPayment, Long appUserId
    ) throws PaymentException, PricingPlanNotFoundException {

        // Проверяем именно тот токен, который мы будем сохранять
        // Определяем, какой ID является главным для этого типа валюты
        String checkToken = successfulPayment.getCurrency().equals(Currency.XTR.name()) ?
                successfulPayment.getTelegramPaymentChargeId() :
                successfulPayment.getProviderPaymentChargeId();

        // Счёт берём под блокировкой: два successful_payment по одному счёту (оплата с двух устройств,
        // повтор апдейта) иначе параллельно видят CREATED и оба выдают покупку в одну запись.
        Payment invoice = validatePayment(
                paymentRepository.findByTelegramInvoicePayloadForUpdate(successfulPayment.getInvoicePayload()),
                successfulPayment.getInvoicePayload(),
                appUserId,
                successfulPayment.getCurrency(),
                successfulPayment.getTotalAmount()
        );

        // После блокировки: параллельный обработчик того же списания уже закоммитил его
        Optional<Payment> byProviderId = paymentRepository.findByProviderPaymentId(checkToken);
        if (byProviderId.isPresent()) {
            log.info("Payment with ID {} has already been processed. Skipping.", checkToken);
            return byProviderId.get();
        }

        Payment payment = invoice;
        if (invoice.getStatus() != Payment.PaymentStatus.CREATED) {
            PricingPlan plan = pricingPlanService.getPricingPlanById(invoice.getPricingPlanId());
            payment = createExtraChargePayment(invoice, plan);
            log.warn("Extra charge {} for already processed payment {} (status {}), recorded as payment {}",
                    checkToken, invoice.getId(), invoice.getStatus(), payment.getId());
        }

        // Используем тот же самый токен для смены статуса
        changeStatus(payment, Payment.PaymentStatus.SUCCESS, checkToken);

        return payment;
    }

    /**
     * Ещё одно списание по уже обработанному счёту — своя запись Payment со своим charge_id,
     * чтобы каждое списание было в учёте и его можно было вернуть (refundTelegramPayment ищет по charge_id).
     * Так приходят: вторая оплата одной ссылки (Telegram-ссылки многоразовые, два pre_checkout проходят,
     * пока счёт ещё CREATED) и продление подписки Telegram с автосписанием (сейчас выключены, et-81).
     * Пользователь заплатил ещё раз — покупка выдаётся ещё раз.
     */
    private Payment createExtraChargePayment(Payment invoice, PricingPlan plan) {
        Payment extra = paymentRepository.save(
                new Payment()
                        .setProvider(invoice.getProvider())
                        .setPricingPlanId(invoice.getPricingPlanId())
                        .setDescription(invoice.getDescription())
                        .setCurrency(invoice.getCurrency())
                        .setAmount(invoice.getAmount())
                        .setStarsAmount(invoice.getStarsAmount())
                        .setStatus(Payment.PaymentStatus.CREATED)
                        .setAppUserId(invoice.getAppUserId())
                        .setProviderStatus(Payment.ProviderStatus.SUCCEEDED)
                        .setBotIdentifier(invoice.getBotIdentifier())
        );

        // payload уникален — свой, на основе нового ID
        extra.setTelegramInvoicePayload(generateTelegramInvoicePayload(extra.getId(), plan));
        return paymentRepository.save(extra);
    }

    private Payment getValidPayment(String invoicePayload,
                                    Long appUserId,
                                    String currency,
                                    Integer totalAmount) throws PaymentValidationDataException, PricingPlanNotFoundException {
        return validatePayment(paymentRepository.findByTelegramInvoicePayload(invoicePayload),
                invoicePayload, appUserId, currency, totalAmount);
    }

    private Payment validatePayment(Optional<Payment> found,
                                    String invoicePayload,
                                    Long appUserId,
                                    String currency,
                                    Integer totalAmount) throws PaymentValidationDataException, PricingPlanNotFoundException {

        Payment payment = found
                .orElseThrow(() -> {
                    log.error("{}: payment not found by invoicePayload={}",
                            PaymentValidationDataException.DEF_MSG, invoicePayload);
                    return new PaymentValidationDataException();
                });

        // Получаем актуальный тариф из БД прямо сейчас
        PricingPlan actualPlan = pricingPlanService.getPricingPlanById(payment.getPricingPlanId());

        // Определяем, какая цена сейчас в официальном прайс-листе
        int actualPriceRightNow = actualPlan.getCurrency().equals(Currency.XTR)
                ? actualPlan.getPriceMinor()
                : actualPlan.getPriceMinor();

        int expectedAmount = payment.getCurrency() == Currency.XTR
                ? payment.getStarsAmount()
                : payment.getAmount();

        boolean valid =
                Objects.equals(invoicePayload, payment.getTelegramInvoicePayload()) &&
                        Objects.equals(appUserId, payment.getAppUserId()) &&
                        Objects.equals(currency, payment.getCurrency().name()) &&
                        Objects.equals(totalAmount, expectedAmount) &&
                        Objects.equals(totalAmount, actualPriceRightNow);

        if (!valid) {
            log.error(
                    "{}: validation failed. " +
                            "invoicePayload={}, appUserId={}, currency={}, totalAmount={}, " +
                            "dbInvoicePayload={}, dbAppUserId={}, dbCurrency={}, dbAmount={}, dbStarsAmount={}",
                    PaymentValidationDataException.DEF_MSG,
                    invoicePayload, appUserId, currency, totalAmount,
                    payment.getTelegramInvoicePayload(),
                    payment.getAppUserId(),
                    payment.getCurrency().name(),
                    payment.getAmount(),
                    payment.getStarsAmount()
            );
            throw new PaymentValidationDataException();
        }

        return payment;
    }

    //Получение записи о платеже по идентификатору из провайдера
    public Payment findByProviderPaymentId(
            String externalPaymentId, YookassaPaymentResponse response)
            throws PaymentNotFoundByProviderPaymentIdException {
        //Пытаемся найти по providerPaymentId
        Payment payment = paymentRepository.findByProviderPaymentId(externalPaymentId)
                .orElse(null);
        if (payment != null) {
            return payment;
        }

        //Пытаемся достать invoicePayload из metadata
        String invoicePayload = null;
        if (response.getMetadata() != null) {
            //Извлечение платжеа по идентификатору из провайдера платежа
            invoicePayload = response.getMetadata().getOrderId();
            log.info("INVOICE PAYLOAD: {}", invoicePayload);
        }

        //Если payload есть – ищем по нему
        if (invoicePayload != null && !invoicePayload.isBlank()) {

            Optional<Payment> byPayload = paymentRepository.findByTelegramInvoicePayload(invoicePayload);
            if (byPayload.isPresent()) {
                return byPayload.get();
            }
        }

        //Не нашли ни по providerPaymentId, ни по payload
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
    @Transactional
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
                .setPaidAt(Instant.now());
        payment = paymentRepository.save(payment);
    }

    //Обработка случая возврата средств
    private void handleRefunded(Payment payment)
            throws PricingPlanNotFoundException,
            SubscriptionNotFound,
            GenerationBalanceNotFoundException,
            GenerationBalanceIllegalOperationDataException,
            GenerationBalanceNotEnoughBalanceException {
        payment = paymentRepository.save(
                payment.setProviderStatus(Payment.ProviderStatus.REFUND_SUCCEEDED));

        //Получение тарифного плана
        PricingPlan plan = pricingPlanService.getPricingPlanById(payment.getPricingPlanId());
        //Предуссматривается 2 типа
        //- Подписка - по факту доступ на N-дней
        //- Количество генераций
        switch (plan.getPayloadType()) {
            case SUBSCRIPTION ->
                //Отмена подписки
                    userSubscriptionService.cancelByPayment(payment);

            case GENERATION_REQUEST ->
                //Списание оплаченных генераций
                    generationBalanceOperationService.createNewBalanceOperationDebit(
                            GenerationBalanceOperation.OperationSource.PAYMENT,
                            plan.getBotIdentifier(),
                            payment.getAppUserId(),
                            payment.getId(),
                            plan.getGenerationsCount(),
                            plan.getTitle(),
                            true
                    );

        }
    }

    //Отмена платежа (не возврат)
    private void handleCanceled(Payment payment) {
        payment.setProviderStatus(Payment.ProviderStatus.CANCELED)
                .setStatus(Payment.PaymentStatus.CANCEL)
                .setCanceledAt(Instant.now());
        // по желанию: посмотреть причину в paymentApi.get("cancellation_details")
        paymentRepository.save(payment);

    }

    /**
     * Счёт так и не выставлен (например, Telegram не отдал ссылку на оплату) — помечаем FAILED,
     * чтобы запись не висела в CREATED, будто ждёт оплаты.
     */
    @Transactional
    public void markFailed(Payment payment, String error) {
        handleFailed(payment, error);
    }

    //Запись лога об ошибке в БД
    private void handleFailed(Payment payment, String error) {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment = paymentRepository.save(payment);

        paymentFailedLogService.createLog(payment.getId(), error);
    }

    @Transactional
    public Payment setProviderPaymentId(Payment payment, String providerOrderId) {
        return paymentRepository.save(payment.setProviderPaymentId(providerOrderId));
    }

    @Transactional
    public boolean refundTelegramPayment(String paymentChargeId) {
        Payment payment = paymentRepository.findByProviderPaymentId(paymentChargeId).orElse(null);
        if (payment == null) return false;
        if (!payment.getCurrency().equals(Currency.XTR)) return false;

        if (payment.getStatus().equals(Payment.PaymentStatus.SUCCESS)) {
            try {
                changeStatus(payment, Payment.PaymentStatus.REFUNDED, paymentChargeId);
                return true;
            } catch (PaymentException e) {
                log.error("Refund error!", e);
                return false;
            }
        }
        return false;
    }
}