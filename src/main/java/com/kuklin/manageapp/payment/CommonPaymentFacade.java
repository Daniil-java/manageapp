package com.kuklin.manageapp.payment;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.CreateInvoiceLinkWithTelegramSubscription;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.components.providerprocessors.PaymentUrlProviderFactory;
import com.kuklin.manageapp.payment.components.providerprocessors.ProviderResult;
import com.kuklin.manageapp.payment.components.providerprocessors.SendInvoiceBuilder;
import com.kuklin.manageapp.payment.entities.*;
import com.kuklin.manageapp.payment.models.PlanPaymentResult;
import com.kuklin.manageapp.payment.models.PlanPaymentResultType;
import com.kuklin.manageapp.payment.models.common.Currency;
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
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Objects;

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
        return pricingPlanService.getAllPlansByBotIdentifier(botIdentifier);
    }

    @Override
    public PricingPlan getPricingPlanById(Long id) throws PricingPlanNotFoundException {
        return pricingPlanService.getPricingPlanById(id);
    }

    // === Создание Payment + SendInvoice ===
    @Override
    public PlanPaymentResult startPlanPayment(
            BotIdentifier botIdentifier,
            Long telegramId,
            Long chatId,
            Long pricingPlanId,
            Payment.Provider provider
    ) throws PricingPlanNotFoundException, PaymentNotFoundException, TelegramApiException {

        // 1. Тариф
        PricingPlan plan = pricingPlanService.getPricingPlanById(pricingPlanId);

        // 2. Платёж
        Payment payment = paymentService.createNewPayment(
                botIdentifier,
                telegramId,
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
        boolean isTelegramSubscription =
                plan.getPayloadType().equals(PricingPlan.PricingPlanType.SUBSCRIPTION)
                        && plan.getCurrency().equals(Currency.XTR)
                        && Objects.equals(plan.getDurationDays(), 30);

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
        SendInvoice sendInvoice = sendInvoiceBuilder.build(provider, payment, plan, chatId);

        return new PlanPaymentResult(
                PlanPaymentResultType.TELEGRAM_INVOICE,
                null,
                sendInvoice,
                null,
                payment.getId()
        );
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
    public boolean checkPreCheckoutQuery(PreCheckoutQuery query) {
        return paymentService.checkPreCheckoutQuery(query);
    }

    // === Успешная оплата ===

    @Override
    public Payment handleSuccessfulPayment(SuccessfulPayment successfulPayment,
                                           Long telegramId)
            throws PaymentException, PricingPlanNotFoundException {
        // Внутри paymentService:
        // - валидация
        // - изменение статуса
        // - работа с подписками / балансом генераций
        // - логирование
        return paymentService.processTelegramSuccessfulPaymentAndGetOrNull(
                successfulPayment,
                telegramId
        );
    }

    // === Подписки ===

    @Override
    public boolean hasActiveSubscription(BotIdentifier botIdentifier, Long telegramId) {
        return userSubscriptionService.hasActiveSubscription(telegramId, botIdentifier);
    }

    // === Баланс генераций ===

    @Override
    public GenerationBalance getOrCreateGenerationBalance(BotIdentifier botIdentifier,
                                                          Long telegramId) {
        return generationBalanceService.createNewBalanceIfNotExist(telegramId, botIdentifier);
    }

    @Override
    public GenerationBalanceOperation consumeGenerationsOrThrow(
            BotIdentifier botIdentifier,
            Long telegramId,
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
                telegramId,
                paymentId,           // paymentId — не из платежа, а из БД
                requestCount,
                comment,
                isRefund
        );
    }

    @Override
    public String getBalanceSubscriptionString(
            TelegramUser telegramUser,
            BotIdentifier botIdentifier
    ) {
        StringBuilder sb = new StringBuilder();

        List<UserSubscription> subscriptions = userSubscriptionService
                .getActiveAndScheduledSubscriptions(
                        telegramUser.getTelegramId(),
                        botIdentifier
                );

        UserSubscription subscription = userSubscriptionService
                .getActiveSubscriptionOrNull(
                        telegramUser.getTelegramId(),
                        botIdentifier
                );

        GenerationBalance generationBalance =
                getOrCreateGenerationBalance(
                        botIdentifier,
                        telegramUser.getTelegramId()
                );

        String sub = (subscription != null && subscription.getStatus() == UserSubscription.Status.ACTIVE)
                ? "активна"
                : "не активна";

        sb
                .append("ID: ").append(telegramUser.getTelegramId()).append("\n")
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
