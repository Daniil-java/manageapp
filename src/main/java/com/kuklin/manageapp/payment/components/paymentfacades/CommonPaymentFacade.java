package com.kuklin.manageapp.payment.components.paymentfacades;

import com.kuklin.manageapp.common.library.tgmodels.CreateInvoiceLinkWithTelegramSubscription;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.components.providerprocessors.PaymentUrlProviderFactory;
import com.kuklin.manageapp.payment.components.providerprocessors.ProviderResult;
import com.kuklin.manageapp.payment.components.providerprocessors.SendInvoiceBuilder;
import com.kuklin.manageapp.payment.entities.*;
import com.kuklin.manageapp.payment.models.PlanPaymentResult;
import com.kuklin.manageapp.payment.models.PlanPaymentResultType;
import com.kuklin.manageapp.payment.services.*;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceIllegalOperationDataException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotEnoughBalanceException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommonPaymentFacade implements PaymentFacade {
    private final PricingPlanService pricingPlanService;
    private final PaymentService paymentService;
    private final SendInvoiceBuilder sendInvoiceBuilder;
    private final UserSubscriptionService userSubscriptionService;
    private final GenerationBalanceService generationBalanceService;
    private final GenerationBalanceOperationService generationBalanceOperationService;
    private final PaymentUrlProviderFactory paymentUrlProviderFactory;

    // === Тарифы ===

    @Override
    public List<PricingPlan> getPricingPlans(BotIdentifier botIdentifier) {
        return pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(botIdentifier);
    }

    @Override
    public PricingPlan getPricingPlanById(Long id) throws PricingPlanNotFoundException {
        return pricingPlanService.getPricingPlanById(id);
    }

    // === Создание Payment и запуск платёжного флоу ===
    // В зависимости от провайдера и тарифного плана:
    // - создаёт запись Payment;
    // - для провайдеров по ссылке отдаёт редирект-URL;
    // - для Telegram-подписки отдаёт объект CreateInvoiceLinkWithTelegramSubscription;
    // - для обычных платежей отдаёт SendInvoice.
    @Override
    public PlanPaymentResult startPlanPayment(
            BotIdentifier botIdentifier,
            Long appUserId,
            Long chatId,
            Long pricingPlanId,
            Payment.Provider provider,
            String providerToken
    ) throws PricingPlanNotFoundException, PaymentNotFoundException, TelegramApiException {

        // 1. Тариф
        PricingPlan plan = pricingPlanService.getPricingPlanById(pricingPlanId);

        // 2. Платёж
        Payment payment = paymentService.createNewPayment(
                botIdentifier,
                appUserId,
                plan,
                provider
        );

        // 3. Флоу: редиректный провайдер
        if (provider.getProviderFlow().equals(Payment.PaymentFlow.PROVIDER_REDIRECT)) {
            ProviderResult result = paymentUrlProviderFactory.handle(provider, payment, plan, chatId);
            paymentService.setProviderPaymentId(payment, result.paymentId());

            return new PlanPaymentResult(
                    PlanPaymentResultType.REDIRECT_URL,
                    result.url(),
                    null,
                    null,
                    payment.getId()
            );
        }

        // 4. Флоу: Telegram-подписка
//        boolean isTelegramSubscription =
//                plan.getPayloadType().equals(PricingPlan.PricingPlanType.SUBSCRIPTION)
//                        && plan.getCurrency().equals(Currency.XTR)
//                        && Objects.equals(plan.getDurationDays(), 30);

        //Хардкодный параметр, чтобы присылать sendInvoice, а не ссылку
        boolean isTelegramSubscription = false;

        if (isTelegramSubscription) {
            CreateInvoiceLinkWithTelegramSubscription subscriptionLink =
                    TelegramBot.buildCreateInvoiceLink(
                            plan.getTitle(),
                            plan.getDescription(),
                            payment.getTelegramInvoicePayload(),
                            plan.getPriceMinor(),
                            plan.getCurrency()
                    );

            return new PlanPaymentResult(
                    PlanPaymentResultType.TELEGRAM_SUBSCRIPTION_URL,
                    null,
                    null,
                    subscriptionLink,
                    payment.getId()
            );
        }

        // 5. Флоу: обычный Telegram invoice
        SendInvoice sendInvoice = sendInvoiceBuilder.build(
                provider, payment, plan, chatId, providerToken
        );

        return new PlanPaymentResult(
                PlanPaymentResultType.TELEGRAM_INVOICE,
                null,
                sendInvoice,
                null,
                payment.getId()
        );
    }

    public Long getActivePlanIdByUserIdAndBotIdentifierOrNull(Long userId, BotIdentifier botIdentifier) {
        UserSubscription userSubscription = userSubscriptionService
                .getActiveSubscriptionOrNull(userId, botIdentifier);

        return userSubscription == null ? null : userSubscription.getPricingPlanId();
    }

    public String getActivePlanCodeByUserIdOrNull(Long userId, BotIdentifier botIdentifier) {
        UserSubscription userSubscription = userSubscriptionService
                .getActiveSubscriptionOrNull(userId, botIdentifier);

        if (userSubscription == null) return null;

        Long pricingPlanId = userSubscription.getPricingPlanId();
        try {
            return pricingPlanService.getPricingPlanById(pricingPlanId).getCodeForOrderId();
        } catch (PricingPlanNotFoundException e) {
            return null;
        }
    }

    @Override
    public void cancelPayment(Payment payment) throws PaymentException {
        paymentService.changeStatus(
                payment,
                Payment.PaymentStatus.CANCEL,
                payment.getProviderPaymentId())
        ;
    }

    // === Telegram pre-checkout ===

    @Override
    public boolean checkPreCheckoutQuery(PreCheckoutQuery query, Long appUserId) {
        return paymentService.checkPreCheckoutQuery(query, appUserId);
    }

    // === Успешная оплата ===

    /**
     * Обработка успешного Telegram-платежа.
     * Внутри paymentService:
     * - проводится валидация;
     * - обновляется статус платежа и провайдера;
     * - создаются/обновляются подписки или баланс генераций;
     * - логируются ошибки.
     * <p>
     * Возвращает Payment или null, если этот платёж уже был обработан (идемпотентность).
     */
    @Override
    @Transactional
    public Payment handleSuccessfulPayment(SuccessfulPayment successfulPayment,
                                           Long appUserId)
            throws PaymentException, PricingPlanNotFoundException {
        // Внутри paymentService:
        // - валидация
        // - изменение статуса
        // - работа с подписками / балансом генераций
        // - логирование
        return paymentService.processTelegramSuccessfulPaymentAndGetOrNull(
                successfulPayment,
                appUserId
        );
    }

    // === Подписки ===

    @Override
    public boolean hasActiveSubscription(BotIdentifier botIdentifier, Long appUserId) {
        return userSubscriptionService.hasActiveSubscription(appUserId, botIdentifier);
    }

    // === Баланс генераций ===

    @Override
    public GenerationBalance getOrCreateGenerationBalance(BotIdentifier botIdentifier,
                                                          Long appUserId) {
        return generationBalanceService.createNewBalanceIfNotExist(appUserId, botIdentifier);
    }

    @Override
    public GenerationBalanceOperation consumeGenerationsOrThrow(
            BotIdentifier botIdentifier,
            Long appUserId,
            Long requestCount,
            Long paymentId,
            String comment,
            boolean isRefund
    ) throws GenerationBalanceNotFoundException,
            GenerationBalanceIllegalOperationDataException,
            GenerationBalanceNotEnoughBalanceException {

        return generationBalanceOperationService.createNewBalanceOperationDebit(
                GenerationBalanceOperation.OperationSource.GENERATION,
                botIdentifier,
                appUserId,
                paymentId,           // paymentId — не из платежа, а из БД
                requestCount,
                comment,
                isRefund
        );
    }

    @Override
    public String getBalanceSubscriptionString(Long appUserId, BotIdentifier botIdentifier) {
        StringBuilder sb = new StringBuilder();

        List<UserSubscription> subscriptions = userSubscriptionService
                .getActiveAndScheduledSubscriptions(
                        appUserId,
                        botIdentifier
                );

        UserSubscription subscription = userSubscriptionService
                .getActiveSubscriptionOrNull(
                        appUserId,
                        botIdentifier
                );

        GenerationBalance generationBalance =
                getOrCreateGenerationBalance(
                        botIdentifier,
                        appUserId
                );

        String sub = (subscription != null && subscription.getStatus() == UserSubscription.Status.ACTIVE)
                ? "активна"
                : "не активна";

        sb
                .append("ID: ").append(appUserId).append("\n")
                .append("Баланс генераций: ").append(generationBalance.getGenerationRequests()).append(" запросов на генерацию").append("\n")
                .append("Подписка: ").append(sub).append("\n")
        ;

        for (UserSubscription subs : subscriptions) {
            sb
                    .append(subs.getStartAt())
                    .append(" - ")
                    .append(subs.getEndAt())
                    .append("\n");
        }
        return sb.toString();
    }
}
