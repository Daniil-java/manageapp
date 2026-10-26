package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiInsightRefresh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Каждый метод — отдельная короткая транзакция: отметка должна сразу стать видна другим запросам,
 * а не после ответа ИИ.
 */
@Repository
public interface CalorieAiInsightRefreshRepository extends JpaRepository<CalorieAiInsightRefresh, Long> {

    /**
     * Отмечает, что генерация началась, — только если сейчас она не идёт (или зависла дольше stuckBefore)
     * и к ИИ не обращались после cooldownFrom. Проверка и отметка — одной командой, поэтому из двух
     * параллельных нажатий пройдёт только одно. Записи ещё нет — создаётся.
     *
     * @param type AiInsightType.name()
     * @return 1 — генерацию можно начинать, 0 — уже идёт или к ИИ обращались слишком недавно
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO calorie_ai_insight_refresh AS r (user_id, type, started_at)
            VALUES (:userId, :type, :now)
            ON CONFLICT (user_id, type) DO UPDATE SET started_at = EXCLUDED.started_at
            WHERE (r.started_at IS NULL OR r.started_at < :stuckBefore)
              AND (r.ai_called_at IS NULL OR r.ai_called_at <= :cooldownFrom)
            """, nativeQuery = true)
    int tryMarkStarted(@Param("userId") Long appUserId,
                       @Param("type") String type,
                       @Param("now") Instant now,
                       @Param("stuckBefore") Instant stuckBefore,
                       @Param("cooldownFrom") Instant cooldownFrom);

    /** Запоминает обращение к ИИ — от него отсчитывается пауза до следующего обновления. */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE calorie_ai_insight_refresh SET ai_called_at = :now
            WHERE user_id = :userId AND type = :type
            """, nativeQuery = true)
    int markAiCalled(@Param("userId") Long appUserId, @Param("type") String type, @Param("now") Instant now);

    /** Генерация закончилась (успешно или с ошибкой) — можно запускать следующую, когда пройдёт пауза. */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE calorie_ai_insight_refresh SET started_at = NULL
            WHERE user_id = :userId AND type = :type
            """, nativeQuery = true)
    int markFinished(@Param("userId") Long appUserId, @Param("type") String type);
}
