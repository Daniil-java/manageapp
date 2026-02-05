package com.kuklin.manageapp.bots.caloriebot.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserNutritionProfileEntryRepository extends JpaRepository<UserNutritionProfileEntry, Long> {

    Optional<UserNutritionProfileEntry> findByUserIdAndValidToIsNull(Long userId);
    List<UserNutritionProfileEntry> findAllByUserId(Long userId);

}
