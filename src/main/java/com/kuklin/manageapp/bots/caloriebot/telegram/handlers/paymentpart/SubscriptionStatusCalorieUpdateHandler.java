package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.texts.SubscriptionTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class SubscriptionStatusCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSubscriptionService userSubscriptionService;
    private final CalorieTexts texts;
    private final SubscriptionTexts subscriptionTexts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery() ?
                update.getCallbackQuery().getMessage().getChatId() :
                update.getMessage().getChatId();

        calorieTelegramBot.sendReturnedMessage(
                chatId,
                getTextStatus(telegramUser.getAppUserId())
        );
    }

    private String getTextStatus(Long appUserId) {
        List<UserSubscription> subscriptions = userSubscriptionService
                .getActiveAndScheduledSubscriptions(appUserId, BotIdentifier.CALORIE_BOT);

        if (subscriptions.isEmpty()) {
            return texts.get(appUserId, BotTextKeys.SUBSCRIPTION_NONE);
        }

        String details = subscriptions.stream()
                // Нам нужны только ACTIVE и SCHEDULED (хотя сервис и так должен их вернуть)
                .filter(sub -> sub.getStatus() == UserSubscription.Status.ACTIVE ||
                        sub.getStatus() == UserSubscription.Status.SCHEDULED)
                .map(sub -> subscriptionTexts.buildSubscriptionEntryText(appUserId, sub))
                .collect(Collectors.joining("\n\n"));

        return texts.get(appUserId, BotTextKeys.SUBSCRIPTION_TITLE) + "\n\n" + details;
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
