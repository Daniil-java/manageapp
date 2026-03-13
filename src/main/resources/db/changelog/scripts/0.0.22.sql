
--liquibase formatted sql

--changeset DanielK:22

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
    bot_identifier
) VALUES (
             '30 дней подписки',
             '30 дней доступа',
             'XTR',
             1,
             'SUBSCRIPTION',
             NULL,
             30,
             'AVAILABLE',
             'CALORIE_SUB_30_XTR',
             'CALORIE_BOT'
         );