--liquibase formatted sql

--changeset DanielK:10

ALTER TABLE dish_choice_chat_model
    ADD COLUMN is_choosed BOOLEAN DEFAULT FALSE;

