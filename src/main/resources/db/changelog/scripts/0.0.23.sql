--liquibase formatted sql

--changeset DanielK:23

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS assistant_google_oauth (
                                                      telegram_id          BIGINT       PRIMARY KEY,
                                                      google_sub           VARCHAR(128),
    email                VARCHAR(320),
    refresh_token_enc    TEXT,
    access_token         TEXT,
    access_expires_at    TIMESTAMP WITH TIME ZONE,
    scope                TEXT,
    default_calendar_id  TEXT,
    last_refresh_at      TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_assist_oauth_email
    ON assistant_google_oauth (email);

CREATE INDEX IF NOT EXISTS idx_assist_oauth_google_sub
    ON assistant_google_oauth (google_sub);

CREATE TABLE IF NOT EXISTS oauth_link (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_id BIGINT      NOT NULL,
    expire_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_oauth_link_expire
    ON oauth_link (expire_at);

CREATE TABLE IF NOT EXISTS oauth_state (
    state       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_id BIGINT      NOT NULL,
    verifier    VARCHAR(256) NOT NULL,
    expire_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_oauth_state_expire
    ON oauth_state (expire_at);

CREATE TABLE notified_event (
                                id BIGSERIAL PRIMARY KEY,
                                calendar_id TEXT NOT NULL,
                                event_id TEXT NOT NULL,
                                user_id BIGINT NOT NULL,
                                notified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                reminder_minutes INT,
                                CONSTRAINT uq_calendar_event UNIQUE (calendar_id, event_id, user_id)
);

CREATE INDEX idx_notified_at
    ON notified_event (notified_at);

CREATE TABLE user_google_calendar_cache (
                                            id BIGSERIAL PRIMARY KEY,
                                            telegram_id BIGINT NOT NULL,
                                            calendar_id TEXT NOT NULL,
                                            summary TEXT NOT NULL
);

CREATE TABLE ai_message_log (
                                id BIGSERIAL PRIMARY KEY,
                                request TEXT,
                                response TEXT
);

--changeset DanielK:2

CREATE TABLE user_messages_log (
                                   id           BIGSERIAL PRIMARY KEY,
                                   telegram_id  BIGINT,
                                   username     TEXT,
                                   firstname    TEXT,
                                   lastname     TEXT,
                                   google_email TEXT,
                                   message      TEXT,
                                   created_at   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_user_messages_log_created_at
    ON user_messages_log (created_at);

CREATE INDEX idx_user_messages_log_telegram_id
    ON user_messages_log (telegram_id);

--changeset DanielK:3

CREATE TABLE user_notification_settings (
                                            id                  BIGSERIAL PRIMARY KEY,
                                            telegram_id         BIGINT    NOT NULL,
                                            daily_enabled       BOOLEAN   NOT NULL DEFAULT TRUE,
                                            daily_time          TIME,
                                            utc_offset_hours    INTEGER,
                                            last_daily_notified DATE
);

CREATE UNIQUE INDEX ux_user_notification_settings_telegram
    ON user_notification_settings (telegram_id);

CREATE INDEX idx_user_notification_settings_daily_enabled
    ON user_notification_settings (daily_enabled);

--changeset DanielK:4

CREATE TABLE user_auth_notification (
                                        id           BIGSERIAL PRIMARY KEY,
                                        telegram_id  BIGINT NOT NULL,
                                        status       TEXT NOT NULL,
                                        text         TEXT,
                                        execute_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_user_auth_notification_status_execute_at
    ON user_auth_notification (status, execute_at);