--liquibase formatted sql

--changeset DanielK:9

CREATE TABLE dish_choice_chat_model (
                                        id BIGSERIAL PRIMARY KEY,
                                        dish_id BIGINT NOT NULL,
                                        chat_model TEXT NOT NULL,
                                        name TEXT,
                                        calories INTEGER,
                                        proteins INTEGER,
                                        fats INTEGER,
                                        carbohydrates INTEGER
);

CREATE INDEX idx_dish_choice_chat_model_chat_model
    ON dish_choice_chat_model (chat_model);

CREATE INDEX idx_dish_choice_chat_model_dish_id
    ON dish_choice_chat_model (dish_id);

CREATE INDEX idx_dish_choice_chat_model_dish_chat
    ON dish_choice_chat_model (dish_id, chat_model);