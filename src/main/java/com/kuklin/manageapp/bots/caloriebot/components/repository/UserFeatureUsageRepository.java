package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import  com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFeatureUsageRepository extends JpaRepository<UserFeatureUsage, Long> {

    Optional<UserFeatureUsage> findByUserIdAndFeatureAndAndBotIdentifier(Long userId, BotFeature botFeature, BotIdentifier botIdentifier);

    @Modifying
    @Query("""
    update UserFeatureUsage u
    set u.usedCount = u.usedCount + 1
    where u.userId = :userId
      and u.botIdentifier = :botIdentifier
      and u.feature = :feature
""")
    int incrementUsage(
            @Param("userId") Long userId,
            @Param("botIdentifier") BotIdentifier botIdentifier,
            @Param("feature") BotFeature feature
    );
}
