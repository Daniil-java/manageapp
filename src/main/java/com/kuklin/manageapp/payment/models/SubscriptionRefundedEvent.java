package com.kuklin.manageapp.payment.models;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;

/**
 * За подписку вернули деньги — подписка по платежу отменена. Публикует UserSubscriptionService.cancelByPayment,
 * слушатели получают его после коммита.
 */
public record SubscriptionRefundedEvent(Long appUserId, BotIdentifier botIdentifier, Long paymentId) {
}
