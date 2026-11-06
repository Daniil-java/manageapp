--liquibase formatted sql
--changeset DanielK:57

-- ════════════════════════════════════════════════════════
--  БЫСТРЫЙ НАБОР ИЗБРАННОГО (меню «+» в миниаппке)
--  quick_add_position — место закреплённого блюда в быстром наборе (1..4),
--  NULL — не закреплено: свободные места занимают последние использованные.
-- ════════════════════════════════════════════════════════

ALTER TABLE user_favorite_dishes ADD COLUMN IF NOT EXISTS quick_add_position INTEGER;

-- ROLLBACK
-- rollback ALTER TABLE user_favorite_dishes DROP COLUMN IF EXISTS quick_add_position;
