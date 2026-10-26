--liquibase formatted sql
--changeset DanielK:61

-- ════════════════════════════════════════════════════════
--  ЛИМИТЫ ИИ — В БД ВМЕСТО ПАМЯТИ ПРОЦЕССА
--  Раньше счётчики жили в памяти: после деплоя обнулялись, а при нескольких
--  экземплярах бэка у каждого были бы свои (лимит × число экземпляров).
-- ════════════════════════════════════════════════════════

-- Обращения пользователей к ИИ — по ним AiRateLimiter считает лимиты за минуту и за сутки.
-- Одна строка — одна попытка (неудачные тоже: вызов ИИ стоит денег).
-- Строки старше 30 дней удаляет AiRateLimiter.deleteOldRequests.
CREATE TABLE IF NOT EXISTS calorie_ai_request (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL,                    -- app_users.id
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_calorie_ai_request_user_created
    ON calorie_ai_request (user_id, created_at);

-- Обновление ИИ-инсайта: идёт ли генерация прямо сейчас и когда последний раз обращались к ИИ.
-- Одна строка на (пользователь, тип) — защита от двойного нажатия и пауза в минуту между обновлениями.
CREATE TABLE IF NOT EXISTS calorie_ai_insight_refresh (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL,                  -- app_users.id
    type         VARCHAR(50) NOT NULL,                  -- AiInsightType enum
    started_at   TIMESTAMPTZ,                           -- генерация идёт с этого момента; NULL — не идёт
    ai_called_at TIMESTAMPTZ                            -- последнее обращение к ИИ (успешное или нет)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_calorie_ai_insight_refresh_user_type
    ON calorie_ai_insight_refresh (user_id, type);

-- ROLLBACK
-- rollback DROP TABLE IF EXISTS calorie_ai_insight_refresh;
-- rollback DROP TABLE IF EXISTS calorie_ai_request;
