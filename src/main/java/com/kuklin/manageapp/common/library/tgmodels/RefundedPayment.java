package com.kuklin.manageapp.common.library.tgmodels;

/**
 * message.refunded_payment (Bot API 7.10): Telegram вернул пользователю оплату — нашим refundStarPayment
 * или сам, без нас. В telegrambots 6.9 такого класса нет — поле достаёт {@link RefundedPaymentCapture}.
 */
public record RefundedPayment(
        String currency,
        int totalAmount,
        String invoicePayload,
        String telegramPaymentChargeId
) {
}
