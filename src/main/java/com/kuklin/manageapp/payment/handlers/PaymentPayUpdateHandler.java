package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.payment.components.texts.PaymentText;
import com.kuklin.manageapp.payment.components.texts.PaymentTextResolver;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.models.PlanPaymentResult;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Обработчик команды Command.PAYMENT_PLAN.
 * <p>
 * Отвечает за:
 * - извлечение из callback-data id тарифного плана и выбранного провайдера;
 * - запуск платежного флоу через CommonPaymentFacade;
 * - в зависимости от результата:
 * - отправляет редирект-URL (провайдер по ссылке),
 * - создаёт ссылку на Telegram-подписку,
 * - отправляет обычный Telegram-инвойс.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class PaymentPayUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final CommonPaymentFacade commonPaymentFacade;
    private final PaymentTextResolver textResolver;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        TelegramBot telegramBot = telegramBotRegistry.get(telegramUser.getBotIdentifier());

        //Извлечение данных о выбранном тарифном плане
        Payment.Provider provider = extractProvider(callbackQuery.getData());
        if (provider == null) {
            telegramBot.sendReturnedMessage(chatId, getText(telegramUser, PaymentText.PAYMENT_DATA_ERROR));
            return;
        }

        Long pricingPlanId = extractPricingPlanId(callbackQuery.getData());
        if (pricingPlanId == null) {
            telegramBot.sendReturnedMessage(chatId, getText(telegramUser, PaymentText.PLAN_NOT_FOUND));
            return;
        }

        try {
            PlanPaymentResult result = commonPaymentFacade.startPlanPayment(
                    telegramBot.getBotIdentifier(),
                    telegramUser.getAppUserId(),
                    chatId,
                    pricingPlanId,
                    provider,
                    telegramBot.getToken()
            );

            switch (result.type()) {
                case REDIRECT_URL -> telegramBot.sendReturnedMessage(chatId, result.url());
                // ⚠️ Подписка Telegram с автосписанием — сейчас не выдаётся (CommonPaymentFacade, isTelegramSubscription = false)
                case TELEGRAM_SUBSCRIPTION_URL -> {
                    String url = telegramBot.execute(result.createInvoiceLink());
                    telegramBot.sendReturnedMessage(
                            chatId,
                            getText(telegramUser, PaymentText.TELEGRAM_SUBSCRIPTION_TEXT),
                            createSubscriptionKeyboard(url, getText(telegramUser, PaymentText.TELEGRAM_SUBSCRIPTION_BUTTON)),
                            null
                    );
                }
                case TELEGRAM_INVOICE -> telegramBotRegistry.get(telegramUser.getBotIdentifier())
                        .execute(result.invoice());
            }

            telegramBot.sendDeleteMessage(
                    chatId,
                    callbackQuery.getMessage().getMessageId()
            );

        } catch (PricingPlanNotFoundException e) {
            telegramBot.sendReturnedMessage(chatId, getText(telegramUser, PaymentText.PLAN_NOT_FOUND));
        } catch (TelegramApiException e) {
            log.error("Telegram execute error! ", e);
            telegramBot.sendReturnedMessage(chatId, getText(telegramUser, PaymentText.INVOICE_SEND_ERROR));
        } catch (PaymentNotFoundException e) {
            log.error("Payment not found error!");
        }
    }

    private String getText(TelegramUser telegramUser, PaymentText text) {
        return textResolver.getText(telegramUser.getBotIdentifier(), text, telegramUser.getAppUserId());
    }

    private InlineKeyboardMarkup createSubscriptionKeyboard(String url, String buttonText) {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton payButton =
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        payButton.setText(buttonText);
        payButton.setUrl(url);

        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup =
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        markup.setKeyboard(java.util.List.of(java.util.List.of(TelegramKeyboard.withStyle(payButton, ButtonStyle.SUCCESS))));

        return markup;
    }

    // Извлекает провайдера из callback-data вида:
    // <command>::<planId>::<providerName>
    private Payment.Provider extractProvider(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        if (parts.length != 3) {
            return null;
        }

        try {
            return Enum.valueOf(Payment.Provider.class, parts[2]);
        } catch (Exception e) {
            log.error("Invalid provider!");
            return null;
        }
    }

    // Извлекает id тарифного плана из callback-data.
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
