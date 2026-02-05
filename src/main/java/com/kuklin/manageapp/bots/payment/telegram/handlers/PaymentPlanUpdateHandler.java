package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.models.providers.InvoiceFactory;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.services.PricingPlanService;
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
    private final PricingPlanService pricingPlanService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();

        //Извлечение данных о выбранном тарифном плане
        Long pricingPlanId = extractPricingPlanId(callbackQuery.getData());
        if (pricingPlanId == null) {
            //TODO
        }

        PricingPlan plan = pricingPlanService.getPricingPlanByIdOrNull(pricingPlanId);

        //Создание записи о новом платеже
        Payment payment = paymentService
                .createNewPaymentYooKassa(telegramUser.getTelegramId(), plan);

        //Создание сообщения платежжа при помощи фабрики
        SendInvoice sendInvoice = invoiceFactory.build(
                payment.getProvider(),
                chatId,
                plan.getTitle(),
                plan.getDescription(),
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

    //Извлечение данных о тарифном плане из Callback-data
    private Long extractPricingPlanId(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        if (parts.length != 2) {
            return null;
        }

        return Long.parseLong(parts[1]);
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PLAN.getCommandText();
    }
}
