package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PostQueueRepository extends JpaRepository<PostQueue, Long> {
    // Поиск постов, готовых к отправке
    List<PostQueue> findAllByStatusAndScheduledAtBefore(PostQueue.PostQueueStatus status, Instant now);

    // Поиск по родительскому посту (для тредов/цепочек)
    List<PostQueue> findAllByParentPostId(Integer parentPostId);

    @Query("""
                SELECT pq FROM PostQueue pq 
                WHERE pq.status IN :statuses 
                AND pq.scheduledAt >= :startOfDay 
                AND pq.scheduledAt <= :endOfDay
            """)
    List<PostQueue> findPlannedAndSentFor(
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay,
            @Param("statuses") List<PostQueue.PostQueueStatus> statuses
    );

    List<PostQueue> findAllByStatus(PostQueue.PostQueueStatus status);
}
