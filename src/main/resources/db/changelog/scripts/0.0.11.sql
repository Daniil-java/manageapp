--liquibase formatted sql

--changeset DanielK:11

ALTER TABLE dish_choice_chat_model
    ADD COLUMN created TIMESTAMP DEFAULT NOW();


