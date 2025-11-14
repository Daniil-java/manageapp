package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Обработчик комманды по интерфейсу. Используется для отправки сообщения об успешной оплатте от вебхука
 *
 * Отвечает за:
 * - отправка сообщения о статусе оплаты
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class WebhookSuccessfulPaymentUpdateHandler implements PaymentUpdateHandler{
    private final PaymentTelegramBot paymentTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        return;
    }

    public void handleYooKassaSuccess(Payment payment, GenerationBalance balance) {
        paymentTelegramBot.sendReturnedMessage(
                payment.getTelegramId(),
                "Оплата подтверждена\nТекущий баланс: " + balance.getGenerationRequests()
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_SUCCESS.name();
    }
}
