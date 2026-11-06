package com.kuklin.manageapp.bots.caloriebot.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalorieAiInsightRepository extends JpaRepository<CalorieAiInsight, Long> {

    // На (пользователь, тип) не больше одной записи — уникальный индекс
    Optional<CalorieAiInsight> findByAppUserIdAndType(Long appUserId, AiInsightType type);

    // Все инсайты пользователя — не больше одного на тип
    List<CalorieAiInsight> findAllByAppUserId(Long appUserId);
}
