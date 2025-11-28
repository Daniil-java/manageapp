package com.kuklin.manageapp.bots.payment.telegram.handlers.telegrampay;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceIllegalOperationDataException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotEnoughBalanceException;
import com.kuklin.manageapp.bots.payment.services.exceptions.generationbalance.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.bots.payment.services.exceptions.payment.PaymentException;
import com.kuklin.manageapp.bots.payment.services.exceptions.payment.PaymentValidationDataException;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.bots.payment.telegram.handlers.PaymentBalanceUpdateHandler;
import com.kuklin.manageapp.bots.payment.telegram.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

/**
 * Обработчик сообщения об успешной оплате, которое присылает телеграм
 * <p>
 * Отвечает за:
 * - принятие запроса из телеграм о статусе оплаты
 * - пополнение баланса пользователя
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class SuccessfulPaymentUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final PaymentService paymentService;
    private final PaymentBalanceUpdateHandler balanceUpdateHandler;
    private static final String SUCCESS_MSG = """
            Успешная оплата!
            """;
    private static final String PLAN_ERROR_MSG = """
            Не удалось найти платежный план!
            """;
    private static final String VALIDATION_ERROR_MSG = """
            Ошибка валидации платежный данных
            """;
    private static final String BALANCE_ERROR_MSG = """
            Баланс пользователя не найден! Попробуйте написать командду /start
            """;
    private static final String BALANCE_NOT_ENOUGH_ERROR_MSG = """
            Недостаточно генераций!
            """;
    private static final String BALANCE_ILLEGAL_OPERATION_ERROR_MSG = """
            В тарифном плане ошибка! Выберите другой!
            """;
    private static final String PAYMENT_ERROR_MSG = """
            Ошибка платежа! Обратитесь к администратору!
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            Long chatId = update.getMessage().getChatId();
            SuccessfulPayment success = update.getMessage().getSuccessfulPayment();
            //Обработка успешного сообщенияя в paymentService
            try {
                Payment payment = paymentService.processTelegramSuccessfulPaymentAndGetOrNull(
                        success, telegramUser.getTelegramId());

                if (payment == null) {
                    paymentTelegramBot.sendReturnedMessage(
                            chatId, PAYMENT_ERROR_MSG);
                    return;
                }
                paymentTelegramBot.sendReturnedMessage(
                        chatId, SUCCESS_MSG);
                balanceUpdateHandler.handle(update, telegramUser);

            } catch (PaymentValidationDataException e) {
                paymentTelegramBot.sendReturnedMessage(chatId, VALIDATION_ERROR_MSG);
            } catch (PricingPlanNotFoundException e) {
                paymentTelegramBot.sendReturnedMessage(chatId, PLAN_ERROR_MSG);
            } catch (PaymentException e) {
                paymentTelegramBot.sendReturnedMessage(chatId, PAYMENT_ERROR_MSG);
            }
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_SUCCESS.getCommandText();
    }
}
