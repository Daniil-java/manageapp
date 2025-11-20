package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.models.common.Currency;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.services.PricingPlanService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.bots.payment.telegram.handlers.providerprocessors.PaymentUrlProviderFactory;
import com.kuklin.manageapp.bots.payment.telegram.handlers.providerprocessors.ProviderResult;
import com.kuklin.manageapp.bots.payment.telegram.handlers.providerprocessors.SendInvoiceBuilder;
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
public class PaymentPayUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final PaymentService paymentService;
    private final PricingPlanService pricingPlanService;
    private final PaymentUrlProviderFactory paymentUrlProviderFactory;
    private final SendInvoiceBuilder sendInvoiceBuilder;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();

        //Извлечение данных о выбранном тарифном плане
        Payment.Provider provider = extractProvider(callbackQuery.getData());
        if (provider == null) {
            //TODO ERROR
        }

        Long pricingPlanId = extractPricingPlanId(callbackQuery.getData());
        PricingPlan plan = pricingPlanService.getPricingPlanByIdOrNull(pricingPlanId);

        //TODO Уйти от привязки к ЮКассе
        Payment payment = paymentService
                .createNewPaymentYooKassa(telegramUser.getTelegramId(), plan);

        if (provider.getProviderFlow().equals(Payment.PaymentFlow.PROVIDER_REDIRECT)) {
            ProviderResult result = paymentUrlProviderFactory.handle(provider, payment, plan, chatId);
            paymentService.setProviderPaymentId(payment, result.paymentId());
            paymentTelegramBot.sendReturnedMessage(chatId, result.url());
            paymentTelegramBot.sendDeleteMessage(chatId, update.getCallbackQuery().getMessage().getMessageId());
            return;
        }

        SendInvoice sendInvoice = sendInvoiceBuilder.build(provider, payment, plan, chatId);
        try {
            paymentTelegramBot.execute(sendInvoice);
            paymentTelegramBot.sendDeleteMessage(chatId, update.getCallbackQuery().getMessage().getMessageId());
        } catch (TelegramApiException e) {
            //TODO
            //Ошибка отправки инвойса
            log.error("Telegram execute error! ", e);
            paymentTelegramBot.sendReturnedMessage(chatId, "Ошибка! Попройбуйте заново");
            //Пометка платежа, как отмененного
            paymentService.cancelPaymentOrNull(payment.getId());
        }
    }

    private Payment.Provider extractProvider(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        if (parts.length != 3) {
            return null;
        }

        try {
            Payment.Provider provider = Enum.valueOf(
                    Payment.Provider.class, parts[2]);
            return provider;
        } catch (Exception e) {
            log.error("Invalid provider!");
            return null;
        }
    }

    //Извлечение данных о тарифном плане из Callback-data
    private Long extractPricingPlanId(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        if (parts.length != 3) {
            return null;
        }

        return Long.parseLong(parts[1]);
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PLAN.getCommandText();
    }
}
