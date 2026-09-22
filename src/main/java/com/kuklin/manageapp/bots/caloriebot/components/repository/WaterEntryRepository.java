package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaterEntryRepository extends JpaRepository<WaterEntry, Long> {
    Optional<WaterEntry> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<WaterEntry> findAllByUserIdAndEntryDate(Long userId, LocalDate date);

    List<WaterEntry> findAllByUserIdAndCreatedAtBetween(Long tgUserId, Instant toInstant, Instant toInstant1);
}
