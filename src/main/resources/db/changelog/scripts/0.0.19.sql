--liquibase formatted sql

--changeset DanielK:19

-- 1. Снимаем старую уникальность по одному telegram_id
DROP INDEX IF EXISTS ux_generation_balance_telegram_id;
DROP INDEX IF EXISTS idx_generation_balance_telegram_id;

-- 2. Делаем уникальность по паре (telegram_id, bot_identifier)
CREATE UNIQUE INDEX ux_generation_balance_telegram_id_bot_identifier
    ON generation_balance (telegram_id, bot_identifier);

-- (опционально) индекс только по bot_identifier, если понадобится
CREATE INDEX idx_generation_balance_bot_identifier
    ON generation_balance (bot_identifier);

CREATE INDEX idx_generation_balance_operations_tg_bot
    ON generation_balance_operations (telegram_id, bot_identifier);

DROP INDEX IF EXISTS ux_pricing_plans_code_for_order_id_notnull;

CREATE UNIQUE INDEX ux_pricing_plans_bot_code_for_order_id_notnull
    ON pricing_plans (bot_identifier, code_for_order_id)
    WHERE code_for_order_id IS NOT NULL;

CREATE INDEX idx_pricing_plans_bot_identifier
    ON pricing_plans (bot_identifier);

