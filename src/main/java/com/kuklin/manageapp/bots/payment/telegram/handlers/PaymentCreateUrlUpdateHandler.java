package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.services.YooKassaPaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@RequiredArgsConstructor
@Component
public class PaymentCreateUrlUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final YooKassaPaymentService yooKassaPaymentService;
    private final PaymentService paymentService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();

        Payment.PaymentPayload payload = extractPayment(callbackQuery.getData());
        if (payload == null) {
            //TODO
        }

        Payment payment = paymentService
                .createNewPaymentYooKassa(telegramUser.getTelegramId(), payload);

        YooKassaPaymentService.Created created = yooKassaPaymentService.create(
                payment.getAmount(),
                payment.getCurrency().name(),
                payment.getDescription(),
                payment.getTelegramId(),
                chatId,
                payment.getPayload().getDescription(),
                payment.getTelegramInvoicePayload()
        );

        paymentTelegramBot.sendReturnedMessage(chatId, created.getConfirmationUrl());
    }

    private Payment.PaymentPayload extractPayment(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        if (parts.length != 2) {
            return null;
        }

        try {
            return Payment.PaymentPayload.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            //TODO
            return null;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_YOOKASSA_URL_CREATE.getCommandText();
    }
}
