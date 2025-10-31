--liquibase formatted sql

--changeset DanielK:9

CREATE TABLE user_google_calendar_cache (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   telegram_id BIGINT NOT NULL,
                                                   calendar_id TEXT NOT NULL,
                                                   summary TEXT NOT NULL,
                                                   CONSTRAINT uq_user_google_calendar_cache_telegram_calendar UNIQUE (telegram_id, calendar_id)
);

CREATE UNIQUE INDEX ux_user_calendar_telegram_calendar ON public.user_google_calendar_cache(telegram_id, calendar_id);