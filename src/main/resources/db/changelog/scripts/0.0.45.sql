--liquibase formatted sql
--changeset DanielK:45 splitStatements:false

-- 1. Добавляем колонку app_user_id во все платежные таблицы
ALTER TABLE payments ADD COLUMN IF NOT EXISTS app_user_id BIGINT;
ALTER TABLE user_subscription ADD COLUMN IF NOT EXISTS app_user_id BIGINT;
ALTER TABLE generation_balance ADD COLUMN IF NOT EXISTS app_user_id BIGINT;
ALTER TABLE generation_balance_operations ADD COLUMN IF NOT EXISTS app_user_id BIGINT;

-- 2. Переливаем данные на основе связей в telegram_users (строго по id и боту)
UPDATE payments p
SET app_user_id = tu.app_user_id
    FROM telegram_users tu
WHERE p.telegram_id = tu.telegram_id AND p.bot_identifier = tu.bot_identifier;

UPDATE user_subscription us
SET app_user_id = tu.app_user_id
    FROM telegram_users tu
WHERE us.telegram_id = tu.telegram_id AND us.bot_identifier = tu.bot_identifier;

UPDATE generation_balance gb
SET app_user_id = tu.app_user_id
    FROM telegram_users tu
WHERE gb.telegram_id = tu.telegram_id AND gb.bot_identifier = tu.bot_identifier;

UPDATE generation_balance_operations gbo
SET app_user_id = tu.app_user_id
    FROM telegram_users tu
WHERE gbo.telegram_id = tu.telegram_id AND gbo.bot_identifier = tu.bot_identifier;

-- 3. СОХРАНЕНИЕ ВМЕСТО БЕЗЖАЛОСТНОЙ ОЧИСТКИ
-- Временно закомментировано, чтобы не потерять сирот.
-- DELETE FROM payments WHERE app_user_id IS NULL;
-- DELETE FROM user_subscription WHERE app_user_id IS NULL;
-- DELETE FROM generation_balance WHERE app_user_id IS NULL;
-- DELETE FROM generation_balance_operations WHERE app_user_id IS NULL;

-- 4. Удаляем старые индексы, завязанные на telegram_id
DROP INDEX IF EXISTS ux_generation_balance_telegram_id_bot_identifier;
DROP INDEX IF EXISTS idx_generation_balance_operations_tg_bot;
DROP INDEX IF EXISTS ux_user_subscription_available_one_only;
DROP INDEX IF EXISTS idx_payments_telegram_id;

-- 5. Создаем новые индексы с app_user_id
CREATE UNIQUE INDEX ux_generation_balance_appuser_bot ON generation_balance (app_user_id, bot_identifier);
CREATE INDEX idx_gen_bal_ops_appuser_bot ON generation_balance_operations (app_user_id, bot_identifier);
CREATE INDEX idx_payments_app_user_id ON payments (app_user_id);
-- Восстанавливаем уникальный индекс для подписок
CREATE UNIQUE INDEX ux_user_subscription_available_one_only ON user_subscription (app_user_id, bot_identifier) WHERE (status = 'ACTIVE');

-- 6. Делаем колонки NOT NULL (Пока закомментировано, так как без шага 3 тут могут остаться NULL)
-- ALTER TABLE payments ALTER COLUMN app_user_id SET NOT NULL;
-- ALTER TABLE user_subscription ALTER COLUMN app_user_id SET NOT NULL;
-- ALTER TABLE generation_balance ALTER COLUMN app_user_id SET NOT NULL;
-- ALTER TABLE generation_balance_operations ALTER COLUMN app_user_id SET NOT NULL;

-- 7. ПЕРЕИМЕНОВЫВАЕМ СТАРУЮ КОЛОНКУ ВМЕСТО УДАЛЕНИЯ (БЭКАП ДАННЫХ)
ALTER TABLE payments RENAME COLUMN telegram_id TO old_telegram_id;
ALTER TABLE user_subscription RENAME COLUMN telegram_id TO old_telegram_id;
ALTER TABLE generation_balance RENAME COLUMN telegram_id TO old_telegram_id;
ALTER TABLE generation_balance_operations RENAME COLUMN telegram_id TO old_telegram_id;


-- БЛОК ОТКАТА (ROLLBACK)
-- rollback ALTER TABLE payments RENAME COLUMN old_telegram_id TO telegram_id;
-- rollback ALTER TABLE user_subscription RENAME COLUMN old_telegram_id TO telegram_id;
-- rollback ALTER TABLE generation_balance RENAME COLUMN old_telegram_id TO telegram_id;
-- rollback ALTER TABLE generation_balance_operations RENAME COLUMN old_telegram_id TO telegram_id;
-- rollback DROP INDEX IF EXISTS ux_generation_balance_appuser_bot;
-- rollback DROP INDEX IF EXISTS idx_gen_bal_ops_appuser_bot;
-- rollback DROP INDEX IF EXISTS idx_payments_app_user_id;
-- rollback DROP INDEX IF EXISTS ux_user_subscription_available_one_only;
-- rollback CREATE UNIQUE INDEX IF NOT EXISTS ux_generation_balance_telegram_id_bot_identifier ON generation_balance (telegram_id, bot_identifier);
-- rollback CREATE INDEX IF NOT EXISTS idx_generation_balance_operations_tg_bot ON generation_balance_operations (telegram_id, bot_identifier);
-- rollback CREATE INDEX IF NOT EXISTS idx_payments_telegram_id ON payments (telegram_id);
-- rollback CREATE UNIQUE INDEX IF NOT EXISTS ux_user_subscription_available_one_only ON user_subscription (telegram_id, bot_identifier) WHERE (status = 'AVAILABLE');
-- rollback ALTER TABLE payments DROP COLUMN IF EXISTS app_user_id;
-- rollback ALTER TABLE user_subscription DROP COLUMN IF EXISTS app_user_id;
-- rollback ALTER TABLE generation_balance DROP COLUMN IF EXISTS app_user_id;
-- rollback ALTER TABLE generation_balance_operations DROP COLUMN IF EXISTS app_user_id;