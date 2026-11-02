package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    // @Transactional здесь: вызываются и из шедулеров вне транзакции, а @Modifying без неё падает
    @Transactional
    @Modifying
    @Query("update UserSettings s set s.dailyLastReminder = :now where s.userId = :userId")
    int updateDailyLastReminder(@Param("userId") Long userId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("update UserSettings s set s.mealLastReminderUtc = :now where s.userId = :userId")
    int updateMealLastReminder(@Param("userId") Long userId, @Param("now") Instant now);
    List<UserSettings> findAllByDailySummaryEnabled(boolean enabled);

    /**
     * Занять право предложить пригласить друга: только если пользователь с нами дольше joinedBefore и последнее
     * предложение было раньше promptedBefore (или не было). Проверка и отметка — одним UPDATE: два параллельных
     * отчёта не предложат дважды.
     *
     * @return 1 — можно предлагать (время отмечено), 0 — рано
     */
    @Transactional
    @Modifying
    @Query("""
            update UserSettings s set s.invitePromptLastAt = :now
            where s.userId = :userId and s.createdAt < :joinedBefore
              and (s.invitePromptLastAt is null or s.invitePromptLastAt < :promptedBefore)
            """)
    int claimInvitePrompt(@Param("userId") Long userId,
                          @Param("now") Instant now,
                          @Param("joinedBefore") Instant joinedBefore,
                          @Param("promptedBefore") Instant promptedBefore);

    /**
     * Первое предложение пригласить (старт-флоу): только если ещё ни разу не предлагали — «Далее» есть в каждом /start,
     * и без этого старые пользователи получали бы его заново. Следующее — не раньше чем через неделю.
     *
     * @return 1 — первое (время отмечено), 0 — уже предлагали
     */
    @Transactional
    @Modifying
    @Query("update UserSettings s set s.invitePromptLastAt = :now where s.userId = :userId and s.invitePromptLastAt is null")
    int claimFirstInvitePrompt(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
                SELECT DISTINCT us FROM UserSettings us
                JOIN TelegramUser tu 
                    ON us.userId = tu.appUserId
                WHERE us.dailySummaryEnabled = :isEnabled
                  AND tu.isBotBlocked = false
                  AND tu.botIdentifier = :botIdentifier
            """)
    List<UserSettings> findActiveSettingsForEnabledDailySummary(
            @Param("isEnabled") boolean isEnabled,
            @Param("botIdentifier") BotIdentifier botIdentifier
    );

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

    /**
     * Находит настройки пользователей, которым пришло время отправить напоминание о приеме пищи.
     * <p>
     * Пользователь считается готовым к напоминанию, если выполняются ВСЕ условия:
     * 1. В экосистеме включены общие уведомления (reminders_enabled = true).
     * 2. Включены конкретно напоминания о еде (meal_reminder_enabled = true).
     * 3. Напоминание еще ни разу не отправлялось (meal_last_reminder_utc IS NULL)
     * ИЛИ с момента последней отправки прошло больше минут, чем задано в интервале пользователя.
     * 4. Бот не заблокирован пользователем (is_bot_blocked = false) для конкретного botIdentifier.
     * <p>
     * <b>Важные технические особенности:</b>
     * <ul>
     * <li>Используется <b>Native Query</b>, так как арифметика дат (EXTRACT EPOCH) специфична для PostgreSQL
     * и позволяет вычислять динамические интервалы пользователя на стороне БД.</li>
     * <li>Связь с таблицей {@code telegram_users} вынесена в подзапрос {@code IN} вместо {@code JOIN}.
     * Это предотвращает конфликт дублирующихся колонок 'id' (org.postgresql.util.PSQLException:
     * column reference "id" is ambiguous), возникающий при маппинге результатов Hibernate.</li>
     * <li>Фильтрация идет строго по {@code tu.app_user_id} для сквозной синхронизации данных.</li>
     * </ul>
     *
     * @param now           текущий момент времени (обычно Instant.now())
     * @param botIdentifier идентификатор конкретного Telegram-бота
     * @return список настроек пользователей, готовых к отправке напоминания
     */
    @Query(value = """
            SELECT u.* FROM user_settings u
            WHERE u.reminders_enabled = true
              AND u.meal_reminder_enabled = true
              AND (
                  u.meal_last_reminder_utc IS NULL
                  OR
                  EXTRACT(EPOCH FROM (:now - u.meal_last_reminder_utc)) / 60 
                      >= u.meal_reminder_interval_minutes
              )
              AND u.user_id IN (
                  SELECT tu.app_user_id 
                  FROM telegram_users tu 
                  WHERE tu.is_bot_blocked = false 
                    AND tu.bot_identifier = :botIdentifier
              )
        """, nativeQuery = true)
    List<UserSettings> findAllUsersReadyForMealReminder(
            @Param("now") Instant now,
            @Param("botIdentifier") String botIdentifier
    );
}
