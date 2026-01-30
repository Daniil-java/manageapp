package com.kuklin.manageapp.bots.caloriebot.featurerestrictions;

import com.kuklin.manageapp.bots.caloriebot.repository.PlanFeatureRepository;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
@RequiredArgsConstructor
public class PlanFeatureService {
    private final PlanFeatureRepository planFeatureRepository;

    public List<PlanFeature> getFeaturesByPlanCode(String planCode, BotIdentifier botIdentifier) {
        return planFeatureRepository.findAllByPlanCodeAndBotIdentifier(planCode, botIdentifier);
    }
}
