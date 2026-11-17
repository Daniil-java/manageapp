--liquibase formatted sql
--changeset DanielK:53

-- ════════════════════════════════════════════════════════
--  «БЕСПЛАТНАЯ ПРОМАШКА» ДЛЯ ФИЧ С ЛИМИТОМ
--  Если ИИ не нашёл еду на фото, первый такой раз за день попытку
--  не списываем, а предупреждаем пользователя. Следующие — списываем.
--
--  last_grace_local_date — день (в таймзоне пользователя), когда
--  промашку уже простили. NULL — ещё ни разу.
-- ════════════════════════════════════════════════════════

ALTER TABLE user_feature_usage ADD COLUMN IF NOT EXISTS last_grace_local_date DATE;

-- ROLLBACK
-- rollback ALTER TABLE user_feature_usage DROP COLUMN IF EXISTS last_grace_local_date;
