--liquibase formatted sql

--changeset DanielK:24

CREATE TABLE user_favorite_dishes (
                                      id              BIGSERIAL PRIMARY KEY,
                                      user_id         BIGINT      NOT NULL,
                                      name            TEXT NOT NULL,

                                      calories        INTEGER NOT NULL ,
                                      proteins        INTEGER NOT NULL ,
                                      fats            INTEGER NOT NULL ,
                                      carbohydrates   INTEGER NOT NULL ,

                                      created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
                                      updated_at      TIMESTAMP WITHOUT TIME ZONE,
                                      last_used_at    TIMESTAMP WITHOUT TIME ZONE
);

-- Для выборки "все избранные блюда пользователя, отсортированные по последнему использованию"
CREATE INDEX idx_user_favorite_dishes_user_last_used
    ON user_favorite_dishes (user_id, last_used_at DESC);

-- Чтобы у одного пользователя не было двух избранных блюд с одинаковым названием
CREATE UNIQUE INDEX uk_user_favorite_dishes_user_name
    ON user_favorite_dishes (user_id, lower(name));