package com.kuklin.manageapp.payment.handlers.telegrampay;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.RefundedPayment;
import com.kuklin.manageapp.common.library.tgmodels.RefundedPaymentCapture;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * refunded_payment — звёзды за платёж вернулись пользователю. Так приходит и наш /refund (платёж уже
 * REFUNDED — ничего не меняется), и возврат без нас: Telegram сам вернул звёзды. Тогда отменяем покупку.
 * Сообщение о возврате пользователь видит в чате от Telegram — сами не пишем.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundedPaymentUpdateHandler implements PaymentUpdateHandler {
    private final PaymentService paymentService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        RefundedPayment refunded = RefundedPaymentCapture.findOrNull(update);
        if (refunded == null) return;
        log.info("refunded_payment: user {}, {} {}, charge {}", telegramUser.getAppUserId(),
                refunded.totalAmount(), refunded.currency(), refunded.telegramPaymentChargeId());
        paymentService.cancelRefundedTelegramPayment(refunded.telegramPaymentChargeId(), telegramUser.getAppUserId());
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_REFUNDED.getCommandText();
    }
}
