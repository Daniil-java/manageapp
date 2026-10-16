--liquibase formatted sql

--changeset DanielK:32

-- 1. Таблица payment
ALTER TABLE payments
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN paid_at TYPE TIMESTAMP WITH TIME ZONE USING paid_at AT TIME ZONE 'UTC',
    ALTER COLUMN canceled_at TYPE TIMESTAMP WITH TIME ZONE USING canceled_at AT TIME ZONE 'UTC';

-- 2. Таблица user_subscriptions
ALTER TABLE user_subscription
ALTER COLUMN start_at TYPE TIMESTAMP WITH TIME ZONE USING start_at AT TIME ZONE 'UTC',
    ALTER COLUMN end_at TYPE TIMESTAMP WITH TIME ZONE USING end_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC';

-- 3. Таблица pricing_plans
ALTER TABLE pricing_plans
ALTER COLUMN created TYPE TIMESTAMP WITH TIME ZONE USING created AT TIME ZONE 'UTC';

-- 4. Таблица generation_balance_operations
ALTER TABLE generation_balance_operations
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC';

-- 5. Таблица webhook_events
ALTER TABLE webhook_events
ALTER COLUMN received_at TYPE TIMESTAMP WITH TIME ZONE USING received_at AT TIME ZONE 'UTC',
    ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE USING processed_at AT TIME ZONE 'UTC';