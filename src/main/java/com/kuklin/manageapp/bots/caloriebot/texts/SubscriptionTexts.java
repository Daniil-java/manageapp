package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Строка подписки «Подписка / 📌 Статус / ⏳ Период» — в «Статусе подписки», после /start (пробный период)
 * и в сообщении планировщика о начале подписки (раньше — SubscriptionStatusCalorieUpdateHandler).
 */
@Component
public class SubscriptionTexts extends AbstractBotTexts {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PlanTexts planTexts;
    private final PricingPlanService pricingPlanService;
    private final UserSettingsService userSettingsService;

    public SubscriptionTexts(CalorieTexts texts, PlanTexts planTexts, PricingPlanService pricingPlanService,
                             UserSettingsService userSettingsService) {
        super(texts);
        this.planTexts = planTexts;
        this.pricingPlanService = pricingPlanService;
        this.userSettingsService = userSettingsService;
    }

    /** Период — в часовом поясе пользователя. */
    public String buildSubscriptionEntryText(Long appUserId, UserSubscription sub) {
        AppLanguage language = languageOf(appUserId);
        ZoneId zoneId = userSettingsService.getZoneId(appUserId);

        String name;
        try {
            PricingPlan pricingPlan = pricingPlanService.getPricingPlanById(sub.getPricingPlanId());
            name = planTexts.getTitle(pricingPlan, language);
        } catch (PricingPlanNotFoundException e) {
            name = texts.get(language, BotTextKeys.SUBSCRIPTION_UNKNOWN_PLAN);
        }
        String period = (sub.getStartAt() != null && sub.getEndAt() != null)
                ? String.format("%s — %s",
                sub.getStartAt().atZone(zoneId).format(PERIOD_FORMAT), sub.getEndAt().atZone(zoneId).format(PERIOD_FORMAT))
                : texts.get(language, BotTextKeys.SUBSCRIPTION_NO_PERIOD);

        // Статус — по имени: подписи UserSubscription.Status в общем модуле оплаты русские
        String status = texts.get(language, BotTextKeys.buildSubscriptionStatusKey(sub.getStatus()));
        return texts.get(language, BotTextKeys.SUBSCRIPTION_ENTRY, name, status, period);
    }
}
