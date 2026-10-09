--liquibase formatted sql
--changeset DanielK:58

-- ════════════════════════════════════════════════════════
--  ЯЗЫК ИИ-ИНСАЙТА (страница Insights в миниаппке)
--  language — на каком языке ИИ написал инсайт ("en", "ru").
--  NULL — инсайты до этой миграции, они на английском.
--  Фронт помечает инсайт устаревшим, если язык интерфейса другой.
-- ════════════════════════════════════════════════════════

ALTER TABLE calorie_ai_insight ADD COLUMN IF NOT EXISTS language VARCHAR(8);

-- ROLLBACK
-- rollback ALTER TABLE calorie_ai_insight DROP COLUMN IF EXISTS language;
