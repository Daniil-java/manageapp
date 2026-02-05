--liquibase formatted sql

--changeset DanielK:7

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS assistant_google_oauth (
                                                      telegram_id          BIGINT       PRIMARY KEY,           -- ключ = chatId
                                                      google_sub           VARCHAR(128),
    email                VARCHAR(320),
    refresh_token_enc    TEXT,                               -- шифротекст AES-GCM
    access_token         TEXT,
    access_expires_at    TIMESTAMP WITH TIME ZONE,           -- лучше хранить в UTC
    scope                TEXT,
    default_calendar_id  TEXT,
    last_refresh_at      TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

-- По желанию: ускорить выборки по email/sub (диагностика/отчёты)
CREATE INDEX IF NOT EXISTS idx_assist_oauth_email      ON assistant_google_oauth (email);
CREATE INDEX IF NOT EXISTS idx_assist_oauth_google_sub ON assistant_google_oauth (google_sub);

CREATE TABLE IF NOT EXISTS oauth_link (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- публичный непредсказуемый ID
    chat_id     BIGINT      NOT NULL,                        -- к какому чату относится
    expire_at   TIMESTAMP WITH TIME ZONE NOT NULL,           -- TTL
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

-- Быстрая очистка просроченных
CREATE INDEX IF NOT EXISTS idx_oauth_link_expire ON oauth_link (expire_at);

CREATE TABLE IF NOT EXISTS oauth_state (
    state       UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- этот UUID кладёшь в &state=...
    chat_id     BIGINT      NOT NULL,
    verifier    VARCHAR(256) NOT NULL,                       -- PKCE code_verifier (хранится временно)
    expire_at   TIMESTAMP WITH TIME ZONE NOT NULL,           -- TTL
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_oauth_state_expire ON oauth_state (expire_at);
