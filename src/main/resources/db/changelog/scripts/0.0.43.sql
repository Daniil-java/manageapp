--liquibase formatted sql
--changeset DanielK:43

ALTER TABLE user_settings
    ALTER COLUMN user_theme DROP NOT NULL;