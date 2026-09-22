--liquibase formatted sql

--changeset DanielK:18

-- 1. Баланс генераций
ALTER TABLE generation_balance
    ADD COLUMN bot_identifier TEXT;

-- 2. Операции по балансу
ALTER TABLE generation_balance_operations
    ADD COLUMN bot_identifier TEXT;

-- 3. Платежи
ALTER TABLE payments
    ADD COLUMN bot_identifier TEXT;

-- 4. Тарифные планы
ALTER TABLE pricing_plans
    ADD COLUMN bot_identifier TEXT;

-- 5. Подписки
ALTER TABLE user_subscription
    ADD COLUMN bot_identifier TEXT;
