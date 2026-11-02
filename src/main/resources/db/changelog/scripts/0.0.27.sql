--liquibase formatted sql

--changeset DanielK:27

-- 1. Создаем/обновляем таблицу настроек пользователя
CREATE TABLE IF NOT EXISTS user_settings (
                                             user_id                           BIGINT PRIMARY KEY,
                                             timezone_id                       TEXT        NOT NULL DEFAULT 'UTC',  -- Например, 'Europe/Moscow'

    -- Ежедневный отчет
                                             daily_summary_enabled             BOOLEAN     NOT NULL DEFAULT TRUE,
                                             daily_summary_hour                INTEGER     NOT NULL DEFAULT 21,    -- час локального времени (0-23)
                                             daily_last_reminder               TIMESTAMPTZ NULL,                   -- когда последний раз отправляли daily summary

    -- Напоминание о еде
                                             meal_reminder_enabled             BOOLEAN     NOT NULL DEFAULT TRUE,
                                             meal_reminder_interval_minutes    INTEGER     NOT NULL DEFAULT 180,   -- через сколько минут без еды напоминать
                                             meal_last_reminder_utc            TIMESTAMPTZ NULL,                   -- когда последний раз слали "ты давно не ел"

    -- Общие настройки уведомлений (если хочешь оставить)
                                             reminders_enabled                 BOOLEAN     NOT NULL DEFAULT TRUE,

                                             created_at                        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                        TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Если тебе notifications_hour больше не нужен
-- можно удалить старый столбец, если он существовал раньше:
-- ALTER TABLE user_settings DROP COLUMN IF EXISTS notifications_hour;


-- 2. Изменяем существующие таблицы (переход на TIMESTAMPTZ)

-- Таблица dishes
ALTER TABLE dishes
ALTER COLUMN created TYPE TIMESTAMPTZ USING created AT TIME ZONE 'UTC';

-- Таблица dish_choice_chat_model
ALTER TABLE dish_choice_chat_model
ALTER COLUMN created TYPE TIMESTAMPTZ USING created AT TIME ZONE 'UTC';

-- Таблица user_favorite_dishes
ALTER TABLE user_favorite_dishes
ALTER COLUMN created_at  TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at  TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_used_at TYPE TIMESTAMPTZ USING last_used_at AT TIME ZONE 'UTC';

-- Таблица user_nutrition_profiles
ALTER TABLE user_nutrition_profiles
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Таблица water_entries
ALTER TABLE water_entries
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- Таблица weight_entries
ALTER TABLE weight_entries
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
