package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findAllByUserId(Long userId);

    /** Блюдо, если оно принадлежит пользователю. */
    Optional<Dish> findByIdAndUserId(Long id, Long userId);

    List<Dish> findAllByUserIdAndCreatedBetween(Long userId, Instant start, Instant end);

    /** Сколько блюд пользователь добавил после момента after — для суточного лимита (calorie.dish-limits). */
    long countByUserIdAndCreatedAfter(Long userId, Instant after);

    /** Время последнего добавленного блюда; null — блюд нет. Для вечернего отчёта и напоминаний о еде. */
    @Query("select max(d.created) from Dish d where d.userId = :userId")
    Instant findLastCreatedByUserId(@Param("userId") Long userId);

    /** Время всех блюд пользователя — для серии дней (лучшая серия считается за всю историю). */
    @Query("select d.created from Dish d where d.userId = :userId and d.created is not null")
    List<Instant> findAllCreatedByUserId(@Param("userId") Long userId);

}

