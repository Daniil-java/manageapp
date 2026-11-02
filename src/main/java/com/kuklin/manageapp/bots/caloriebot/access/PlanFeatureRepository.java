package com.kuklin.manageapp.bots.caloriebot.access;

import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    List<PlanFeature> findAllByPlanCodeAndBotIdentifier(String planCode, BotIdentifier botIdentifier);
}
