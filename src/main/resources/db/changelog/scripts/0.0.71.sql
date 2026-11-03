--liquibase formatted sql

--changeset DanielK:74

-- Упавшие вызовы ИИ (et-111): HTTP-код провайдера (0 — ответа не было: таймаут, сеть, пустой ответ) и текст ошибки.
-- Без токенов и стоимости. У успешных вызовов — null
ALTER TABLE metrics_ai_interaction_record
    ADD COLUMN error_code INTEGER,
    ADD COLUMN error      TEXT;
