--liquibase formatted sql

--changeset DanielK:31

-- 1. Таблица правил (лимитов для тарифов)
CREATE TABLE IF NOT EXISTS plan_features (
                                             id SERIAL PRIMARY KEY,
                                             plan_code TEXT NOT NULL,         -- 'FREE', 'premium_monthly'
                                             feature TEXT NOT NULL,           -- Имя из Enum BotFeature
                                             bot_identifier TEXT NOT NULL,
                                             limit_value INTEGER DEFAULT 0,
                                             limit_period TEXT NOT NULL,      -- DAILY, MONTHLY, LIFETIME, UNLIMITED
                                             CONSTRAINT uk_plan_feature UNIQUE (plan_code, bot_identifier, feature)
    );

-- 2. Таблица учета использования (счетчики юзеров)
CREATE TABLE IF NOT EXISTS user_feature_usage (
                                                  id SERIAL PRIMARY KEY,
                                                  user_id BIGINT NOT NULL,
                                                  bot_identifier TEXT NOT NULL,
                                                  feature TEXT NOT NULL,
                                                  used_count INTEGER DEFAULT 0,
                                                  last_reset_local_date DATE,
                                                  last_reset_utc TIMESTAMP,
                                                  CONSTRAINT uk_usage_row UNIQUE (user_id, bot_identifier, feature)
    );

-- 3. Безопасный индекс для подписок
-- Гарантирует, что у пользователя не может быть ДВУХ одновременно активных подписок.
-- Это обычный индекс, он работает везде и не требует расширений.
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_subscription_available_one_only
    ON user_subscription (telegram_id, bot_identifier)
    WHERE (status = 'AVAILABLE');

CREATE UNIQUE INDEX IF NOT EXISTS uk_pricing_plan_bot_code
    ON pricing_plans (bot_identifier, code_for_order_id);

-- 4. Добавление данных в каталог планов (если таблицы еще нет - убедись, что она создана ранее)
INSERT INTO pricing_plans (
    title, description, currency, price_minor, payload_type,
    generations_count, duration_days, plan_status, code_for_order_id, bot_identifier
)
VALUES
    ('Premium Месяц', 'Доступ ко всем функциям на 30 дней', 'XTR', 150, 'SUBSCRIPTION', 0, 30, 'AVAILABLE', 'CALORIE_PREMIUM_MONTH', 'CALORIE_BOT')
    ON CONFLICT (bot_identifier, code_for_order_id)
DO UPDATE SET
    price_minor = EXCLUDED.price_minor,
           plan_status = EXCLUDED.plan_status;

-- 5. Заполнение лимитов (Plan Features)

-- Лимиты для БЕСПЛАТНОГО тарифа
INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
VALUES
    ('FREE', 'CALORIE_BOT', 'DISH_AI_VISION', 2, 'DAILY'),        -- 2 фото в день
    ('FREE', 'CALORIE_BOT', 'DISH_FAVORITE_LIST', 5, 'LIFETIME'),  -- 5 блюд в избранном
    ('FREE', 'CALORIE_BOT', 'REPORT_PDF_WEEK', 0, 'LIFETIME'),     -- Запрещено
    ('FREE', 'CALORIE_BOT', 'REPORT_PDF_MONTH', 0, 'LIFETIME'),     -- Запрещено
    ('FREE', 'CALORIE_BOT', 'REPORT_DAY', 2, 'DAILY')
    ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;

-- Лимиты для ПРЕМИУМ тарифа
INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
VALUES
    ('CALORIE_PREMIUM_MONTH', 'CALORIE_BOT', 'DISH_AI_VISION', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_MONTH', 'CALORIE_BOT', 'DISH_FAVORITE_LIST', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_MONTH', 'CALORIE_BOT', 'REPORT_PDF_WEEK', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_MONTH', 'CALORIE_BOT', 'REPORT_PDF_MONTH', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_MONTH', 'CALORIE_BOT', 'REPORT_DAY', -1, 'UNLIMITED')
    ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;

-- 6. Добавление ПРЕМИУМ 3 МЕСЯЦА

INSERT INTO pricing_plans (
    title, description, currency, price_minor, payload_type,
    generations_count, duration_days, plan_status, code_for_order_id, bot_identifier
)
VALUES
    ('Premium 3 месяца', 'Доступ ко всем функциям на 90 дней', 'XTR', 350,
     'SUBSCRIPTION', 0, 90, 'AVAILABLE', 'CALORIE_PREMIUM_THREE_MONTH', 'CALORIE_BOT')
    ON CONFLICT (bot_identifier, code_for_order_id)
DO UPDATE SET
    price_minor = EXCLUDED.price_minor,
           plan_status = EXCLUDED.plan_status;


-- 7. Лимиты для ПРЕМИУМ 3 МЕСЯЦА (полная копия monthly)

INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
VALUES
    ('CALORIE_PREMIUM_THREE_MONTH', 'CALORIE_BOT', 'DISH_AI_VISION', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_THREE_MONTH', 'CALORIE_BOT', 'DISH_FAVORITE_LIST', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_THREE_MONTH', 'CALORIE_BOT', 'REPORT_PDF_WEEK', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_THREE_MONTH', 'CALORIE_BOT', 'REPORT_PDF_MONTH', -1, 'UNLIMITED'),
    ('CALORIE_PREMIUM_THREE_MONTH', 'CALORIE_BOT', 'REPORT_DAY', -1, 'UNLIMITED')
    ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;

INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
VALUES
    ('CALORIE_SUB_30_XTR', 'CALORIE_BOT', 'DISH_AI_VISION', -1, 'UNLIMITED'),
    ('CALORIE_SUB_30_XTR', 'CALORIE_BOT', 'DISH_FAVORITE_LIST', -1, 'UNLIMITED'),
    ('CALORIE_SUB_30_XTR', 'CALORIE_BOT', 'REPORT_PDF_WEEK', -1, 'UNLIMITED'),
    ('CALORIE_SUB_30_XTR', 'CALORIE_BOT', 'REPORT_PDF_MONTH', -1, 'UNLIMITED'),
    ('CALORIE_SUB_30_XTR', 'CALORIE_BOT', 'REPORT_DAY', -1, 'UNLIMITED')
    ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;
