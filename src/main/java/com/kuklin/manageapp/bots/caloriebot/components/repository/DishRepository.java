package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findAllByUserId(Long userId);
    List<Dish> findAllByUserIdAndCreatedBetween(Long userId, Instant start, Instant end);

    @Query("""
        select d.created
        from Dish d
        where d.userId = :userId
          and d.created >= :start
          and d.created < :end
    """)
    List<Instant> findCreatedByUserIdAndCreatedBetween(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

}

