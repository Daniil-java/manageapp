package com.kuklin.manageapp.payment;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.GenerationBalance;
import com.kuklin.manageapp.payment.entities.GenerationBalanceOperation;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.PlanPaymentResult;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceIllegalOperationDataException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotEnoughBalanceException;
import com.kuklin.manageapp.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentNotFoundException;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

public interface PaymentFacade {
    // === Тарифы ===

    /**
     * Вернуть все тарифы для конкретного бота.
     */
    List<PricingPlan> getPricingPlans(BotIdentifier botIdentifier);
    PricingPlan getPricingPlanById(Long id) throws PricingPlanNotFoundException;

    // === Создание платежа и инвойса ===

    PlanPaymentResult startPlanPayment(
            BotIdentifier botIdentifier,
            Long telegramId,
            Long chatId,
            Long pricingPlanId,
            Payment.Provider provider
    ) throws PricingPlanNotFoundException, TelegramApiException, PaymentNotFoundException;

    void cancelPayment(Payment payment) throws PaymentException;

    // === Telegram-платежи: pre-checkout и успешная оплата ===

    /**
     * Проверка данных pre-checkout.
     * true — всё ок, можно подтверждать платёж; false — отклонять.
     */
    boolean checkPreCheckoutQuery(PreCheckoutQuery query);

    /**
     * Обработка успешного Telegram-платежа.
     * Возвращает Payment или null, если этот платёж уже обрабатывали (idempotency).
     */
    Payment handleSuccessfulPayment(SuccessfulPayment successfulPayment,
                                    Long telegramId)
            throws PaymentException, PricingPlanNotFoundException;

    // === Подписки ===

    /**
     * Есть ли у пользователя активная подписка для конкретного бота.
     */
    boolean hasActiveSubscription(BotIdentifier botIdentifier, Long telegramId);
    // === Баланс генераций ===

    /**
     * Получить или создать (если нет) баланс генераций для пользователя.
     */
    GenerationBalance getOrCreateGenerationBalance(BotIdentifier botIdentifier, Long telegramId);

    /**
     * Списать N генераций.
     * Бросает исключения, если баланс недостаточный или данные кривые.
     */
    GenerationBalanceOperation consumeGenerationsOrThrow(
            BotIdentifier botIdentifier,
            Long telegramId,
            Long requestCount,
            Long paymentId,
            String comment,
            boolean isRefund
    ) throws GenerationBalanceNotFoundException,
            GenerationBalanceIllegalOperationDataException,
            GenerationBalanceNotEnoughBalanceException;

    String getBalanceSubscriptionString(TelegramUser telegramUser, BotIdentifier botIdentifier
    );
}

