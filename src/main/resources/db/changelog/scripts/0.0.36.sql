--liquibase formatted sql

--changeset DanielK:36

-- 1. Таблица для UtmLink
CREATE TABLE IF NOT EXISTS utm_links (
                                         id BIGSERIAL PRIMARY KEY,
                                         code VARCHAR(255) NOT NULL UNIQUE,
    source_url TEXT,
    tittle TEXT,
    owner_type VARCHAR(150),      -- Соответствует Enum OwnerType
    creator_id BIGINT,           -- Кто создал запись
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                             );

-- 2. Таблица для UtmClick
CREATE TABLE IF NOT EXISTS utm_clicks (
                                          id BIGSERIAL PRIMARY KEY,
                                          utm_link_id BIGINT,
                                          user_id BIGINT,             -- ID того, кто перешел
                                          is_new_user BOOLEAN NOT NULL DEFAULT FALSE,
                                          clicked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Индексы для производительности
CREATE INDEX IF NOT EXISTS idx_utm_links_code ON utm_links(code);
CREATE INDEX IF NOT EXISTS idx_utm_clicks_user_id ON utm_clicks(user_id);