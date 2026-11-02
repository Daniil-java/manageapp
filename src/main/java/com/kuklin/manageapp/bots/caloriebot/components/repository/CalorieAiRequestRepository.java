package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CalorieAiRequestRepository extends JpaRepository<CalorieAiRequest, Long> {

    /**
     * Блокирует обращения к ИИ одного пользователя до конца транзакции: параллельные запросы этого пользователя
     * (в том числе с другого экземпляра бэка) ждут, пока текущий не посчитает лимит и не запишет обращение.
     * Разные пользователи друг друга не ждут. Первое число — «пространство» блокировок этой таблицы,
     * чтобы не пересечься с другими advisory-блокировками по тем же id.
     */
    @Query(value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtext('calorie_ai_request'), CAST(:userId % 2147483647 AS integer))
            """, nativeQuery = true)
    Integer lockUserRequests(@Param("userId") Long appUserId);

    // Сколько обращений позже момента since (since не включается)
    long countByAppUserIdAndCreatedAtAfter(Long appUserId, Instant since);

    // Обращения позже since, сначала свежие; страница из одной записи — n-е обращение с конца
    List<CalorieAiRequest> findByAppUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long appUserId, Instant since,
                                                                               Pageable pageable);

    @Modifying
    @Query("delete from CalorieAiRequest r where r.createdAt < :border")
    int deleteCreatedBefore(@Param("border") Instant border);
}
