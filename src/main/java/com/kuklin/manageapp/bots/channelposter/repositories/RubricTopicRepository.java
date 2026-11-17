package com.kuklin.manageapp.bots.channelposter.repositories;

import com.kuklin.manageapp.bots.channelposter.entities.RubricTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RubricTopicRepository extends JpaRepository<RubricTopic, Long> {

    List<RubricTopic> findAllByCategoryId(Long categoryId);

    List<RubricTopic> findAllByCategoryIdAndStatusOrderByIdAsc(Long categoryId, RubricTopic.Status status);

    long countByCategoryIdAndStatus(Long categoryId, RubricTopic.Status status);

    // темы, использованные давно, снова в ходу — когда новые кончились
    @Modifying
    @Query("""
            UPDATE RubricTopic t SET t.status = :fresh
            WHERE t.categoryId = :categoryId AND t.status = :used AND t.usedAt < :before
            """)
    int updateStatusUsedBefore(@Param("categoryId") Long categoryId,
                               @Param("before") Instant before,
                               @Param("used") RubricTopic.Status used,
                               @Param("fresh") RubricTopic.Status fresh);
}
