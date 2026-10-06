package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {
    List<WeightEntry> findAllByUserIdOrderByEntryDateAsc(Long userId);
    List<WeightEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
            Long userId, LocalDate startDate, LocalDate endDate
    );

    void deleteByIdAndUserId(Long id, Long userId);

    // Последнее взвешивание: по дате записи, внутри дня — по времени создания
    Optional<WeightEntry> findFirstByUserIdOrderByEntryDateDescCreatedAtDescIdDesc(Long userId);

    List<WeightEntry> findAllByUserIdAndCreatedAtBetween(Long userId, Instant start, Instant end);
}
