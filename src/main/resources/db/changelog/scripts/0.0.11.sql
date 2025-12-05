--liquibase formatted sql

--changeset DanielK:10


CREATE TABLE ai_message_log (
                                id BIGSERIAL PRIMARY KEY,
                                request TEXT,
                                response TEXT
);