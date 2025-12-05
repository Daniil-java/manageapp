package com.kuklin.manageapp.bots.pomidorotimer.repositories;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimerRepository extends JpaRepository<Timer, Long> {
    Optional<List<Timer>> findTimersByUserEntityIdAndStatusNot(Long userId, TimerStatus status);
    @EntityGraph(attributePaths = {"tasks"})
    Optional<Timer> findById(Long id);

    @Query("SELECT t FROM Timer t WHERE t.stopTime < :currentTime AND t.status = 'RUNNING' AND t.status <> 'COMPLETE'")
    Optional<List<Timer>> findAllExpiredAndNotComplete(@Param("currentTime") LocalDateTime currentTime);

    @Modifying
    @Query("UPDATE Timer t SET t.status = 'PENDING' WHERE t.id IN :ids")
    void updateStatusToPending(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE Timer t SET t.interval = 0 WHERE t.id = :id")
    void resetIntervalById(Long id);

    List<Timer> findTimersByUserEntityIdAndCreatedAfter(Long userId, LocalDateTime localDateTime);

    List<Timer> findAllByUserEntityIdAndStatus(Long userId, TimerStatus status);
}
