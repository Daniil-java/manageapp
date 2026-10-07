package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService.FREE_PLAN_CODE;

/**
 * Данные для лендинга zefir.fit. Тарифы и лимиты берутся из БД (pricing_plans, plan_features) —
 * страница не расходится с тем, что реально продаёт бот.
 */
@Service
@RequiredArgsConstructor
public class CalorieLandingService {
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;
    private static final String NOT_AVAILABLE = "—";
    private static final String UNLIMITED = "Без лимита";

    private final PricingPlanService pricingPlanService;
    private final PlanFeatureService planFeatureService;
    private final AiRateLimitProperties aiRateLimitProperties;

    /** Код UTM-ссылки (/calorie/utm-form) для кнопок «Открыть в Telegram». Пусто — ссылка без метки. */
    @Value("${calorie.landing.start-code:}")
    private String startCode;

    /** Ключ закрытого доступа к лендингу (?key=...). Пусто — лендинг открыт всем. */
    @Value("${calorie.landing.access-key:}")
    private String accessKey;

    /** Лендинг закрыт: открывается только по ссылке с ключом. */
    public boolean isClosed() {
        return accessKey != null && !accessKey.isBlank();
    }

    public boolean isAccessKeyValid(String presentedKey) {
        if (!isClosed() || presentedKey == null) {
            return false;
        }
        // Сравнение за постоянное время — ключ не подобрать по времени ответа
        return MessageDigest.isEqual(
                accessKey.getBytes(StandardCharsets.UTF_8),
                presentedKey.getBytes(StandardCharsets.UTF_8));
    }

    public LandingPage getLandingPage() {
        List<PricingPlan> plans = pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BOT).stream()
                .filter(plan -> plan.getPayloadType() == PricingPlan.PricingPlanType.SUBSCRIPTION)
                .filter(plan -> plan.getDurationDays() != null && plan.getDurationDays() > 0)
                .sorted(Comparator.comparing(PricingPlan::getDurationDays)
                        .thenComparing(plan -> plan.getCurrency().name()))
                .toList();

        Map<BotFeature, PlanFeature> free = featuresByPlan(FREE_PLAN_CODE);
        // Лимиты у всех платных планов одинаковые (копии) — берём у первого
        Map<BotFeature, PlanFeature> premium = plans.isEmpty()
                ? Map.of()
                : featuresByPlan(plans.get(0).getCodeForOrderId());

        List<FeatureRow> features = new ArrayList<>();
        features.add(new FeatureRow(
                "Запросы к ИИ: фото, текст, голос, отчёты",
                "до " + aiRateLimitProperties.getPerDay() + " в сутки",
                "до " + aiRateLimitProperties.getPerDayPremium() + " в сутки"));
        for (BotFeature feature : BotFeature.values()) {
            features.add(new FeatureRow(
                    feature.getDisplayName(),
                    describeLimit(free.get(feature)),
                    describeLimit(premium.get(feature))));
        }

        return new LandingPage(toPlanCards(plans), features, botUrl());
    }

    private Map<BotFeature, PlanFeature> featuresByPlan(String planCode) {
        Map<BotFeature, PlanFeature> result = new EnumMap<>(BotFeature.class);
        planFeatureService.getFeaturesByPlanCode(planCode, BOT)
                .forEach(planFeature -> result.put(planFeature.getFeature(), planFeature));
        return result;
    }

    static String describeLimit(PlanFeature planFeature) {
        if (planFeature == null) {
            return NOT_AVAILABLE;
        }
        Integer limit = planFeature.getLimitValue();
        if (planFeature.getLimitPeriod() == FeatureLimitPeriod.UNLIMITED || limit == null || limit <= -1) {
            return UNLIMITED;
        }
        if (limit == 0) {
            return NOT_AVAILABLE;
        }
        return switch (planFeature.getLimitPeriod()) {
            case DAILY -> limit + " в день";
            case MONTHLY -> limit + " в месяц";
            case LIFETIME, UNLIMITED -> "до " + limit;
        };
    }

    /**
     * Карточки тарифов. Выгода считается к самому короткому плану в той же валюте:
     * 3 месяца за 350 ⭐ против 3 × 150 ⭐ — «выгода 22%».
     */
    private List<PlanCard> toPlanCards(List<PricingPlan> plans) {
        Map<Currency, PricingPlan> shortestByCurrency = new EnumMap<>(Currency.class);
        plans.forEach(plan -> shortestByCurrency.putIfAbsent(plan.getCurrency(), plan));

        return plans.stream()
                .map(plan -> new PlanCard(
                        plan.getTitle(),
                        plan.getDescription(),
                        formatPrice(plan.getPriceMinor(), plan.getCurrency()),
                        describeDuration(plan.getDurationDays()),
                        savingPercent(plan, shortestByCurrency.get(plan.getCurrency()))))
                .toList();
    }

    static String formatPrice(Integer priceMinor, Currency currency) {
        if (currency == Currency.XTR) {
            // У Telegram Stars нет дробных единиц: price_minor — это и есть количество звёзд
            return priceMinor + " ⭐";
        }
        BigDecimal rubles = BigDecimal.valueOf(priceMinor).movePointLeft(2).stripTrailingZeros();
        return rubles.toPlainString().replace('.', ',') + " ₽";
    }

    static String describeDuration(int days) {
        if (days % 30 == 0) {
            int months = days / 30;
            return months == 1 ? "1 месяц" : months + (months < 5 ? " месяца" : " месяцев");
        }
        return days + " дней";
    }

    static Integer savingPercent(PricingPlan plan, PricingPlan base) {
        if (base == null || plan == base || base.getPriceMinor() == null || plan.getPriceMinor() == null) {
            return null;
        }
        BigDecimal baseForSameDays = BigDecimal.valueOf(base.getPriceMinor())
                .multiply(BigDecimal.valueOf(plan.getDurationDays()))
                .divide(BigDecimal.valueOf(base.getDurationDays()), 2, RoundingMode.HALF_UP);
        if (baseForSameDays.signum() <= 0) {
            return null;
        }
        int percent = BigDecimal.ONE
                .subtract(BigDecimal.valueOf(plan.getPriceMinor()).divide(baseForSameDays, 4, RoundingMode.HALF_UP))
                .movePointRight(2)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        return percent > 0 ? percent : null;
    }

    private String botUrl() {
        String url = "https://t.me/" + BOT.getBotUsername().replace("@", "");
        return startCode == null || startCode.isBlank() ? url : url + "?start=" + startCode;
    }

    public record LandingPage(List<PlanCard> plans, List<FeatureRow> features, String botUrl) { }

    /** saving — выгода в процентах к самому короткому плану, null — без выгоды. */
    public record PlanCard(String title, String description, String price, String duration, Integer saving) { }

    public record FeatureRow(String name, String free, String premium) { }
}
