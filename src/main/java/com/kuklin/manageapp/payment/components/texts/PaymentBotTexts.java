package com.kuklin.manageapp.payment.components.texts;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;

/**
 * Тексты модуля оплаты на языке пользователя — реализует бот, у которого есть переводы.
 * Модуль оплаты общий и переводов не знает: берёт тексты через PaymentTextResolver,
 * а для ботов без реализации — тексты по умолчанию (PaymentText, title / description тарифа из БД).
 */
public interface PaymentBotTexts {

    /** Для какого бота эта реализация. */
    BotIdentifier getBotIdentifier();

    String getText(PaymentText text, Long appUserId);

    String getPlanTitle(PricingPlan plan, Long appUserId);

    /** Описание тарифа в счёте Telegram. Переносы строк счёт не показывает. */
    String getPlanInvoiceDescription(PricingPlan plan, Long appUserId);

    /** Название способа оплаты на кнопке выбора. */
    String getProviderTitle(Payment.Provider provider, Long appUserId);

    /** Сообщение планировщика: подписка началась (статус, период). */
    String buildSubscriptionStartedText(UserSubscription subscription);
}
