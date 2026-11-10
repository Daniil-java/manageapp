--liquibase formatted sql

--changeset DanielK:33

ALTER TABLE user_subscription ADD COLUMN version BIGINT NOT NULL DEFAULT 0;