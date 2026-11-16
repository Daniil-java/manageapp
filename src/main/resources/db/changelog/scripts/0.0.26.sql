--liquibase formatted sql

--changeset DanielK:26

-- 1. Создание таблицы для учета воды
CREATE TABLE water_entries (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               entry_date DATE NOT NULL,
                               amount_ml INTEGER NOT NULL,
                               created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для воды
-- Поиск всех записей юзера за конкретный период (самый частый запрос для аналитики)
CREATE INDEX idx_water_user_date ON water_entries (user_id, entry_date);

-- 2. Создание таблицы для учета веса
CREATE TABLE weight_entries (
                                id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                entry_date DATE NOT NULL,
                                weight_kg DECIMAL(5, 2) NOT NULL,
                                created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для веса
-- Поиск истории веса юзера по датам
CREATE INDEX idx_weight_user_date ON weight_entries (user_id, entry_date);