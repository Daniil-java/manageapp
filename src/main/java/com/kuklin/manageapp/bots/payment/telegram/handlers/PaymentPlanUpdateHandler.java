package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.models.providers.InvoiceFactory;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Обработчик комманды Command.PAYMENT_PLAN
 *
 * Отвечает за:
 * - возвращение телеграм инвойса (сообщения с оплатой);
 * - создании записи о новом платеже в БД
 *
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class PaymentPlanUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final InvoiceFactory invoiceFactory;
    private final PaymentService paymentService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();

        //Извлечение даных о тарифном плане из callback-data
        Payment.PaymentPayload payload = extractPayment(callbackQuery.getData());
        if (payload == null) {
            //TODO
        }
        //Создание записи о новом платеже
        Payment payment = paymentService
                .createNewPaymentYooKassa(telegramUser.getTelegramId(), payload);

        //Создание сообщения платежжа при помощи фабрики
        SendInvoice sendInvoice = invoiceFactory.build(
                payment.getProvider(),
                chatId,
                payment.getPayload().getDescription(),
                payment.getPayload().getDescription(),
                payment.getTelegramInvoicePayload(),
                payment.getAmount()
        );

        try {
            paymentTelegramBot.execute(sendInvoice);
        } catch (TelegramApiException e) {
            //TODO
            //Ошибка отправки инвойса
            log.error("Telegram execute error! ", e);
            paymentTelegramBot.sendReturnedMessage(chatId, "Ошибка! Попройбуйте заново");
            //Пометка платежа, как отмененного
            paymentService.cancelPaymentOrNull(payment.getId());
        }
    }

    //Извлечение данных из callback-data
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
        return Command.PAYMENT_PLAN.getCommandText();
    }
}
