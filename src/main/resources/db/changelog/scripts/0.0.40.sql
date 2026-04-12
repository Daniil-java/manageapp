--liquibase formatted sql
--changeset DanielK:40

CREATE TABLE smoking_record (
                               id BIGSERIAL PRIMARY KEY,
                               smoked_at TIMESTAMP WITH TIME ZONE NOT NULL,
                               user_id BIGINT NOT NULL
);

-- Индексы под реальные запросы
CREATE INDEX idx_smoking_record_user_id
    ON smoking_event (user_id);

CREATE INDEX idx_smoking_record_smoked_at
    ON smoking_event (smoked_at);

-- Часто самый полезный (юзер + время)
CREATE INDEX idx_smoking_record_user_time
    ON smoking_event (user_id, smoked_at DESC);