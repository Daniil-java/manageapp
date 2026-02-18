package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
    List<UserSettings> findAllByDailySummaryEnabled(boolean enabled);
    @Query("SELECT us FROM UserSettings us " +
            "JOIN TelegramUser tu ON us.userId = tu.telegramId " + // Предполагаем связь по userId
            "WHERE us.dailySummaryEnabled = :isEnabled " +
            "AND tu.isBotBlocked = false")
    List<UserSettings> findActiveSettingsForEnabledDailySummary(@Param("isEnabled") boolean isEnabled);
    
    List<UserSettings> findAllByMealReminderEnabled(boolean enabled);
    /**
     * Находит пользователей, у которых:
     * 1. Включены напоминания вообще.
     * 2. Включены напоминания о еде.
     * 3. (Сейчас - Последнее_Напоминание) в минутах > Интервала пользователя
     * Native Query (SQL), так как арифметика дат в JPQL сложная и зависит от БД.
     */
//    @Query(value = """
//        SELECT * FROM user_settings u
//        WHERE u.reminders_enabled = true
//          AND u.meal_reminder_enabled = true
//          AND (
//              u.meal_last_reminder_utc IS NULL
//              OR
//              EXTRACT(EPOCH FROM (:now - u.meal_last_reminder_utc)) / 60 >= u.meal_reminder_interval_minutes
//          )
//    """, nativeQuery = true)
//    List<UserSettings> findAllUsersReadyForMealReminder(@Param("now") Instant now);
    @Query(value = """
    SELECT u.* FROM user_settings u 
    -- Присоединяем таблицу пользователей по userId
    JOIN telegram_users tu ON u.user_id = tu.telegram_id 
    WHERE u.reminders_enabled = true 
      AND u.meal_reminder_enabled = true
      -- Проверка, что бот НЕ заблокирован
      AND tu.is_bot_blocked = false 
      AND (
          u.meal_last_reminder_utc IS NULL 
          OR 
          EXTRACT(EPOCH FROM (:now - u.meal_last_reminder_utc)) / 60 >= u.meal_reminder_interval_minutes
      )
""", nativeQuery = true)
    List<UserSettings> findAllUsersReadyForMealReminder(@Param("now") Instant now);
}
