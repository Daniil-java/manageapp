package com.kuklin.manageapp.payment.handlers.telegrampay;

import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentException;
import com.kuklin.manageapp.payment.services.exceptions.payment.PaymentValidationDataException;
import com.kuklin.manageapp.payment.handlers.PaymentBalanceUpdateHandler;
import com.kuklin.manageapp.payment.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

/**
 * Обработчик сообщения об успешной оплате, которое присылает Telegram.
 *
 * Отвечает за:
 * - передачу SuccessfulPayment в CommonPaymentFacade;
 * - обработку случая, когда платёж уже был обработан (идемпотентность);
 * - отправку пользователю сообщений об успехе/ошибке;
 * - повторный вывод баланса (через PaymentBalanceUpdateHandler).
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class SuccessfulPaymentUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final CommonPaymentFacade commonPaymentFacade;
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
    private static final String PAYMENT_ERROR_MSG = """
            Ошибка платежа! Обратитесь к администратору!
            """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            Long chatId = update.getMessage().getChatId();
            SuccessfulPayment success = update.getMessage().getSuccessfulPayment();
            TelegramBot telegramBot = telegramBotRegistry.get(telegramUser.getBotIdentifier());

            //Обработка успешного сообщенияя в paymentService
            try {
                Payment payment = commonPaymentFacade.handleSuccessfulPayment(success, telegramUser.getTelegramId());
                if (payment == null) {
                    telegramBot.sendReturnedMessage(
                            chatId, PAYMENT_ERROR_MSG);
                    return;
                }
                telegramBot.sendReturnedMessage(
                        chatId, SUCCESS_MSG);
                balanceUpdateHandler.handle(update, telegramUser);

            } catch (PaymentValidationDataException e) {
                telegramBot.sendReturnedMessage(chatId, VALIDATION_ERROR_MSG);
            } catch (PricingPlanNotFoundException e) {
                telegramBot.sendReturnedMessage(chatId, PLAN_ERROR_MSG);
            } catch (PaymentException e) {
                telegramBot.sendReturnedMessage(chatId, PAYMENT_ERROR_MSG);
            }
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_SUCCESS.getCommandText();
    }
}
