package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNutritionProfileRepository extends JpaRepository<UserNutritionProfile, Long> {

    Optional<UserNutritionProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Создаёт пустой профиль, если его ещё нет. При параллельном создании проигравший не получает
     * ошибку (ON CONFLICT DO NOTHING), поэтому транзакция вызывающего кода остаётся рабочей.
     * norm_mode, water_mode, created_at, updated_at — DEFAULT из схемы (AUTO, now()).
     *
     * @return 1 — создали, 0 — уже был
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_nutrition_profiles (user_id, water_target_ml_per_day)
            VALUES (:userId, :waterMl)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("waterMl") Integer waterMl);
}
