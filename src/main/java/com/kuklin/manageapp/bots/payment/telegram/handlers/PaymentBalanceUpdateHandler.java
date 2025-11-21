package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import com.kuklin.manageapp.bots.payment.entities.UserSubscription;
import com.kuklin.manageapp.bots.payment.services.GenerationBalanceService;
import com.kuklin.manageapp.bots.payment.services.UserSubscriptionService;
import com.kuklin.manageapp.bots.payment.services.exceptions.GenerationBalanceNotFoundException;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

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
    private final UserSubscriptionService userSubscriptionService;
    private static final String BALANCE_ERROR_MSG = "Ошибка! Баланс пользователя не существует! Попробуйте ввести команду /start и повторите операцию заново";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        List<UserSubscription> subscriptions = userSubscriptionService
                .getActiveAndScheduledSubscriptions(telegramUser.getTelegramId());

        try {
            GenerationBalance generationBalance = generationBalanceService
                    .getBalanceByTelegramId(telegramUser.getTelegramId());
            paymentTelegramBot.sendReturnedMessage(chatId,
                    getBalanceString(telegramUser, subscriptions, generationBalance));
        } catch (GenerationBalanceNotFoundException e) {
            paymentTelegramBot.sendReturnedMessage(chatId, BALANCE_ERROR_MSG);
        }
    }

    private String getBalanceString(TelegramUser telegramUser, List<UserSubscription> subscriptions, GenerationBalance generationBalance) {
        StringBuilder sb = new StringBuilder();

        UserSubscription subscription = userSubscriptionService
                .getActiveSubscriptionOrNull(telegramUser.getTelegramId());
        String sub = subscription.getStatus().equals(UserSubscription.Status.ACTIVE)
                ? "активна"
                : "не активна"
                ;

        sb
                .append("ID: ").append(telegramUser.getTelegramId()).append("\n")
                .append("Баланс генераций: ").append(generationBalance.getGenerationRequests()).append(" запросов на генерацию").append("\n")
                .append("Подписка: ").append(sub).append("\n")
        ;

        for (UserSubscription subs: subscriptions) {
            sb
                    .append(subs.getStartAt())
                    .append(" - ")
                    .append(subs.getEndAt())
                    .append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
