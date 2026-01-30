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

    private final CommonPaymentFacade commonPaymentFacade;
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
    public boolean hasAccess(Long userId, BotIdentifier botIdentifier, BotFeature feature) {
        // 1. Ищем настройки запрашиваемой фичи в текущем плане
        PlanFeature config = planFeatureService
                .getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(
                        userId, botIdentifier, feature);

        // Если фича не описана для плана, значит доступ к ней по умолчанию закрыт
        if (config == null) {
            log.warn("Feature {} not found! userId {}", feature, userId);
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
