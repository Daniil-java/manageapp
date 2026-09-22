package com.kuklin.manageapp.bots.nicotinebot.components;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SmokingRecordRepository extends JpaRepository<SmokingRecord, Long> {
    List<SmokingRecord> findByUserId(Long userId);

    List<SmokingRecord> findByUserIdAndSmokedAtBetween(
            Long userId,
            Instant from,
            Instant to
    );

    Optional<SmokingRecord> findTopByUserIdOrderBySmokedAtDesc(Long userId);
}
