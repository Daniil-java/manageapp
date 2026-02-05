package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.services.GenerationBalanceService;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

@RequiredArgsConstructor
@Component
@Slf4j
public class SuccessfulPaymentUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final PaymentService paymentService;
    private final GenerationBalanceService generationBalanceService;


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            SuccessfulPayment success = update.getMessage().getSuccessfulPayment();
            Payment payment = paymentService.processSuccessfulPaymentAndGetOrNull(success, telegramUser.getTelegramId());

            if (payment != null) {
                GenerationBalance balance = generationBalanceService.increaseBalanceByPaymentOrNull(payment, telegramUser.getTelegramId());
                if (balance != null) {

                    paymentTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), "Успешная оплата\nВаш баланс: " + balance.getGenerationRequests());
                } else {
                    //TODO ERROR
                }
            } else {
                //TODO ERROR
            }
        }
    }

    public void handleYooKassaSuccess(Payment payment, GenerationBalance balance) {
        paymentTelegramBot.sendReturnedMessage(
                payment.getTelegramId(),
                "Оплата подтверждена\nТекущий баланс: " + balance.getGenerationRequests()
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_SUCCESS.getCommandText();
    }
}
