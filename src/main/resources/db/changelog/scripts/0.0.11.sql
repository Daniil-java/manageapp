--liquibase formatted sql

--changeset DanielK:10


CREATE TABLE ai_message_log (
                                id BIGINT PRIMARY KEY,
                                request TEXT,
                                response TEXT
);