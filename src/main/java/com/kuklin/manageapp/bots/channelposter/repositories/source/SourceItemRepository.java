package com.kuklin.manageapp.bots.channelposter.repositories.source;

import com.kuklin.manageapp.bots.channelposter.entities.source.SourceItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface SourceItemRepository extends JpaRepository<SourceItem, Long> {

    @Query("SELECT i.externalId FROM SourceItem i WHERE i.externalId IN :ids")
    List<String> findExistingExternalIds(@Param("ids") Collection<String> ids);

    // для AI-фильтра: сначала самые свежие
    List<SourceItem> findAllByStatusOrderByPublishedAtDesc(SourceItem.Status status, Pageable pageable);

    // для генерации: сначала лучшие по оценке AI, потом свежие
    List<SourceItem> findAllByStatusOrderByAiScoreDescPublishedAtDesc(SourceItem.Status status, Pageable pageable);

    long countByStatus(SourceItem.Status status);

    long countByParsedAtAfter(Instant after);

    long countByStatusAndParsedAtAfter(SourceItem.Status status, Instant after);

    // устаревшие материалы — не тратим на них AI
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SourceItem i SET i.status = :to, i.aiReason = :reason
            WHERE i.status = :from AND COALESCE(i.publishedAt, i.parsedAt) < :before
            """)
    int expire(@Param("from") SourceItem.Status from,
               @Param("to") SourceItem.Status to,
               @Param("before") Instant before,
               @Param("reason") String reason);

    // статистика по источникам: [sourceId, status, count] за период
    @Query("""
            SELECT i.sourceId, i.status, COUNT(i) FROM SourceItem i
            WHERE i.parsedAt >= :after
            GROUP BY i.sourceId, i.status
            """)
    List<Object[]> countBySourceAndStatusAfter(@Param("after") Instant after);
}
