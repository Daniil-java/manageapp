package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserNutritionProfileEntryRepository extends JpaRepository<UserNutritionProfileEntry, Long> {

    Optional<UserNutritionProfileEntry> findByUserIdAndValidToIsNull(Long userId);
    List<UserNutritionProfileEntry> findAllByUserId(Long userId);

    /** Нормы, действовавшие хотя бы в один момент from–to (включительно). */
    @Query("select e from UserNutritionProfileEntry e where e.userId = :userId"
            + " and e.validFrom <= :to and (e.validTo is null or e.validTo > :from)")
    List<UserNutritionProfileEntry> findAllActiveBetween(@Param("userId") Long userId,
                                                         @Param("from") Instant from,
                                                         @Param("to") Instant to);

}
