--liquibase formatted sql
--changeset DanielK:69

-- Награды за приглашение (ReferralRewardService). Три независимые награды, каждая выдаётся один раз:
--   приглашённому +3 дня Premium — за первое блюдо;
--   пригласившему +7 дней — когда друг вёл дневник 3 разных дня за первые 7 дней (не больше 14 дней за 30 дней);
--   пригласившему +30 дней — за первую оплату подписки другом.
ALTER TABLE utm_attributions
    ADD COLUMN IF NOT EXISTS invited_rewarded_at       TIMESTAMP WITH TIME ZONE, -- приглашённый получил награду за первое блюдо
    ADD COLUMN IF NOT EXISTS referrer_rewarded_at      TIMESTAMP WITH TIME ZONE, -- друг втянулся, пригласивший награждён
    ADD COLUMN IF NOT EXISTS referrer_bonus_days       INTEGER,                  -- сколько дней за это получил (0 — упёрся в лимит)
    ADD COLUMN IF NOT EXISTS referrer_paid_rewarded_at TIMESTAMP WITH TIME ZONE, -- друг оплатил подписку, пригласивший награждён
    ADD COLUMN IF NOT EXISTS referrer_paid_bonus_days  INTEGER,                  -- сколько дней за оплату получил
    ADD COLUMN IF NOT EXISTS referrer_paid_payment_id  BIGINT,                   -- за какой платёж друга: вернули деньги — награда отменяется
    ADD COLUMN IF NOT EXISTS referrer_paid_subscription_id BIGINT;               -- бонусная подписка пригласившего, которую тогда отменить

CREATE INDEX IF NOT EXISTS idx_utm_attributions_referrer_rewarded
    ON utm_attributions (referrer_app_user_id, referrer_rewarded_at) WHERE referrer_rewarded_at IS NOT NULL;

-- Бонусные тарифы. SYSTEM_BONUS: не продаются и не путаются с пробным (его ищут по SYSTEM_FREE).
-- Срок награды за активного друга может быть меньше duration_days — остаток лимита за 30 дней.
INSERT INTO pricing_plans (
    title, description, currency, price_minor, payload_type,
    generations_count, duration_days, plan_status, code_for_order_id, bot_identifier, created)
VALUES
    ('Бонус по приглашению', 'Premium на 3 дня: пришёл по приглашению друга',
     'XTR', 0, 'SUBSCRIPTION', 0, 3, 'SYSTEM_BONUS', 'CALORIE_REFERRAL_INVITED', 'CALORIE_BOT', NOW()),
    ('Бонус за друга', 'Premium на 7 дней: приглашённый друг ведёт дневник',
     'XTR', 0, 'SUBSCRIPTION', 0, 7, 'SYSTEM_BONUS', 'CALORIE_REFERRAL_INVITER', 'CALORIE_BOT', NOW()),
    ('Бонус за подписку друга', 'Premium на 30 дней: приглашённый друг оформил подписку',
     'XTR', 0, 'SUBSCRIPTION', 0, 30, 'SYSTEM_BONUS', 'CALORIE_REFERRAL_FRIEND_PAID', 'CALORIE_BOT', NOW())
ON CONFLICT (bot_identifier, code_for_order_id) DO NOTHING;

-- Лимиты — копия премиума
INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
SELECT code.plan_code, pf.bot_identifier, pf.feature, pf.limit_value, pf.limit_period
FROM plan_features pf
         CROSS JOIN (VALUES ('CALORIE_REFERRAL_INVITED'), ('CALORIE_REFERRAL_INVITER'),
                            ('CALORIE_REFERRAL_FRIEND_PAID')) AS code (plan_code)
WHERE pf.plan_code = 'CALORIE_PREMIUM_MONTH' AND pf.bot_identifier = 'CALORIE_BOT'
ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;
