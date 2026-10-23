--liquibase formatted sql
--changeset DanielK:72

-- Новые цены (et-106): 299 / 699 / 1990 звёзд вместо 150 / 350.
-- 1. Все тарифы калорийного бота, что сейчас в продаже, — в архив: месяц, 3 и 9 месяцев, тестовый за 1 звезду
--    и всё, что могли завести руками на проде. Пробный период (SYSTEM_FREE) и бонусы (SYSTEM_BONUS) не трогаем.
--    Купленные подписки на архивные тарифы работают до конца срока: доступ берётся из plan_features по коду тарифа,
--    а не по статусу. Суточный лимит ИИ у них прежний — calorie.ai-rate-limit.per-day-by-plan.
UPDATE pricing_plans
SET plan_status = 'ARCHIVED'
WHERE bot_identifier = 'CALORIE_BOT'
  AND plan_status = 'AVAILABLE';

-- 2. Новые тарифы. Тексты — в i18n/calorie/plans_ru/en (здесь запасные, для ЮKassa и админки)
INSERT INTO pricing_plans (
    title, description, currency, price_minor, payload_type,
    generations_count, duration_days, plan_status, code_for_order_id, bot_identifier, created)
VALUES
    ('Premium Месяц',
     'Доступ ко всем функциям на 30 дней

🚀 Фото, текст и голос — разборы каждый день
🤖 ИИ-разбор дня + PDF-отчёты
📊 Полная статистика и история
ℹ️ До 150 обращений к ИИ в сутки. Условия — на zefir.fit',
     'XTR', 299, 'SUBSCRIPTION', 0, 30, 'AVAILABLE', 'CALORIE_PREMIUM_V2_MONTH', 'CALORIE_BOT', NOW()),
    ('Premium 3 месяца',
     'Доступ ко всем функциям на 90 дней

🚀 Фото, текст и голос — разборы каждый день
🤖 ИИ-разбор дня + PDF-отчёты
📊 Полная статистика и история
ℹ️ До 150 обращений к ИИ в сутки. Условия — на zefir.fit',
     'XTR', 699, 'SUBSCRIPTION', 0, 90, 'AVAILABLE', 'CALORIE_PREMIUM_V2_THREE_MONTH', 'CALORIE_BOT', NOW()),
    ('Premium Год',
     'Доступ ко всем функциям на 365 дней

🚀 Фото, текст и голос — разборы каждый день
🤖 ИИ-разбор дня + PDF-отчёты
📊 Полная статистика и история
ℹ️ До 150 обращений к ИИ в сутки. Условия — на zefir.fit',
     'XTR', 1990, 'SUBSCRIPTION', 0, 365, 'AVAILABLE', 'CALORIE_PREMIUM_V2_YEAR', 'CALORIE_BOT', NOW());

-- 3. Доступ к функциям — копия старого премиума (лендинг берёт лимиты у самого короткого тарифа в продаже)
INSERT INTO plan_features (plan_code, bot_identifier, feature, limit_value, limit_period)
SELECT code.plan_code, pf.bot_identifier, pf.feature, pf.limit_value, pf.limit_period
FROM plan_features pf
         CROSS JOIN (VALUES ('CALORIE_PREMIUM_V2_MONTH'), ('CALORIE_PREMIUM_V2_THREE_MONTH'),
                            ('CALORIE_PREMIUM_V2_YEAR')) AS code (plan_code)
WHERE pf.plan_code = 'CALORIE_PREMIUM_MONTH' AND pf.bot_identifier = 'CALORIE_BOT'
ON CONFLICT (plan_code, bot_identifier, feature) DO NOTHING;
