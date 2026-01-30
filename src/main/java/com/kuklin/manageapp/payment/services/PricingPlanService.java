package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.repositories.PricingPlanRepository;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingPlanService {
    private final PricingPlanRepository pricingPlanRepository;

    public List<PricingPlan> getAllPlansByBotIdentifier(BotIdentifier botIdentifier) {
        return pricingPlanRepository.findAllByBotIdentifier(botIdentifier);
    }

    public List<PricingPlan> getAllPlansByBotIdentifierAndPlanStatusAvailable(BotIdentifier botIdentifier) {
        return pricingPlanRepository.findAllByBotIdentifierAndPlanStatus(botIdentifier, PricingPlan.PlanStatus.AVAILABLE);
    }

    public PricingPlan getPricingPlanById(Long id) throws PricingPlanNotFoundException {
        return pricingPlanRepository.findById(id)
                .orElseThrow(PricingPlanNotFoundException::new);
    }
}
