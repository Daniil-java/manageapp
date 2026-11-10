package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AbstractBotTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.payment.components.texts.PaymentBotTexts;
import com.kuklin.manageapp.payment.components.texts.PaymentText;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import org.springframework.stereotype.Component;

/**
 * Тексты общего модуля оплаты для пользователей калорийного бота — из словаря bot_*:
 * payment.text.<PaymentText>, payment.provider.<Payment.Provider>; тарифы — PlanTexts.
 */
@Component
public class PaymentModuleTexts extends AbstractBotTexts implements PaymentBotTexts {
    private final PlanTexts planTexts;
    private final SubscriptionTexts subscriptionTexts;

    public PaymentModuleTexts(CalorieTexts texts, PlanTexts planTexts, SubscriptionTexts subscriptionTexts) {
        super(texts);
        this.planTexts = planTexts;
        this.subscriptionTexts = subscriptionTexts;
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.CALORIE_BOT;
    }

    @Override
    public String getText(PaymentText text, Long appUserId) {
        return getText(text, languageOf(appUserId));
    }

    /** Когда язык уже известен (миниаппка передаёт ?lang=). */
    public String getText(PaymentText text, AppLanguage language) {
        return texts.get(language, BotTextKeys.buildPaymentTextKey(text));
    }

    @Override
    public String getPlanTitle(PricingPlan plan, Long appUserId) {
        return planTexts.getTitle(plan, appUserId);
    }

    @Override
    public String getPlanInvoiceDescription(PricingPlan plan, Long appUserId) {
        return planTexts.getInvoiceDescription(plan, languageOf(appUserId));
    }

    @Override
    public String getProviderTitle(Payment.Provider provider, Long appUserId) {
        return texts.get(languageOf(appUserId), BotTextKeys.buildPaymentProviderKey(provider));
    }

    /** «Подписка «Premium Месяц» началась — Premium до 13 февраля». */
    @Override
    public String buildSubscriptionStartedText(UserSubscription subscription) {
        return subscriptionTexts.buildStartedText(subscription);
    }
}
