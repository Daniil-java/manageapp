package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.components.repository.PlanFeatureRepository;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
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

    public List<PlanFeature> getFeaturesByPlanCode(String planCode, BotIdentifier botIdentifier) {
        return planFeatureRepository.findAllByPlanCodeAndBotIdentifier(planCode, botIdentifier);
    }

    //Препдолагается, что у пользователя только одна активная подписка
    public PlanFeature getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(Long userId,
                                                                          BotIdentifier botIdentifier,
                                                                          BotFeature feature) {
        String planCode = commonPaymentFacade.getActivePlanCodeByUserIdOrNull(userId, botIdentifier);
        if (planCode == null) planCode = FREE_PLAN_CODE;

        Optional<PlanFeature> optionalPlanFeature =  planFeatureRepository
                .findAllByPlanCodeAndBotIdentifier(planCode, botIdentifier)
                .stream()
                .filter(pf -> pf.getFeature().equals(feature))
                .findFirst();

        return optionalPlanFeature.orElse(null);
    }
}
