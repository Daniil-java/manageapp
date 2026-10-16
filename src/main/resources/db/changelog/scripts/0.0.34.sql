--liquibase formatted sql

--changeset DanielK:34

ALTER TABLE pricing_plans ALTER COLUMN plan_status TYPE VARCHAR(100);

INSERT INTO pricing_plans (
    title,
    description,
    currency,
    price_minor,
    payload_type,
    generations_count,
    duration_days,
    plan_status,
    code_for_order_id,
    bot_identifier,
    created
) VALUES (
             'Пробный период',            -- title
             'Бесплатный доступ на 3 дня', -- description
             'XTR',                       -- currency (из твоего Enum Currency)
             9999999,                           -- price_minor (0, так как бесплатно)
             'SUBSCRIPTION',              -- payload_type (PricingPlanType)
             0,                          -- generations_count (если нужно ограничить кол-во)
             3,                           -- duration_days
             'SYSTEM_FREE',               -- plan_status (PlanStatus)
             'FREE_TRIAL',                -- code_for_order_id
             'CALORIE_BOT',             -- bot_identifier (Enum BotIdentifier)
             NOW()                        -- created
         );