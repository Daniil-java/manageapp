--liquibase formatted sql
--changeset DanielK:44

-- 1. Создаем главную таблицу приложения, только если её вообще нет
CREATE TABLE IF NOT EXISTS app_users (
                                         id BIGSERIAL PRIMARY KEY,
                                         email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    username VARCHAR(255),
    firstname VARCHAR(255),
    lastname VARCHAR(255),
    updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    temp_tg_id BIGINT -- Временная колонка для маппинга данных
    );

-- 2. Безопасно добавляем ссылку в telegram_users, если её ещё нет
ALTER TABLE telegram_users ADD COLUMN IF NOT EXISTS app_user_id BIGINT REFERENCES app_users(id);

-- 3. ПРЕДВАРИТЕЛЬНАЯ ОЧИСТКА МУСОРА
-- Удаляем сиротские записи, у которых user_id не совпадает с реальными telegram_id
DELETE FROM user_nutrition_profiles WHERE user_id NOT IN (SELECT telegram_id FROM telegram_users) AND user_id > 10000;
DELETE FROM user_settings WHERE user_id NOT IN (SELECT telegram_id FROM telegram_users) AND user_id > 10000;

-- Удаляем старые локальные тесты, чтобы освободить ID 1, 2, 3... для новых app_users
DELETE FROM user_nutrition_profiles WHERE user_id < 10000;
DELETE FROM user_settings WHERE user_id < 10000;
DELETE FROM dishes WHERE user_id < 10000;

-- 4. Переливаем уникальных пользователей из телеги в app_users
INSERT INTO app_users (temp_tg_id, username, firstname, lastname, created_at)
SELECT DISTINCT ON (telegram_id) telegram_id, username, firstname, lastname, created
FROM telegram_users
WHERE telegram_id NOT IN (SELECT COALESCE(temp_tg_id, 0) FROM app_users)
ORDER BY telegram_id, created DESC
ON CONFLICT DO NOTHING;

-- 5. Связываем telegram_users с созданным app_users
UPDATE telegram_users tu
SET app_user_id = au.id
    FROM app_users au
WHERE tu.telegram_id = au.temp_tg_id AND tu.app_user_id IS NULL;

-- 5.5 СОЗДАНИЕ БЭКАП-КОЛОНОК И СОХРАНЕНИЕ СТАРЫХ ID
ALTER TABLE dishes ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE user_nutrition_profiles ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE user_nutrition_profile_entries ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE water_entries ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE weight_entries ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE user_favorite_dishes ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE user_feature_usage ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE utm_clicks ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS telegram_user_id BIGINT;

-- Заполняем новые бэкап-колонки старыми значениями (пока user_id еще равен telegram_id)
UPDATE dishes SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE user_nutrition_profiles SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE user_nutrition_profile_entries SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE water_entries SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE weight_entries SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE user_favorite_dishes SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE user_feature_usage SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE utm_clicks SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;
UPDATE user_settings SET telegram_user_id = user_id WHERE user_id > 10000 AND telegram_user_id IS NULL;

-- 6. ВРЕМЕННО ОТКЛЮЧАЕМ ИНДЕКСЫ УНИКАЛЬНОСТИ
DROP INDEX IF EXISTS ux_user_nutrition_profiles_user_id;
ALTER TABLE user_settings DROP CONSTRAINT IF EXISTS user_settings_pkey;

-- 7. МИГРАЦИЯ ДАННЫХ (теперь ориентируемся на точный сохраненный telegram_user_id)
UPDATE dishes d SET user_id = au.id FROM app_users au WHERE d.telegram_user_id = au.temp_tg_id;
UPDATE user_nutrition_profiles unp SET user_id = au.id FROM app_users au WHERE unp.telegram_user_id = au.temp_tg_id;
UPDATE user_nutrition_profile_entries unpe SET user_id = au.id FROM app_users au WHERE unpe.telegram_user_id = au.temp_tg_id;
UPDATE water_entries we SET user_id = au.id FROM app_users au WHERE we.telegram_user_id = au.temp_tg_id;
UPDATE weight_entries wge SET user_id = au.id FROM app_users au WHERE wge.telegram_user_id = au.temp_tg_id;
UPDATE user_favorite_dishes ufd SET user_id = au.id FROM app_users au WHERE ufd.telegram_user_id = au.temp_tg_id;
UPDATE user_feature_usage ufu SET user_id = au.id FROM app_users au WHERE ufu.telegram_user_id = au.temp_tg_id;
UPDATE utm_clicks uc SET user_id = au.id FROM app_users au WHERE uc.telegram_user_id = au.temp_tg_id;
UPDATE user_settings us SET user_id = au.id FROM app_users au WHERE us.telegram_user_id = au.temp_tg_id;

-- 7.5 УДАЛЕНИЕ ДУБЛИКАТОВ (На всякий случай, если кто-то задублировался)
DELETE FROM user_nutrition_profiles WHERE ctid NOT IN (SELECT MAX(ctid) FROM user_nutrition_profiles GROUP BY user_id);
DELETE FROM user_settings WHERE ctid NOT IN (SELECT MAX(ctid) FROM user_settings GROUP BY user_id);

-- 8. ВОЗВРАЩАЕМ ИНДЕКСЫ И КЛЮЧИ НА МЕСТО
ALTER TABLE user_nutrition_profiles ADD CONSTRAINT ux_user_nutrition_profiles_user_id UNIQUE (user_id);
ALTER TABLE user_settings ADD PRIMARY KEY (user_id);

-- БЛОК ОТКАТА (ROLLBACK)
-- rollback ALTER TABLE user_nutrition_profiles DROP CONSTRAINT IF EXISTS ux_user_nutrition_profiles_user_id;
-- rollback ALTER TABLE user_settings DROP CONSTRAINT IF EXISTS user_settings_pkey;
-- rollback UPDATE dishes SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE user_nutrition_profiles SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE user_nutrition_profile_entries SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE water_entries SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE weight_entries SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE user_favorite_dishes SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE user_feature_usage SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE utm_clicks SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback UPDATE user_settings SET user_id = telegram_user_id WHERE telegram_user_id IS NOT NULL;
-- rollback CREATE UNIQUE INDEX IF NOT EXISTS ux_user_nutrition_profiles_user_id ON user_nutrition_profiles (user_id);
-- rollback ALTER TABLE user_settings ADD PRIMARY KEY (user_id);
-- rollback ALTER TABLE dishes DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE user_nutrition_profiles DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE user_nutrition_profile_entries DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE water_entries DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE weight_entries DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE user_favorite_dishes DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE user_feature_usage DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE utm_clicks DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE user_settings DROP COLUMN IF EXISTS telegram_user_id;
-- rollback ALTER TABLE telegram_users DROP COLUMN IF EXISTS app_user_id;
-- rollback DROP TABLE IF EXISTS app_users;