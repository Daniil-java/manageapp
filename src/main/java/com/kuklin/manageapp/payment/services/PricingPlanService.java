package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.repositories.PricingPlanRepository;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
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

    public PricingPlan getPricingPlanById(Long id) throws PricingPlanNotFoundException {
        return pricingPlanRepository.findById(id)
                .orElseThrow(() -> new PricingPlanNotFoundException());
    }
}
