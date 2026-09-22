package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findAllByUserId(Long userId);
    List<Dish> findAllByUserIdAndCreatedBetween(Long userId, Instant start, Instant end);

    @Query("""
        select distinct function('date', d.created)
        from Dish d
        where d.userId = :userId
          and d.created between :start and :end
        order by function('date', d.created) desc
    """)
    List<LocalDate> findDistinctDaysByUserIdAndCreatedBetween(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

}

