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

--changeset DanielK:56
-- ════════════════════════════════════════════════════════
--  РЕЖИМ НОРМЫ ВОДЫ: АВТО / ВРУЧНУЮ (независимо от калорий)
--  AUTO   — вода = вес × мл/кг для активности (LOW 30, MEDIUM 33, HIGH 35),
--           пересчитывается вместе с профилем.
--  MANUAL — норму воды задал пользователь, пересчёт её не трогает.
-- ════════════════════════════════════════════════════════

ALTER TABLE user_nutrition_profiles ADD COLUMN IF NOT EXISTS water_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO';

-- Кто уже правил воду руками: она не совпадает с формулой (или с 2000 мл по умолчанию, если формулу не посчитать)
UPDATE user_nutrition_profiles
SET water_mode = 'MANUAL'
WHERE water_target_ml_per_day IS NOT NULL
  AND (
        (current_weight_kg IS NOT NULL AND activity_level IS NOT NULL
            AND water_target_ml_per_day <> round(current_weight_kg *
                CASE activity_level WHEN 'LOW' THEN 30 WHEN 'MEDIUM' THEN 33 WHEN 'HIGH' THEN 35 END))
     OR ((current_weight_kg IS NULL OR activity_level IS NULL) AND water_target_ml_per_day <> 2000)
      );

-- rollback ALTER TABLE user_nutrition_profiles DROP COLUMN IF EXISTS water_mode;
