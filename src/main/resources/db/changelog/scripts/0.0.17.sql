--liquibase formatted sql

--changeset DanielK:17

CREATE TABLE payment_failed_log (
                                    id           BIGSERIAL PRIMARY KEY,
                                    payment_id   BIGINT,
                                    error_message TEXT,
                                    status       VARCHAR(32) NOT NULL,
                                    created      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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
        '30 дней',
        '30 дней доступа.',
        'RUB',
        70000,
        'SUBSCRIPTION',
        NULL,
        30,
        'AVAILABLE',
        'SUB_30'
    );