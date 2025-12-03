package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Обработчик комманды Command.PAYMENT_BALANCE
 * <p>
 * Отвечает за:
 * - отправку баланса пользователю
 */
@Component
@RequiredArgsConstructor
public class PaymentBalanceUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final CommonPaymentFacade commonPaymentFacade;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        telegramBotRegistry.get(telegramUser.getBotIdentifier())
                .sendReturnedMessage(
                        chatId,
                        commonPaymentFacade.getBalanceSubscriptionString(
                                telegramUser, telegramUser.getBotIdentifier()
                        ));
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
