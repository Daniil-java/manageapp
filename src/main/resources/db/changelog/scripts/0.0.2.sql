--liquibase formatted sql

--changeset DanielK:1

CREATE TABLE IF NOT EXISTS user_google_calendar (
                                                    telegram_id BIGINT NOT NULL PRIMARY KEY,
                                                    calendar_id TEXT
);