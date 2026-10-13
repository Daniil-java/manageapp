package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class SubscriptionStatusCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSubscriptionService userSubscriptionService;
    private final UserSettingsService userSettingsService;
    private final PricingPlanService pricingPlanService;
    private final CalorieTexts texts;

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
                .map(sub -> getSubscriptionStatusMessage(appUserId, sub))
                .collect(Collectors.joining("\n\n"));

        return texts.get(appUserId, BotTextKeys.SUBSCRIPTION_TITLE) + "\n\n" + details;
    }

    public String getSubscriptionStatusMessage(Long appUserId, UserSubscription sub) {
        UserSettings userSettings = userSettingsService.getOrCreate(appUserId);

        ZoneId zoneId = userSettings.getZoneId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        String name;
//        String description;
        try {
            PricingPlan pricingPlan = pricingPlanService.getPricingPlanById(sub.getPricingPlanId());
            name = pricingPlan.getTitle();
//            description = pricingPlan.getDescription();
        } catch (PricingPlanNotFoundException e) {
            name = texts.get(appUserId, BotTextKeys.SUBSCRIPTION_UNKNOWN_PLAN);
        }
        String period = (sub.getStartAt() != null && sub.getEndAt() != null)
                ? String.format("%s — %s", sub.getStartAt().atZone(zoneId).format(fmt), sub.getEndAt().atZone(zoneId).format(fmt))
                : texts.get(appUserId, BotTextKeys.SUBSCRIPTION_NO_PERIOD);

        // Статус — по имени: подписи UserSubscription.Status в общем модуле оплаты русские
        String status = texts.get(appUserId, "subscription.status." + sub.getStatus().name());
        return texts.get(appUserId, BotTextKeys.SUBSCRIPTION_ENTRY, name, status, period);
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
