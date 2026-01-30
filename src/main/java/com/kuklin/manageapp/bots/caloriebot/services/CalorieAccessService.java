package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.*;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис проверки доступа к функционалу бота (Features) на основе тарифных планов и лимитов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalorieAccessService {
    private static final Long RESPONSE_LIMIT = 10L;

    private final CommonPaymentFacade commonPaymentFacade;
    private final TelegramUserService telegramUserService;
    private final PlanFeatureService planFeatureService;
    private final UserFeatureUsageService userFeatureUsageService;
    /** Код тарифа по умолчанию, если у пользователя нет активной подписки */
    private static final String FREE_PLAN_CODE = "FREE";

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
    public boolean hasAccess(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        // 1. Определяем текущий тарифный план пользователя
        // Если подписки нет в БД, автоматически считаем его на плане FREE
        String planCode = commonPaymentFacade.getActivePlanCodeByUserIdOrNull(userId, botIdentifier);
        planCode = (planCode == null) ? FREE_PLAN_CODE : planCode;

        // 2. Загружаем конфигурацию фич для данного плана из кэша/БД
        List<PlanFeature> planFeatures = planFeatureService
                .getFeaturesByPlanCode(planCode, botIdentifier);

        // 3. Ищем настройки запрашиваемой фичи в текущем плане
        PlanFeature config = planFeatures.stream()
                .filter(f -> f.getFeature().equals(feature))
                .findFirst()
                .orElse(null);

        // Если фича не описана для плана, значит доступ к ней по умолчанию закрыт
        if (config == null) {
            log.warn("Feature {} not found for plan {} and user {}", feature, planCode, userId);
            return false;
        }

        // 4. Проверка на "Безлимитный доступ"
        // Доступ разрешен без проверки счетчиков, если период UNLIMITED или значение лимита отрицательное
        if (config.getLimitPeriod().equals(FeatureLimitPeriod.UNLIMITED) || config.getLimitValue() == null || config.getLimitValue() <= -1 ) {
            return true;
        }

        // 5. Проверка количественных лимитов (DAILY/LIFETIME и т.д.)
        // Получаем текущую статистику использования.
        // ВАЖНО: Внутри метода getUserFeatureUsage... заложена логика сброса счетчика
        // при наступлении нового периода (например, нового дня для DAILY лимитов).
        UserFeatureUsage usage = userFeatureUsageService
                .getUserFeatureUsageByUserIdAndBotIdentifierAndBotFeatureOrCreate(
                        userId, botIdentifier, feature, config.getLimitPeriod()
                );

        // Разрешаем, если количество использований строго меньше установленного лимита
        boolean canProceed = usage.getUsedCount() < config.getLimitValue();

        if (!canProceed) {
            log.info("User {} exhausted limit for feature {}: {}/{}",
                    userId, feature, usage.getUsedCount(), config.getLimitValue());
        }

        return canProceed;
    }
}
