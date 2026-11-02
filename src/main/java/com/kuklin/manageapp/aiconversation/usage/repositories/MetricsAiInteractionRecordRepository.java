package com.kuklin.manageapp.aiconversation.usage.repositories;

import com.kuklin.manageapp.aiconversation.usage.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.aiconversation.usage.models.AiUsageByUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetricsAiInteractionRecordRepository extends JpaRepository<MetricsAiInteractionRecord, Long> {
    List<MetricsAiInteractionRecord> findByCreatedBetween(Instant from, Instant to);

    // Вызовы ИИ пользователя с момента from — для заявки в поддержку: успешные, упавшие и последний упавший
    long countByAppUserIdAndCreatedAfterAndErrorCodeIsNull(Long appUserId, Instant from);

    long countByAppUserIdAndCreatedAfterAndErrorCodeIsNotNull(Long appUserId, Instant from);

    Optional<MetricsAiInteractionRecord> findFirstByAppUserIdAndCreatedAfterAndErrorCodeIsNotNullOrderByCreatedDesc(
            Long appUserId, Instant from);

    /** Успешные вызовы ИИ с момента from по пользователям, больше всего вызовов — первым. Упавшие (error_code) не считаем. */
    @Query("""
            select new com.kuklin.manageapp.aiconversation.usage.models.AiUsageByUser(
                r.appUserId,
                count(r),
                coalesce(sum(coalesce(r.inputTokens, 0L) + coalesce(r.outputTokens, 0L)), 0L),
                sum(r.costUsd))
            from MetricsAiInteractionRecord r
            where r.created >= :from and r.errorCode is null
            group by r.appUserId
            order by count(r) desc
            """)
    List<AiUsageByUser> findUsageByUserSince(@Param("from") Instant from);

}
