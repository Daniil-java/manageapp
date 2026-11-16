--liquibase formatted sql
--changeset DanielK:70

-- Бонус по партнёрской ссылке: новый пользователь, пришедший по ссылке (не реферальной), получает столько дней
-- Premium сверх пробного периода. 0 — без бонуса. У ссылок из формы по умолчанию 7 (UtmService.saveLink).
ALTER TABLE utm_links
    ADD COLUMN IF NOT EXISTS bonus_days INTEGER NOT NULL DEFAULT 0;

-- Когда пользователю последний раз предлагали пригласить друга (InvitePromptService) — не чаще раза в неделю
ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS invite_prompt_last_at TIMESTAMP WITH TIME ZONE;

-- Тариф бонуса по партнёрской ссылке. Срок выдачи — bonus_days ссылки, duration_days — только по умолчанию
INSERT INTO pricing_plans (
    title, description, currency, price_minor, payload_type,
    generations_count, duration_days, plan_status, code_for_order_id, bot_identifier, created)
VALUES ('Бонус партнёра', 'Premium по ссылке партнёра',
        'XTR', 0, 'SUBSCRIPTION', 0, 7, 'SYSTEM_BONUS', 'CALORIE_PARTNER_BONUS', 'CALORIE_BOT', NOW())
ON CONFLICT (bot_identifier, code_for_order_id) DO NOTHING;

INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
SELECT 'CALORIE_PARTNER_BONUS', pf.bot_identifier, pf.feature, pf.limit_value, pf.limit_period
FROM plan_features pf
WHERE pf.plan_code = 'CALORIE_PREMIUM_MONTH' AND pf.bot_identifier = 'CALORIE_BOT'
ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;
