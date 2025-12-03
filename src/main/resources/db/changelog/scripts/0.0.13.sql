--liquibase formatted sql

--changeset DanielK:13

CREATE TABLE pricing_plans (
                               id               BIGSERIAL PRIMARY KEY,
                               title            TEXT NOT NULL,
                               description      TEXT,
                               currency         VARCHAR(10) NOT NULL,
                               price_minor      INTEGER NOT NULL CHECK (price_minor >= 0),
                               payload_type     VARCHAR(30) NOT NULL,   -- PricingPlanType (GENERATION_REQUEST / SUBSCRIPTION)
                               generations_count BIGINT,                -- используется для GENERATION_REQUEST
                               duration_days    INTEGER,                -- используется для SUBSCRIPTION
                               plan_status      VARCHAR(20) NOT NULL,   -- PlanStatus (AVAILABLE / DISABLED)
                               code_for_order_id VARCHAR(64) NOT NULL,  -- твой codeForOrderId
                               created          TIMESTAMP NOT NULL DEFAULT now()
);

-- привязка платежа к тарифу
ALTER TABLE payments
    ADD COLUMN pricing_plan_id BIGINT;

ALTER TABLE payments
    DROP COLUMN payload;

-- уникальный код тарифа, если указан
CREATE UNIQUE INDEX ux_pricing_plans_code_for_order_id_notnull
    ON pricing_plans (code_for_order_id)
    WHERE code_for_order_id IS NOT NULL;

-- по статусу, чтобы быстро выбирать только AVAILABLE
CREATE INDEX idx_pricing_plans_plan_status
    ON pricing_plans (plan_status);

-- по типу тарифа (если будешь часто делить пакеты/подписки)
CREATE INDEX idx_pricing_plans_payload_type
    ON pricing_plans (payload_type);

-- индекс для джойнов платежей с тарифами
CREATE INDEX idx_payments_pricing_plan_id
    ON payments (pricing_plan_id);

COMMENT ON COLUMN payments.amount IS 'Сумма в минимальной единице валюты (копейки, центы)';


INSERT INTO pricing_plans (
    title,
    description,
    currency,
    price_minor,
    payload_type,
    generations_count,
    duration_days,
    plan_status,
    code_for_order_id
)
VALUES
    (
        'Пакет 10 генераций',
        'Разовый пакет на 10 запросов к ИИ. Подходит для теста сервиса и разовых задач.',
        'RUB',
        7000,
        'GENERATION_REQUEST',
        10,
        NULL,
        'AVAILABLE',
        'GEN_10'
    ),
    (
        'Пакет 100 генераций',
        'Пакет на 100 запросов к ИИ для активного использования и долгих сессий.',
        'RUB',
        70000,
        'GENERATION_REQUEST',
        100,
        NULL,
        'AVAILABLE',
        'GEN_100'
    );