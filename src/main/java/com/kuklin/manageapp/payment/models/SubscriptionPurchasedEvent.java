package com.kuklin.manageapp.payment.models;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;

/**
 * Пользователь купил подписку (в том числе продление подписки за звёзды). Публикует
 * UserSubscriptionService.createSubscriptionByPayment, слушатели получают его после коммита платежа.
 */
public record SubscriptionPurchasedEvent(Long appUserId, BotIdentifier botIdentifier, Long paymentId) {
}
