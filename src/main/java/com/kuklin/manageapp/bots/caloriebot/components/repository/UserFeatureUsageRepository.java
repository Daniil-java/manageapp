package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import  com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface UserFeatureUsageRepository extends JpaRepository<UserFeatureUsage, Long> {

    Optional<UserFeatureUsage> findByUserIdAndFeatureAndAndBotIdentifier(Long userId, BotFeature botFeature, BotIdentifier botIdentifier);

    /**
     * Списывает одну попытку, только если лимит ещё не исчерпан. Проверка и списание — одной командой,
     * поэтому параллельные запросы не проскочат лимит.
     *
     * @return 1 — попытка списана, 0 — лимит исчерпан (или записи нет)
     */
    @Modifying(flushAutomatically = true)
    @Query("""
    update UserFeatureUsage u
    set u.usedCount = u.usedCount + 1
    where u.userId = :userId
      and u.botIdentifier = :botIdentifier
      and u.feature = :feature
      and u.usedCount < :limit
""")
    int tryIncrementUsage(
            @Param("userId") Long userId,
            @Param("botIdentifier") BotIdentifier botIdentifier,
            @Param("feature") BotFeature feature,
            @Param("limit") int limit
    );

    /**
     * Возвращает одну попытку (счётчик не уходит ниже нуля).
     */
    @Modifying
    @Query("""
    update UserFeatureUsage u
    set u.usedCount = u.usedCount - 1
    where u.userId = :userId
      and u.botIdentifier = :botIdentifier
      and u.feature = :feature
      and u.usedCount > 0
""")
    int decrementUsage(
            @Param("userId") Long userId,
            @Param("botIdentifier") BotIdentifier botIdentifier,
            @Param("feature") BotFeature feature
    );

    /**
     * Отмечает, что сегодня пустой ответ ИИ уже простили.
     *
     * @return 1 — сегодня ещё не прощали (отметка поставлена), 0 — уже прощали
     */
    @Modifying
    @Query("""
    update UserFeatureUsage u
    set u.lastGraceLocalDate = :today
    where u.userId = :userId
      and u.botIdentifier = :botIdentifier
      and u.feature = :feature
      and (u.lastGraceLocalDate is null or u.lastGraceLocalDate <> :today)
""")
    int markGraceUsed(
            @Param("userId") Long userId,
            @Param("botIdentifier") BotIdentifier botIdentifier,
            @Param("feature") BotFeature feature,
            @Param("today") LocalDate today
    );
}
