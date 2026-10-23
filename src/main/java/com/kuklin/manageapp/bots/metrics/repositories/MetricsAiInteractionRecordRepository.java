package com.kuklin.manageapp.bots.metrics.repositories;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.bots.metrics.models.AiUsageByUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MetricsAiInteractionRecordRepository extends JpaRepository<MetricsAiInteractionRecord, Long> {
    List<MetricsAiInteractionRecord> findByCreatedBetween(Instant from, Instant to);

    /** Вызовы ИИ с момента from по пользователям, больше всего вызовов — первым. */
    @Query("""
            select new com.kuklin.manageapp.bots.metrics.models.AiUsageByUser(
                r.appUserId,
                count(r),
                coalesce(sum(coalesce(r.inputTokens, 0L) + coalesce(r.outputTokens, 0L)), 0L),
                sum(r.costUsd))
            from MetricsAiInteractionRecord r
            where r.created >= :from
            group by r.appUserId
            order by count(r) desc
            """)
    List<AiUsageByUser> findUsageByUserSince(@Param("from") Instant from);

}
