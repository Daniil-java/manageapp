--liquibase formatted sql

--changeset DanielK:8

CREATE TABLE notified_event (
                                id BIGSERIAL PRIMARY KEY,
                                calendar_id TEXT NOT NULL,
                                event_id TEXT NOT NULL,
                                user_id BIGINT NOT NULL,
                                notified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                CONSTRAINT uq_calendar_event UNIQUE (calendar_id, event_id, user_id)
);
CREATE INDEX idx_notified_at ON notified_event(notified_at);