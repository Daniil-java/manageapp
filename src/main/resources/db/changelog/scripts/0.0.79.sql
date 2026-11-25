--liquibase formatted sql

--changeset DanielK:82

-- Авиабот: ожидаемое (или фактическое, если уже вылетел) время вылета
ALTER TABLE flights ADD COLUMN IF NOT EXISTS expected_departure VARCHAR(50);

--changeset DanielK:83

-- Авиабот: личные токены для REST API (виджет). Храним только SHA-256 от токена
CREATE TABLE IF NOT EXISTS avia_api_tokens (
    id          BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT      NOT NULL UNIQUE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
