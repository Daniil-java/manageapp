package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.components.repository.PlanFeatureRepository;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService.FREE_PLAN_CODE;

@Component
@RequiredArgsConstructor
public class PlanFeatureService {
    private final PlanFeatureRepository planFeatureRepository;
    private final CommonPaymentFacade commonPaymentFacade;
    private final TelegramUserService telegramUserService;

    public List<PlanFeature> getFeaturesByPlanCode(String planCode, BotIdentifier botIdentifier) {
        return planFeatureRepository.findAllByPlanCodeAndBotIdentifier(planCode, botIdentifier);
    }

    /**
     * Все настройки фич текущего тарифа пользователя (активная подписка или FREE).
     * Предполагается, что у пользователя только одна активная подписка.
     */
    public List<PlanFeature> getFeaturesByUserIdAndBotIdentifier(Long appUserId, BotIdentifier botIdentifier) {
        String planCode = FREE_PLAN_CODE;
        String activePlan = commonPaymentFacade.getActivePlanCodeByUserIdOrNull(appUserId, botIdentifier);
        if (activePlan != null) {
            planCode = activePlan;
        }

        return planFeatureRepository.findAllByPlanCodeAndBotIdentifier(planCode, botIdentifier);
    }

    public PlanFeature getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(Long appUserId,
                                                                          BotIdentifier botIdentifier,
                                                                          BotFeature feature) {
        Optional<PlanFeature> optionalPlanFeature = getFeaturesByUserIdAndBotIdentifier(appUserId, botIdentifier)
                .stream()
                .filter(pf -> pf.getFeature().equals(feature))
                .findFirst();

        return optionalPlanFeature.orElse(null);
    }
}
