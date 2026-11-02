package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.LandingTextKeys;
import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.bots.caloriebot.texts.PlanTexts;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** Эмодзи в начале пункта: символы, модификаторы цвета кожи, вариационный селектор, ZWJ, keycap. */
    private static final Pattern LEADING_ICON =
            Pattern.compile("^([\\p{So}\\p{Sk}\\x{FE0F}\\x{200D}\\x{20E3}]+)\\s*(.*)$");
    private static final Pattern BULLET = Pattern.compile("^[-•*]\\s+");

    private final PricingPlanService pricingPlanService;
    private final PlanFeatureService planFeatureService;
    private final AiRateLimitProperties aiRateLimitProperties;
    private final PlanTexts planTexts;
    private final CalorieTexts texts;

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

    /** Данные лендинга на языке страницы: тарифы, таблица лимитов, ссылка на бота с кодом из конфига. */
    public LandingPage getLandingPage(AppLanguage language) {
        return getLandingPage(language, null);
    }

    /**
     * То же, но кнопки «Открыть в Telegram» ведут по коду кампании (zefir.fit/?utm=код, уже проверен —
     * UtmService.findLandingCodeOrNull). null — по коду из конфига calorie.landing.start-code.
     */
    public LandingPage getLandingPage(AppLanguage language, String campaignCode) {
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
                texts.get(language, LandingTextKeys.LIMIT_AI_REQUESTS),
                texts.get(language, LandingTextKeys.LIMIT_AI_PER_DAY, aiRateLimitProperties.getPerDay()),
                texts.get(language, LandingTextKeys.LIMIT_AI_PER_DAY, aiRateLimitProperties.getPerDayPremium())));
        for (BotFeature feature : BotFeature.values()) {
            features.add(new FeatureRow(
                    texts.get(language, feature.getTextKey()),
                    describeLimit(free.get(feature), language),
                    describeLimit(premium.get(feature), language)));
        }

        return new LandingPage(toPlanCards(plans, language), features, botUrl(campaignCode));
    }

    private Map<BotFeature, PlanFeature> featuresByPlan(String planCode) {
        Map<BotFeature, PlanFeature> result = new EnumMap<>(BotFeature.class);
        planFeatureService.getFeaturesByPlanCode(planCode, BOT)
                .forEach(planFeature -> result.put(planFeature.getFeature(), planFeature));
        return result;
    }

    String describeLimit(PlanFeature planFeature, AppLanguage language) {
        if (planFeature == null) {
            return NOT_AVAILABLE;
        }
        Integer limit = planFeature.getLimitValue();
        if (planFeature.getLimitPeriod() == FeatureLimitPeriod.UNLIMITED || limit == null || limit <= -1) {
            return texts.get(language, LandingTextKeys.LIMIT_UNLIMITED);
        }
        if (limit == 0) {
            return NOT_AVAILABLE;
        }
        return texts.get(language, switch (planFeature.getLimitPeriod()) {
            case DAILY -> LandingTextKeys.LIMIT_DAILY;
            case MONTHLY -> LandingTextKeys.LIMIT_MONTHLY;
            case LIFETIME, UNLIMITED -> LandingTextKeys.LIMIT_LIFETIME;
        }, limit);
    }

    /**
     * Карточки тарифов. Выгода считается к самому короткому плану в той же валюте:
     * 3 месяца за 350 ⭐ против 3 × 150 ⭐ — «выгода 22%».
     */
    private List<PlanCard> toPlanCards(List<PricingPlan> plans, AppLanguage language) {
        Map<Currency, PricingPlan> shortestByCurrency = new EnumMap<>(Currency.class);
        plans.forEach(plan -> shortestByCurrency.putIfAbsent(plan.getCurrency(), plan));

        return plans.stream()
                .map(plan -> new PlanCard(
                        planTexts.getTitle(plan, language),
                        toPoints(planTexts.getDescription(plan, language)),
                        formatPrice(plan.getPriceMinor(), plan.getCurrency(), language),
                        describeDuration(plan.getDurationDays(), language),
                        savingPercent(plan, shortestByCurrency.get(plan.getCurrency()))))
                .toList();
    }

    /**
     * Описание тарифа из БД — пункты по строкам, обычно «эмодзи + текст». HTML схлопывает переносы,
     * поэтому пункты отдаются списком, а эмодзи отделяется от текста, чтобы встать слева от него.
     */
    static List<PlanPoint> toPoints(String description) {
        if (description == null) {
            return List.of();
        }
        return description.lines()
                .map(String::strip)
                .map(line -> BULLET.matcher(line).replaceFirst(""))
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    Matcher m = LEADING_ICON.matcher(line);
                    return m.matches() && !m.group(2).isBlank()
                            ? new PlanPoint(m.group(1), m.group(2))
                            : new PlanPoint(null, line);
                })
                .toList();
    }

    static String formatPrice(Integer priceMinor, Currency currency, AppLanguage language) {
        if (currency == Currency.XTR) {
            // У Telegram Stars нет дробных единиц: price_minor — это и есть количество звёзд
            return priceMinor + " ⭐";
        }
        BigDecimal rubles = BigDecimal.valueOf(priceMinor).movePointLeft(2).stripTrailingZeros();
        String amount = rubles.toPlainString();
        // Дробная часть — через запятую по-русски, через точку по-английски
        return (language == AppLanguage.RU ? amount.replace('.', ',') : amount) + " ₽";
    }

    /** «1 месяц», «3 месяца», «45 дней» — месяцы, если делится на 30; год (365 дней) — «12 месяцев». */
    String describeDuration(int days, AppLanguage language) {
        if (days % 365 == 0) {
            return texts.get(language, LandingTextKeys.PLAN_DURATION_MONTHS, days / 365 * 12);
        }
        return days % 30 == 0
                ? texts.get(language, LandingTextKeys.PLAN_DURATION_MONTHS, days / 30)
                : texts.get(language, LandingTextKeys.PLAN_DURATION_DAYS, days);
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

    private String botUrl(String campaignCode) {
        String url = "https://t.me/" + BOT.getBotUsername().replace("@", "");
        String code = campaignCode != null ? campaignCode : startCode;
        return code == null || code.isBlank() ? url : url + "?start=" + code;
    }

    public record LandingPage(List<PlanCard> plans, List<FeatureRow> features, String botUrl) { }

    /** saving — выгода в процентах к самому короткому плану, null — без выгоды. */
    public record PlanCard(String title, List<PlanPoint> points, String price, String duration, Integer saving) { }

    /** Пункт описания тарифа; icon — эмодзи в начале строки или null. */
    public record PlanPoint(String icon, String text) { }

    public record FeatureRow(String name, String free, String premium) { }
}
