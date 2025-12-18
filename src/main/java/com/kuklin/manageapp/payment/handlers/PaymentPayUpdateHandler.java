package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
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
    private static final String PLAN_ERROR_MSG = """
                        Не получилось найти тарифный план!
            """;
    private static final String DATA_EXTRACT_ERROR_MSG = """
                        Бот не смог извлечь данные о платежном формате!
            """;
    private static final String TELEGRAM_ERROR_MSG = """
                        Ошибка! Не получилось отправить форму оплаты! Попройбуйте заново
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        TelegramBot telegramBot = telegramBotRegistry.get(telegramUser.getBotIdentifier());

        //Извлечение данных о выбранном тарифном плане
        Payment.Provider provider = extractProvider(callbackQuery.getData());
        if (provider == null) {
            telegramBotRegistry.get(telegramUser.getBotIdentifier())
                    .sendReturnedMessage(chatId, DATA_EXTRACT_ERROR_MSG);
        }

        Long pricingPlanId = extractPricingPlanId(callbackQuery.getData());
        if (pricingPlanId == null) {
            telegramBotRegistry.get(telegramUser.getBotIdentifier())
                    .sendReturnedMessage(chatId, PLAN_ERROR_MSG);
        }

        try {
            PlanPaymentResult result = commonPaymentFacade.startPlanPayment(
                    telegramBot.getBotIdentifier(),
                    telegramUser.getTelegramId(),
                    chatId,
                    pricingPlanId,
                    provider,
                    telegramBot.getToken()
            );

            switch (result.type()) {
                case REDIRECT_URL -> telegramBot.sendReturnedMessage(chatId, result.url());
                case TELEGRAM_SUBSCRIPTION_URL -> {
                    String url = telegramBot.execute(result.createInvoiceLink());
                    telegramBot
                            .sendReturnedMessage(chatId, url);
                }
                case TELEGRAM_INVOICE -> telegramBotRegistry.get(telegramUser.getBotIdentifier())
                        .execute(result.invoice());
            }

            telegramBot.sendDeleteMessage(
                    chatId,
                    callbackQuery.getMessage().getMessageId()
            );

        } catch (PricingPlanNotFoundException e) {
            telegramBot.sendReturnedMessage(chatId, PLAN_ERROR_MSG);
        } catch (TelegramApiException e) {
            log.error("Telegram execute error! ", e);
            telegramBot.sendReturnedMessage(chatId, TELEGRAM_ERROR_MSG);
        } catch (PaymentNotFoundException e) {
            log.error("Payment not found error!");
        }
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
