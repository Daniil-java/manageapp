--liquibase formatted sql
--changeset DanielK:42

ALTER TABLE user_settings
    ADD COLUMN user_theme VARCHAR(32) NOT NULL DEFAULT 'DAY';

-- dishes
ALTER TABLE dishes
    RENAME COLUMN weight_grams TO weight;

-- weight_entries
ALTER TABLE weight_entries
    RENAME COLUMN weight_kg TO weight;

-- water_entries
ALTER TABLE water_entries
    RENAME COLUMN amount_ml TO amount;