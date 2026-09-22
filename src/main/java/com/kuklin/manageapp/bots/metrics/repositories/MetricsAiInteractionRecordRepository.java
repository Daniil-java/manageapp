package com.kuklin.manageapp.bots.metrics.repositories;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MetricsAiInteractionRecordRepository extends JpaRepository<MetricsAiInteractionRecord, Long> {
    List<MetricsAiInteractionRecord> findByCreatedBetween(Instant from, Instant to);

}
