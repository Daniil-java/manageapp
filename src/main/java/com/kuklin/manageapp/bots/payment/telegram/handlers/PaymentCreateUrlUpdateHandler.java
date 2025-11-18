package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.services.PricingPlanService;
import com.kuklin.manageapp.bots.payment.services.YooKassaPaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Обработчик комманды Command.PAYMENT_YOOKASSA_URL_CREATE
 *
 * Отвечает за:
 * - создание ссылки на оплату через ЮКассу
 */
@RequiredArgsConstructor
@Component
public class PaymentCreateUrlUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final YooKassaPaymentService yooKassaPaymentService;
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

        //Получение ссылки от ЮКассы
        YooKassaPaymentService.Created created = yooKassaPaymentService.create(
                payment.getAmount(),
                payment.getCurrency().name(),
                payment.getDescription(),
                payment.getTelegramId(),
                chatId,
                plan.getCodeForOrderId(),
                payment.getTelegramInvoicePayload()
        );

        //Сохранение специального идентификатора оплаты от ЮКассы
        paymentService.setProviderPaymentId(payment, created);

        if (created == null || created.getConfirmationUrl() == null) {
            //TODO ERROR
            return;
        }

        paymentTelegramBot.sendReturnedMessage(chatId, "Оплаты ЮКасса: \n" + created.getConfirmationUrl());
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
        return Command.PAYMENT_YOOKASSA_URL_CREATE.getCommandText();
    }
}
