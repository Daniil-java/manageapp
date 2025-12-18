package com.kuklin.manageapp.bots.caloriebot.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {
    List<WeightEntry> findAllByUserIdOrderByEntryDateAsc(Long userId);
    List<WeightEntry> findAllByUserIdOrderByEntryDateDesc(Long userId);
    List<WeightEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate startDate, LocalDate now);
}
