package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AbstractBotTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Статус Premium человеческим языком: «💎 Premium до 13 ноября · ещё 7 дней» вместо строк «тариф / статус /
 * период». Подписки идут цепочкой (пробный → бонус за друга → купленная), поэтому Premium действует до конца
 * последней из них. Даты — в часовом поясе пользователя.
 */
@Component
public class SubscriptionTexts extends AbstractBotTexts {
    private static final String DATE_PATTERN = "d MMMM";
    private static final String DATE_WITH_YEAR_PATTERN = "d MMMM yyyy";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PlanTexts planTexts;
    private final PricingPlanService pricingPlanService;
    private final UserSettingsService userSettingsService;
    private final UserSubscriptionService userSubscriptionService;

    public SubscriptionTexts(CalorieTexts texts, PlanTexts planTexts, PricingPlanService pricingPlanService,
                             UserSettingsService userSettingsService, UserSubscriptionService userSubscriptionService) {
        super(texts);
        this.planTexts = planTexts;
        this.pricingPlanService = pricingPlanService;
        this.userSettingsService = userSettingsService;
        this.userSubscriptionService = userSubscriptionService;
    }

    /** Действующие и запланированные подписки по порядку. */
    public List<UserSubscription> findWorkingSubscriptions(Long appUserId) {
        return userSubscriptionService.getActiveAndScheduledSubscriptions(appUserId, BotIdentifier.CALORIE_BOT);
    }

    /** Конец Premium — конец последней подписки в цепочке; null — Premium нет. */
    public static Instant findPremiumEndOrNull(List<UserSubscription> subscriptions) {
        return subscriptions.stream()
                .map(UserSubscription::getEndAt)
                .filter(end -> end != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** «🆓 Сейчас: бесплатный план» или «💎 Сейчас: Premium до 13 ноября · ещё 7 дней». */
    public String buildStatusLine(Long appUserId) {
        return buildStatusLine(appUserId, findWorkingSubscriptions(appUserId), Instant.now());
    }

    String buildStatusLine(Long appUserId, List<UserSubscription> subscriptions, Instant now) {
        AppLanguage language = languageOf(appUserId);
        Instant end = findPremiumEndOrNull(subscriptions);
        if (end == null || !end.isAfter(now)) {
            return texts.get(language, BotTextKeys.PREMIUM_STATUS_FREE);
        }
        return texts.get(language, BotTextKeys.PREMIUM_STATUS_ACTIVE,
                formatDate(appUserId, end, now), buildTimeLeft(language, end, now));
    }

    /**
     * Строка статуса и, если подписок в цепочке несколько, из чего она сложилась:
     * «• Пробный период — 3 дня», «• Бонус за друга — 7 дней».
     */
    public String buildStatusDetails(Long appUserId) {
        List<UserSubscription> subscriptions = findWorkingSubscriptions(appUserId);
        Instant now = Instant.now();
        String line = buildStatusLine(appUserId, subscriptions, now);
        if (subscriptions.size() < 2) {
            return line;
        }
        AppLanguage language = languageOf(appUserId);
        String parts = subscriptions.stream()
                .map(sub -> texts.get(language, BotTextKeys.PREMIUM_STATUS_PART,
                        getPlanTitle(sub, language), getDurationDays(sub)))
                .collect(Collectors.joining("\n"));
        return line + "\n\n" + texts.get(language, BotTextKeys.PREMIUM_STATUS_PARTS) + "\n" + parts;
    }

    /** «Подписка «Premium Месяц» началась — Premium до 13 февраля». */
    public String buildStartedText(UserSubscription started) {
        Long appUserId = started.getAppUserId();
        AppLanguage language = languageOf(appUserId);
        Instant end = findPremiumEndOrNull(findWorkingSubscriptions(appUserId));
        if (end == null) {
            end = started.getEndAt();
        }
        return texts.get(language, BotTextKeys.PAYMENT_SUBSCRIPTION_STARTED,
                getPlanTitle(started, language), formatDate(appUserId, end, Instant.now()));
    }

    /** «13 ноября в 14:00» — для предупреждения, что Premium заканчивается. */
    public String formatDateTime(Long appUserId, Instant instant) {
        ZoneId zone = userSettingsService.getZoneId(appUserId);
        return texts.get(languageOf(appUserId), BotTextKeys.PREMIUM_DATE_TIME,
                formatDate(appUserId, instant, Instant.now()), instant.atZone(zone).format(TIME_FORMAT));
    }

    /** «13 ноября»; другой год — «13 ноября 2027». */
    String formatDate(Long appUserId, Instant instant, Instant now) {
        ZoneId zone = userSettingsService.getZoneId(appUserId);
        ZonedDateTime date = instant.atZone(zone);
        String pattern = date.getYear() == now.atZone(zone).getYear() ? DATE_PATTERN : DATE_WITH_YEAR_PATTERN;
        Locale locale = Locale.forLanguageTag(languageOf(appUserId).getCode());
        return date.format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    private String buildTimeLeft(AppLanguage language, Instant end, Instant now) {
        long hoursLeft = Duration.between(now, end).toHours();
        if (hoursLeft < 24) {
            return texts.get(language, BotTextKeys.PREMIUM_LEFT_LESS_THAN_DAY);
        }
        // Неполный день считаем целым: «ещё 3 дня» до конца третьих суток
        long daysLeft = (hoursLeft + 23) / 24;
        return texts.get(language, BotTextKeys.PREMIUM_LEFT_DAYS, daysLeft);
    }

    private long getDurationDays(UserSubscription sub) {
        if (sub.getStartAt() == null || sub.getEndAt() == null) {
            return 0;
        }
        return Math.round(Duration.between(sub.getStartAt(), sub.getEndAt()).toHours() / 24.0);
    }

    private String getPlanTitle(UserSubscription sub, AppLanguage language) {
        try {
            PricingPlan plan = pricingPlanService.getPricingPlanById(sub.getPricingPlanId());
            return planTexts.getTitle(plan, language);
        } catch (PricingPlanNotFoundException e) {
            return "Premium";
        }
    }
}
