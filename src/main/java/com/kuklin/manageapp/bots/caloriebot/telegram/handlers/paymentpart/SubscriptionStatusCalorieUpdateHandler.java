package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.common.CalorieBotUpdateHandler;
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

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery() ?
                update.getCallbackQuery().getMessage().getChatId() :
                update.getMessage().getChatId();

        calorieTelegramBot.sendReturnedMessage(
                chatId,
                getTextStatus(telegramUser.getTelegramId())
        );
    }

    private String getTextStatus(Long telegramId) {
        List<UserSubscription> subscriptions = userSubscriptionService
                .getActiveAndScheduledSubscriptions(telegramId, BotIdentifier.CALORIE_BOT);

        if (subscriptions.isEmpty()) {
            return "У вас пока нет активных или запланированных подписок.";
        }

        String details = subscriptions.stream()
                // Нам нужны только ACTIVE и SCHEDULED (хотя сервис и так должен их вернуть)
                .filter(sub -> sub.getStatus() == UserSubscription.Status.ACTIVE ||
                        sub.getStatus() == UserSubscription.Status.SCHEDULED)
                .map(sub -> getSubscriptionStatusMessage(telegramId, sub))
                .collect(Collectors.joining("\n\n"));

        return "Статус подписки:\n\n" + details;
    }

    public String getSubscriptionStatusMessage(Long telegramId, UserSubscription sub) {
        UserSettings userSettings = userSettingsService.getOrCreate(telegramId);

        ZoneId zoneId = userSettings.getZoneId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        String name;
        String description;
        try {
            PricingPlan pricingPlan = pricingPlanService.getPricingPlanById(sub.getPricingPlanId());
            name = pricingPlan.getTitle();
            description = pricingPlan.getDescription();
        } catch (PricingPlanNotFoundException e) {
            name = "Неизвестно";
            description = "Без описания";
        }
        String period = (sub.getStartAt() != null && sub.getEndAt() != null)
                ? String.format("%s — %s", sub.getStartAt().atZone(zoneId).format(fmt), sub.getEndAt().atZone(zoneId).format(fmt))
                : "не задан";

        return String.format("Подписка: %s\n %s\n📌 Статус: %s\n⏳ Период: %s", name, description, sub.getStatus().getCommandText(), period);
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
