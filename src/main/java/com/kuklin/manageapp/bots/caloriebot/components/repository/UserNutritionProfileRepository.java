package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNutritionProfileRepository extends JpaRepository<UserNutritionProfile, Long> {

    Optional<UserNutritionProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
