package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitDto;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.bots.caloriebot.models.feature.QuotaCharge;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис проверки доступа к функционалу бота (Features) на основе тарифных планов и лимитов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalorieAccessService {
    private static final Long RESPONSE_LIMIT = 10L;

    private final TelegramUserService telegramUserService;
    private final PlanFeatureService planFeatureService;
    private final UserFeatureUsageService userFeatureUsageService;
    private final CalorieTexts texts;
    /** Код тарифа по умолчанию, если у пользователя нет активной подписки */
    public static final String FREE_PLAN_CODE = "FREE";

    /**
     * Увеличивает счетчик общего количества ответов бота пользователю.
     * Пишет только response_count через UPDATE: сохранять весь объект нельзя —
     * устаревшая копия затёрла бы поля, изменённые за время запроса к ИИ (например, isBotBlocked).
     */
    public void incrementResponses(TelegramUser user) {
        telegramUserService.incrementResponseCount(user.getId());
    }

    /**
     * Проверяет доступ пользователя к функции и сразу списывает попытку, если у функции есть лимит.
     * Заменил hasAccess: тот только читал счётчик и сравнивал с лимитом, а списывал аспект отдельно,
     * после вызова ИИ, — параллельные запросы проходили сверх лимита. Теперь проверка лимита и списание —
     * одна атомарная команда в БД ({@link UserFeatureUsageService#tryConsume}).
     * <p>
     * Кто вызывает: {@link com.kuklin.manageapp.bots.caloriebot.components.FeatureAccessAspect} — до вызова
     * метода с @RequiresFeature. Если услуга потом не будет оказана, аспект возвращает попытку
     * через {@link UserFeatureUsageService#refundUsage}.
     *
     * @param appUserId ID пользователя
     * @param botIdentifier Идентификатор бота (для мультибота)
     * @param feature Тип функции, к которой запрашивается доступ
     * @return DENIED — фичи нет в тарифе или лимит исчерпан (метод не вызывать);
     *         UNLIMITED — безлимит, ничего не списано (и возвращать нечего);
     *         CHARGED — попытка списана (вернуть, если услуга не оказана)
     */
    public QuotaCharge tryConsume(Long appUserId, BotIdentifier botIdentifier, BotFeature feature) {
        // 1. Ищем настройки запрашиваемой фичи в текущем плане
        PlanFeature config = planFeatureService
                .getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(
                        appUserId, botIdentifier, feature);

        // Если фича не описана для плана, значит доступ к ней по умолчанию закрыт
        if (config == null) {
            log.warn("Feature {} not found! userId {}", feature, appUserId);
            return QuotaCharge.DENIED;
        }

        // 2. Безлимит (UNLIMITED или limitValue <= -1) — счётчик не ведём, в БД не ходим
        if (isUnlimited(config)) {
            return QuotaCharge.UNLIMITED;
        }

        // 3. Количественный лимит (DAILY/MONTHLY/LIFETIME): списываем попытку, лимит проверяется
        // условием в UPDATE. Сброс счётчика в новом периоде — там же, перед списанием
        // (по таймзоне пользователя, с защитой от её смены).
        boolean charged = userFeatureUsageService.tryConsume(
                appUserId, botIdentifier, feature, config.getLimitPeriod(), config.getLimitValue());

        // UPDATE не обновил строку — used_count уже равен лимиту
        if (!charged) {
            log.info("User {} exhausted limit for feature {}: {}", appUserId, feature, config.getLimitValue());
            return QuotaCharge.DENIED;
        }
        return QuotaCharge.CHARGED;
    }

    public int getRemainingLimits(Long appUserId, BotFeature feature) {
        PlanFeature planFeature = planFeatureService
                .getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(appUserId, BotIdentifier.CALORIE_BOT, feature);
        return getRemaining(planFeature, getUsedCounts(appUserId, planFeature == null ? List.of() : List.of(planFeature)));
    }

    /**
     * Лимиты по всем функциям бота для текущего тарифа пользователя — одним запросом для миниаппки.
     * Тариф определяется один раз, счётчики читаются одним запросом и не создаются (GET ничего не пишет).
     */
    public List<FeatureLimitDto> getAllLimits(Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        List<PlanFeature> features = planFeatureService
                .getFeaturesByUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT);
        Map<BotFeature, PlanFeature> planFeatures = features.stream()
                .collect(Collectors.toMap(PlanFeature::getFeature, Function.identity(), (a, b) -> a));
        Map<BotFeature, Integer> usedCounts = getUsedCounts(appUserId, features);

        return Arrays.stream(BotFeature.values())
                .map(feature -> {
                    PlanFeature planFeature = planFeatures.get(feature);
                    return new FeatureLimitDto(
                            feature,
                            texts.get(language, feature.getTextKey()),
                            isUnlimited(planFeature) ? -1 : (planFeature == null ? 0 : planFeature.getLimitValue()),
                            getRemaining(planFeature, usedCounts),
                            planFeature == null ? null : planFeature.getLimitPeriod()
                    );
                })
                .toList();
    }

    private boolean isUnlimited(PlanFeature planFeature) {
        return planFeature != null
                && (planFeature.getLimitPeriod() == FeatureLimitPeriod.UNLIMITED
                || planFeature.getLimitValue() == null
                || planFeature.getLimitValue() <= -1);
    }

    /** Использовано по фичам с лимитом; у безлимитных счётчик не ведём и в БД за ним не ходим. */
    private Map<BotFeature, Integer> getUsedCounts(Long appUserId, List<PlanFeature> planFeatures) {
        Map<BotFeature, FeatureLimitPeriod> periods = new EnumMap<>(BotFeature.class);
        planFeatures.stream()
                .filter(pf -> !isUnlimited(pf))
                .forEach(pf -> periods.putIfAbsent(pf.getFeature(), pf.getLimitPeriod()));
        if (periods.isEmpty()) return Map.of();
        return userFeatureUsageService.getUsedCounts(appUserId, BotIdentifier.CALORIE_BOT, periods);
    }

    private int getRemaining(PlanFeature planFeature, Map<BotFeature, Integer> usedCounts) {
        if (planFeature == null) {
            return 0; // фича не описана для тарифа — доступа нет
        }
        if (isUnlimited(planFeature)) {
            return -1;
        }
        return Math.max(0, planFeature.getLimitValue() - usedCounts.getOrDefault(planFeature.getFeature(), 0));
    }
}
