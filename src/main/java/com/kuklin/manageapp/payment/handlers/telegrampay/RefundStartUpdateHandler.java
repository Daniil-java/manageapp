package com.kuklin.manageapp.payment.handlers.telegrampay;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.payment.models.StarsRefundClient;
import com.kuklin.manageapp.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundStartUpdateHandler implements PaymentUpdateHandler {
    private final PaymentService paymentService;
    private final TelegramBotRegistry telegramBotRegistry;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String paymentChargeId = update.getMessage().getText()
                .split(TelegramBot.DEFAULT_DELIMETER)[1];
        TelegramBot telegramBot = telegramBotRegistry.get(telegramUser.getBotIdentifier());

        try {
            if (paymentService.refundTelegramPayment(paymentChargeId)) {
                StarsRefundClient starsRefundClient = new StarsRefundClient(telegramBot.getToken());
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
