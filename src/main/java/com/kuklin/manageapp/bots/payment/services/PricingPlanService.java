package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.repositories.PricingPlanRepository;
import com.kuklin.manageapp.bots.payment.services.exceptions.PricingPlanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PricingPlanService {
    private final PricingPlanRepository pricingPlanRepository;

    public List<PricingPlan> getAllPlans() {
        return pricingPlanRepository.findAll();
    }

    public PricingPlan getPricingPlanById(Long id) throws PricingPlanNotFoundException {
        return pricingPlanRepository.findById(id)
                .orElseThrow(() -> new PricingPlanNotFoundException());
    }
}
