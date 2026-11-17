--liquibase formatted sql
--changeset DanielK:62

-- ════════════════════════════════════════════════════════
--  ДНЕВНОЙ ЛИМИТ ФИЧ: ОТСЧЁТ 18 ЧАСОВ ОТ ПОЛУНОЧИ, А НЕ ОТ МОМЕНТА СБРОСА
--  last_reset_utc хранил момент сброса — первое обращение за день. После первого фото в 23:00
--  лимит назавтра не обнулялся до 17:00 (UserFeatureUsageService.shouldReset ждёт 18 часов).
--  Теперь там полночь дня сброса по таймзоне пользователя, в UTC. Старые строки переводим так же —
--  иначе у них ошибка повторилась бы ещё один раз. Время только уменьшаем, вперёд не сдвигаем.
--  Таймзона, которой Postgres не знает (настройки до валидации), — как по умолчанию: иначе миграция упадёт.
-- ════════════════════════════════════════════════════════

WITH midnight AS (
    SELECT u.id,
           (u.last_reset_local_date::timestamp AT TIME ZONE
               COALESCE((SELECT tz.name FROM pg_timezone_names tz
                         JOIN user_settings s ON s.timezone_id = tz.name
                         WHERE s.user_id = u.user_id), 'Europe/Moscow'))
               AT TIME ZONE 'UTC' AS reset_utc
    FROM user_feature_usage u
    WHERE u.last_reset_local_date IS NOT NULL
      AND u.last_reset_utc IS NOT NULL
)
UPDATE user_feature_usage u
SET last_reset_utc = m.reset_utc
FROM midnight m
WHERE u.id = m.id
  AND m.reset_utc < u.last_reset_utc;

-- rollback SELECT 1;
