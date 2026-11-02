--liquibase formatted sql
--changeset DanielK:71

-- Учёт расходов на ИИ по пользователям: кто вызвал, зачем, какой моделью и сколько это стоило.
-- Старые записи остаются без пользователя и стоимости.
ALTER TABLE metrics_ai_interaction_record
    ADD COLUMN IF NOT EXISTS model               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS app_user_id         BIGINT,
    ADD COLUMN IF NOT EXISTS operation           VARCHAR(50),
    ADD COLUMN IF NOT EXISTS cached_input_tokens BIGINT,
    ADD COLUMN IF NOT EXISTS cost_usd            NUMERIC(12, 6);

CREATE INDEX IF NOT EXISTS idx_metrics_ai_record_user_created
    ON metrics_ai_interaction_record (app_user_id, created);
