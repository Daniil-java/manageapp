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

/**
 * Обработчик сообщения об успешной оплате, которое присылает телеграм
 *
 * Отвечает за:
 * - принятие запроса из телеграм о статусе оплаты
 * - пополнение баланса пользователя
 *
 */
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
            //Обработка успешного сообщенияя в paymentService
            Payment payment = paymentService.processSuccessfulPaymentAndGetOrNull(success, telegramUser.getTelegramId());

            if (payment != null) {
                //Увеличение баланса согласно купленного плана
                GenerationBalance balance = generationBalanceService.increaseBalanceByPayment(payment);
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
