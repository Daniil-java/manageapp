package com.kuklin.manageapp.bots.metrics.repositories;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MetricsAiLogRepository extends JpaRepository<MetricsAiLog, Long> {

    Optional<MetricsAiLog> findByDate(LocalDate date);
}
