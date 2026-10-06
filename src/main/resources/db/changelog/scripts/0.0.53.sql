--liquibase formatted sql
--changeset DanielK:54

-- ════════════════════════════════════════════════════════
--  РЕЖИМ НОРМЫ КАЛОРИЙ: АВТО / ВРУЧНУЮ
--  AUTO   — норма по формуле (Миффлин — Сан-Жеор × активность × цель),
--           пересчитывается при любой правке профиля и новом весе.
--  MANUAL — калории задал пользователь, правка профиля их не меняет;
--           БЖУ считаются от ручных калорий, вода — как в AUTO.
-- ════════════════════════════════════════════════════════

ALTER TABLE user_nutrition_profiles ADD COLUMN IF NOT EXISTS norm_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO';

-- ROLLBACK
-- rollback ALTER TABLE user_nutrition_profiles DROP COLUMN IF EXISTS norm_mode;

--changeset DanielK:55
-- Уже существующие пользователи с посчитанной нормой переходят на ручной режим:
-- их норма не должна вдруг начать меняться от веса. Без нормы (профиль не заполнен) — остаются AUTO,
-- иначе норма у них так и не посчитается. Новые профили — AUTO по умолчанию колонки.
-- Отдельный changeset в том же файле: changeset 54 уже применён локально, его checksum менять нельзя.
UPDATE user_nutrition_profiles SET norm_mode = 'MANUAL' WHERE calories_norm_per_day IS NOT NULL;

-- rollback UPDATE user_nutrition_profiles SET norm_mode = 'AUTO';
