package com.kuklin.manageapp.bots.payment.telegram.handlers.telegrampay;

import com.kuklin.manageapp.bots.payment.configurations.TelegramPaymentBotKeyComponents;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.bots.payment.telegram.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundStartUpdateHandler implements PaymentUpdateHandler {
    private final TelegramPaymentBotKeyComponents components;
    private final PaymentService paymentService;
    private final PaymentTelegramBot telegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String paymentChargeId = update.getMessage().getText()
                .split(TelegramBot.DEFAULT_DELIMETER)[1];

        StarsRefundClient starsRefundClient = new StarsRefundClient(components.getKey());
        try {
            if (paymentService.refundTelegramPayment(paymentChargeId)) {
                starsRefundClient.refund(telegramUser.getTelegramId(), paymentChargeId);
            } else {
                telegramBot.sendReturnedMessage(
                        update.getMessage().getChatId(),
                        "Платеж не соответствует условиям возврата!"
                );
            }
        } catch (Exception e) {
            log.error("REFUND ERROR!", e);
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_REFUND.getCommandText();
    }
}
