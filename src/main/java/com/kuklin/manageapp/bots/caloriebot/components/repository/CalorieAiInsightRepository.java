package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiInsight;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CalorieAiInsightRepository extends JpaRepository<CalorieAiInsight, Long> {

    Optional<CalorieAiInsight> findFirstByAppUserIdAndTypeOrderByCreatedAtDesc(Long appUserId, AiInsightType type);
}
