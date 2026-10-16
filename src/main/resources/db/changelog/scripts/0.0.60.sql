--liquibase formatted sql
--changeset DanielK:63

-- ════════════════════════════════════════════════════════
--  ИНДЕКС БЛЮД ПО ПОЛЬЗОВАТЕЛЮ И ДАТЕ
--  Все выборки блюд идут по (user_id, created): сегодня/период, count за день,
--  max(created) в шедулерах напоминаний. Индекса не было — каждый раз seq scan по dishes.
-- ════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS ix_dishes__user_created ON dishes (user_id, created);

-- rollback DROP INDEX IF EXISTS ix_dishes__user_created;
