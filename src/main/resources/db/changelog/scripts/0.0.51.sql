--liquibase formatted sql
--changeset DanielK:52

-- ════════════════════════════════════════════════════════
--  AI-ИНСАЙТЫ ДЛЯ СТРАНИЦЫ INSIGHTS
--  Хранит последний сгенерированный ИИ отчёт, чтобы страница
--  открывалась сразу, а ИИ дёргался только по кнопке Refresh.
--
--  type:
--    WEEKLY_SUMMARY  — саммари за 7 дней
--    MONTHLY_SUMMARY — саммари за 30 дней
--    PATTERNS_WEEK   — шаблоны поведения за 7 дней
--    PATTERNS_MONTH  — шаблоны поведения за 30 дней
--
--  payload — ответ ИИ в JSON как есть (структура зависит от type).
--  Одна запись на (user_id, type): при обновлении перезаписывается,
--  created_at = время последней генерации.
-- ════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS calorie_ai_insight (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,                  -- app_users.id
    type        VARCHAR(50)  NOT NULL,                  -- AiInsightType enum
    period_from DATE         NOT NULL,                  -- включительно, в таймзоне пользователя
    period_to   DATE         NOT NULL,                  -- включительно
    payload     JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    );

-- Одна запись на пользователя и тип; заодно индекс для выборки инсайтов пользователя
CREATE UNIQUE INDEX IF NOT EXISTS ux_calorie_ai_insight_user_type
    ON calorie_ai_insight (user_id, type);

-- ROLLBACK
-- rollback DROP INDEX IF EXISTS ux_calorie_ai_insight_user_type;
-- rollback DROP TABLE IF EXISTS calorie_ai_insight;
