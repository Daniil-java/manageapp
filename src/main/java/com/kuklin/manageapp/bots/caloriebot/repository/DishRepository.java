package com.kuklin.manageapp.bots.caloriebot.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findAllByUserId(Long userId);
    List<Dish> findAllByUserIdAndCreatedBetween(Long userId, Instant start, Instant end);

}

