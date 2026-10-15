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

    /**
     * Дни с блюдами (номер дня от 1970-01-01) в таймзоне пользователя — для серии дней. Лучшая серия считается
     * за всю историю, поэтому без окна, но по строке на день, а не на блюдо.
     *
     * @param zoneName      имя зоны IANA или "UTC"
     * @param offsetSeconds фиксированное смещение поверх zoneName: смещение вида «+03:00» Postgres читает
     *                      в POSIX-смысле, с обратным знаком, поэтому его передаём числом
     */
    @Query(value = """
            select distinct cast((d.created at time zone :zoneName) + make_interval(secs => :offsetSeconds) as date)
                   - date '1970-01-01'
            from dishes d
            where d.user_id = :userId and d.created is not null
            """, nativeQuery = true)
    List<Integer> findDishEpochDaysByUserId(@Param("userId") Long userId,
                                            @Param("zoneName") String zoneName,
                                            @Param("offsetSeconds") int offsetSeconds);

}

