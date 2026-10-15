package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Взвешивания для отчёта: с датой взвешивания в fromDate–toDate или внесённые в createdFrom–createdTo (задним числом). */
    @Query("select w from WeightEntry w where w.userId = :userId"
            + " and (w.entryDate between :fromDate and :toDate or w.createdAt between :createdFrom and :createdTo)"
            + " order by w.entryDate asc")
    List<WeightEntry> findAllForReport(@Param("userId") Long userId,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate,
                                       @Param("createdFrom") Instant createdFrom,
                                       @Param("createdTo") Instant createdTo);

    /** Последние взвешивания, от новых к старым. */
    List<WeightEntry> findAllByUserIdAndEntryDateNotNullOrderByEntryDateDesc(Long userId, Limit limit);
}
