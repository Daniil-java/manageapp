package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    /** Код тарифа по умолчанию, если у пользователя нет активной подписки */
    public static final String FREE_PLAN_CODE = "FREE";

    /**
     * Увеличивает счетчик общего количества ответов бота пользователю.
     */
    public void incrementResponses(TelegramUser user) {
        telegramUserService.save(
                user.setResponseCount(user.getResponseCount() + 1)
        );
    }

    /**
     * Основной метод проверки доступа пользователя к конкретной функции.
     * * @param userId ID пользователя в Telegram
     * @param botIdentifier Идентификатор бота (для мультибота)
     * @param feature Тип функции, к которой запрашивается доступ
     * @return true, если доступ разрешен (лимит не исчерпан или безлимит)
     */
    public boolean hasAccess(Long appUserId, BotIdentifier botIdentifier, BotFeature feature) {
        // 1. Ищем настройки запрашиваемой фичи в текущем плане
        PlanFeature config = planFeatureService
                .getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(
                        appUserId, botIdentifier, feature);

        // Если фича не описана для плана, значит доступ к ней по умолчанию закрыт
        if (config == null) {
            log.warn("Feature {} not found! userId {}", feature, appUserId);
            return false;
        }

        // 2. Проверка на "Безлимитный доступ"
        // Доступ разрешен без проверки счетчиков, если период UNLIMITED или значение лимита отрицательное
        if (config.getLimitPeriod().equals(FeatureLimitPeriod.UNLIMITED) || config.getLimitValue() == null || config.getLimitValue() <= -1 ) {
            return true;
        }

        // 3. Проверка количественных лимитов (DAILY/LIFETIME и т.д.)
        // Получаем текущую статистику использования.
        // ВАЖНО: Внутри метода getUserFeatureUsage... заложена логика сброса счетчика
        // при наступлении нового периода (например, нового дня для DAILY лимитов).
        UserFeatureUsage usage = userFeatureUsageService
                .getUserFeatureUsageByUserIdAndBotIdentifierAndBotFeatureOrCreate(
                        appUserId, botIdentifier, feature, config.getLimitPeriod()
                );

        // Разрешаем, если количество использований строго меньше установленного лимита
        boolean canProceed = usage.getUsedCount() < config.getLimitValue();

        if (!canProceed) {
            log.info("User {} exhausted limit for feature {}: {}/{}",
                    appUserId, feature, usage.getUsedCount(), config.getLimitValue());
        }

        return canProceed;
    }

    public int getRemainingLimits(Long appUserId, BotFeature feature) {
        // 1. Получаем настройки лимита для пользователя из его тарифа
        PlanFeature planFeature = planFeatureService
                .getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(appUserId, BotIdentifier.CALORIE_BOT, feature);

        if (planFeature != null || planFeature.getFeature().equals(FeatureLimitPeriod.UNLIMITED)) {
            return -1;
        }
        if (planFeature == null || planFeature.getLimitValue() == null) {
            return 0; // Или -1 для безлимита
        }

        // 2. Получаем текущее использование (с учетом сброса периода, например, за день)
        UserFeatureUsage usage = userFeatureUsageService
                .getUserFeatureUsageByUserIdAndBotIdentifierAndBotFeatureOrCreate(
                        appUserId, BotIdentifier.CALORIE_BOT, feature, planFeature.getLimitPeriod()
                );

        // 3. Считаем остаток
        int remaining = planFeature.getLimitValue() - usage.getUsedCount();
        return Math.max(0, remaining);
    }
}
