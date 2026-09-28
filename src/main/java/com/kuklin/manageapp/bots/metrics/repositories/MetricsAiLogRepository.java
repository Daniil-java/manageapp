package com.kuklin.manageapp.bots.metrics.repositories;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MetricsAiLogRepository extends JpaRepository<MetricsAiLog, Long> {

    Optional<MetricsAiLog> findByDate(LocalDate date);
}
