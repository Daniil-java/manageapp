--liquibase formatted sql

--changeset DanielK:25

CREATE TABLE user_nutrition_profiles (
                                         id                          BIGSERIAL PRIMARY KEY,
                                         user_id                     BIGINT NOT NULL,

                                         age_years                   INTEGER,
                                         height_cm                   INTEGER,
                                         current_weight_kg           NUMERIC(5, 2),

                                         sex                         VARCHAR(32),
                                         activity_level              VARCHAR(32),
                                         goal                        VARCHAR(32),
                                         diet_type                   VARCHAR(32),
                                         user_profile_filling_state  VARCHAR(32),

                                         calories_norm_per_day       INTEGER,
                                         proteins_norm_grams_per_day INTEGER,
                                         fats_norm_grams_per_day     INTEGER,
                                         carbs_norm_grams_per_day    INTEGER,

                                         water_target_ml_per_day     INTEGER,

                                         created_at                  TIMESTAMP NOT NULL DEFAULT now(),
                                         updated_at                  TIMESTAMP NOT NULL DEFAULT now()
);

-- Один профиль на user_id (если нужно именно так)
CREATE UNIQUE INDEX ux_user_nutrition_profiles_user_id
    ON user_nutrition_profiles (user_id);

