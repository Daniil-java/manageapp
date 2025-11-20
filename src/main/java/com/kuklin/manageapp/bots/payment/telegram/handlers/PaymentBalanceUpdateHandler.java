package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.services.GenerationBalanceService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Обработчик комманды Command.PAYMENT_BALANCE
 *
 * Отвечает за:
 * - отправку баланса пользователю
 */
@Component
@RequiredArgsConstructor
public class PaymentBalanceUpdateHandler implements PaymentUpdateHandler {

    private final PaymentTelegramBot paymentTelegramBot;
    private final GenerationBalanceService generationBalanceService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        GenerationBalance generationBalance = generationBalanceService
                .getBalanceByTelegramIdOrNull(telegramUser.getTelegramId());
        paymentTelegramBot.sendReturnedMessage(chatId, getBalanceString(telegramUser, generationBalance));
    }

    private String getBalanceString(TelegramUser telegramUser, GenerationBalance generationBalance) {
        StringBuilder sb = new StringBuilder();

        sb
                .append("ID: ").append(telegramUser.getTelegramId()).append("\n")
                .append("Баланс: ").append(generationBalance.getGenerationRequests()).append(" запросов на генерацию")
        ;

        return sb.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
