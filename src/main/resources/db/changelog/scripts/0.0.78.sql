--liquibase formatted sql

--changeset DanielK:81

-- Premium снова дешевле (et-128): месяц 150 ⭐, 3 месяца 350 ⭐ — как до et-106. Годового тарифа больше нет в продаже.
-- Цена меняется у тех же тарифов (коды V2), а не у старых CALORIE_PREMIUM_MONTH / _THREE_MONTH: у старых
-- суточный лимит ИИ 300 (calorie.ai-rate-limit.per-day-by-plan), у V2 — 150, его оставляем.
-- Купленные годовые подписки работают до конца срока: доступ — по коду тарифа, а не по статусу.
UPDATE pricing_plans
SET price_minor = 150
WHERE bot_identifier = 'CALORIE_BOT'
  AND code_for_order_id = 'CALORIE_PREMIUM_V2_MONTH';

UPDATE pricing_plans
SET price_minor = 350
WHERE bot_identifier = 'CALORIE_BOT'
  AND code_for_order_id = 'CALORIE_PREMIUM_V2_THREE_MONTH';

UPDATE pricing_plans
SET plan_status = 'ARCHIVED'
WHERE bot_identifier = 'CALORIE_BOT'
  AND code_for_order_id = 'CALORIE_PREMIUM_V2_YEAR';
