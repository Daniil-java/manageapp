--liquibase formatted sql

--changeset DanielK:35

ALTER TABLE telegram_users
    ADD COLUMN is_bot_blocked BOOLEAN NOT NULL DEFAULT false;