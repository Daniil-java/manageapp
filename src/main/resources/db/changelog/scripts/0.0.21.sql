--liquibase formatted sql

--changeset DanielK:21

CREATE TABLE metrics_ai_log (
                                id BIGSERIAL PRIMARY KEY,

                                total_ai_request_count  BIGINT NOT NULL DEFAULT 0,
                                open_ai_request_count   BIGINT NOT NULL DEFAULT 0,
                                gemini_ai_request_count BIGINT NOT NULL DEFAULT 0,
                                claude_ai_request_count BIGINT NOT NULL DEFAULT 0,
                                deep_seek_ai_request_count BIGINT NOT NULL DEFAULT 0,
                                yandex_ai_request_count BIGINT NOT NULL DEFAULT 0,
                                date DATE NOT NULL,
                                CONSTRAINT uq_metrics_ai_log_date UNIQUE (date)
);