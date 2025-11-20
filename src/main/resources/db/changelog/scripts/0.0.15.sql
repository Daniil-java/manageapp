--liquibase formatted sql

--changeset DanielK:15

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
        'Пакет 10 генераций [Звезды]',
        'Разовый пакет на 10 запросов к ИИ. Подходит для теста сервиса и разовых задач. Оплата звездами.',
        'XTR',
        1,
        'GENERATION_REQUEST',
        10,
        NULL,
        'AVAILABLE',
        'GEN_10_XTR'
    );