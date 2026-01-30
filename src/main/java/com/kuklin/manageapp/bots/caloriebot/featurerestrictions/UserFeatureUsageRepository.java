package com.kuklin.manageapp.bots.caloriebot.featurerestrictions;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFeatureUsageRepository extends JpaRepository<UserFeatureUsage, Long> {

    Optional<UserFeatureUsage> findByUserIdAndFeatureAndAndBotIdentifier(Long userId, BotFeature botFeature, BotIdentifier botIdentifier);

}
