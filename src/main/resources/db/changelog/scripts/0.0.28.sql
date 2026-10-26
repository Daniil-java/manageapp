--liquibase formatted sql

--changeset DanielK:28

ALTER TABLE dishes
    ADD COLUMN IF NOT EXISTS emoji_icon TEXT,
    ADD COLUMN IF NOT EXISTS fats INT,
    ADD COLUMN IF NOT EXISTS carbohydrates INT,
    ADD COLUMN IF NOT EXISTS weight_grams INT,
    ADD COLUMN IF NOT EXISTS portions INT,
    ADD COLUMN IF NOT EXISTS portion_weight INT,
    ADD COLUMN IF NOT EXISTS category TEXT,
    ADD COLUMN IF NOT EXISTS ai_confidence INT;

-- ===============================
-- user_favorite_dishes → обновление под новую сущность
-- ===============================

ALTER TABLE user_favorite_dishes
    -- --- Основное ---
    ADD COLUMN emoji_icon TEXT,

    -- --- Количество ---
    ADD COLUMN weight_grams INTEGER,
    ADD COLUMN portions INTEGER,
    ADD COLUMN portion_weight INTEGER,

    -- --- Классификация ---
    ADD COLUMN category TEXT,

    -- --- ИИ ---
    ADD COLUMN ai_confidence INTEGER;


ALTER TABLE user_favorite_dishes
    ALTER COLUMN calories DROP NOT NULL,
ALTER COLUMN proteins DROP NOT NULL,
    ALTER COLUMN fats DROP NOT NULL,
    ALTER COLUMN carbohydrates DROP NOT NULL;
