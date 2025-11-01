--liquibase formatted sql

--changeset DanielK:9

CREATE TABLE user_google_calendar_cache (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   telegram_id BIGINT NOT NULL,
                                                   calendar_id TEXT NOT NULL,
                                                   summary TEXT NOT NULL
);
