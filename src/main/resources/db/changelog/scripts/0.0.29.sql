--liquibase formatted sql

--changeset DanielK:29

-- Таблица истории целей профиля питания
CREATE TABLE IF NOT EXISTS user_nutrition_profile_entries (
                                                              id BIGSERIAL PRIMARY KEY,

                                                              user_id BIGINT NOT NULL,

    -- Цели / таргеты
                                                              goal TEXT,
                                                              calories_norm_per_day INTEGER,
                                                              proteins_norm_grams_per_day INTEGER,
                                                              fats_norm_grams_per_day INTEGER,
                                                              carbs_norm_grams_per_day INTEGER,
                                                              water_target_ml_per_day INTEGER,

    -- Интервал действия
                                                              valid_from TIMESTAMPTZ NOT NULL,
                                                              valid_to   TIMESTAMPTZ,

                                                              updated_at TIMESTAMPTZ,
                                                              created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

-- Индексы для выборок по истории
CREATE INDEX IF NOT EXISTS idx_profile_entry_user_from
    ON user_nutrition_profile_entries (user_id, valid_from);

CREATE INDEX IF NOT EXISTS idx_profile_entry_user_to
    ON user_nutrition_profile_entries (user_id, valid_to);

-- Гарантия: у пользователя только одна активная цель
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_active_profile_entry
    ON user_nutrition_profile_entries (user_id)
    WHERE valid_to IS NULL;
