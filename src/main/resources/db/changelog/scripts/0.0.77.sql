--liquibase formatted sql

--changeset DanielK:80

-- Клетчатка и микронутриенты блюда (et-127). Оба — на всю порцию, как КБЖУ, и пересчитываются вместе с порцией.
-- fiber — граммы, оценивает ИИ при любом распознавании. micronutrients — витамины, минералы, насыщенные жиры,
-- добавленный сахар, омега-3, ПНЖК: {"ironMg": 1.2, "vitaminDMcg": 0.1, ...} (ключи — enum Micronutrient),
-- оцениваются только для Premium. null — не оценивались: старые блюда, ручной ввод, без Premium.
ALTER TABLE dishes ADD COLUMN fiber INTEGER;
ALTER TABLE dishes ADD COLUMN micronutrients JSONB;

-- Избранное хранит копию блюда — чтобы добавленное из избранного не теряло клетчатку и микронутриенты
ALTER TABLE user_favorite_dishes ADD COLUMN fiber INTEGER;
ALTER TABLE user_favorite_dishes ADD COLUMN micronutrients JSONB;
