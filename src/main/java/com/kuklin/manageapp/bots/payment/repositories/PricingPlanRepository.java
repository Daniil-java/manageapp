package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingPlanRepository extends JpaRepository<PricingPlan, Long> {

    Optional<PricingPlan> findPricingPlanByCodeForOrderId(String codeForOrderId);
    List<PricingPlan> findAllByBotIdentifier(BotIdentifier botIdentifier);
}
