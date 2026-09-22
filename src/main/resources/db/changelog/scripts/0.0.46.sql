--liquibase formatted sql
--changeset DanielK:46

-- Снимаем NOT NULL с бэкап-колонок, так как для новых записей они будут пустыми
ALTER TABLE payments ALTER COLUMN old_telegram_id DROP NOT NULL;
ALTER TABLE user_subscription ALTER COLUMN old_telegram_id DROP NOT NULL;
ALTER TABLE generation_balance ALTER COLUMN old_telegram_id DROP NOT NULL;
ALTER TABLE generation_balance_operations ALTER COLUMN old_telegram_id DROP NOT NULL;

-- rollback ALTER TABLE payments ALTER COLUMN old_telegram_id SET NOT NULL;
-- rollback ALTER TABLE user_subscription ALTER COLUMN old_telegram_id SET NOT NULL;
-- rollback ALTER TABLE generation_balance ALTER COLUMN old_telegram_id SET NOT NULL;
-- rollback ALTER TABLE generation_balance_operations ALTER COLUMN old_telegram_id SET NOT NULL
